package com.contractapi.service;

import com.contractapi.constants.ContractStatus;
import com.contractapi.constants.ErrorCode;
import com.contractapi.constants.SignerStatus;
import com.contractapi.dto.ContractDetail;
import com.contractapi.dto.InviteSignersRequest;
import com.contractapi.dto.SignerInviteRequest;
import com.contractapi.entity.Contract;
import com.contractapi.entity.ContractSigner;
import com.contractapi.exception.ApiException;
import com.contractapi.mapper.ContractMapper;
import com.contractapi.mapper.ContractSignerMapper;
import com.contractapi.utils.TimeProvider;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 合同签署闭环：邀请签署方、顺序签署/拒绝、到期与终态流转。
 * 所有写操作均在单事务内完成：签署记录与合同状态一次落盘，任一步失败保持原样。
 */
@Service
public class SigningService {
  private final ContractMapper contractMapper;
  private final ContractSignerMapper signerMapper;
  private final TimeProvider timeProvider;

  public SigningService(ContractMapper contractMapper,
                        ContractSignerMapper signerMapper,
                        TimeProvider timeProvider) {
    this.contractMapper = contractMapper;
    this.signerMapper = signerMapper;
    this.timeProvider = timeProvider;
  }

  /** 草稿可一次性邀请多名签署方，登记身份、顺序与各自截止时间，随后进入待签署。 */
  @Transactional
  public ContractDetail inviteSigners(Long contractId, InviteSignersRequest request) {
    if (request == null || request.userId() == null) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "缺少用户信息");
    }
    if (request.signers() == null || request.signers().isEmpty()) {
      throw new ApiException(ErrorCode.SIGNERS_REQUIRED, "至少需要一名签署方");
    }

    Contract contract = contractMapper.selectByIdForUpdate(contractId);
    if (contract == null) {
      throw new ApiException(ErrorCode.NOT_FOUND, "合同不存在", HttpStatus.NOT_FOUND);
    }
    if (!contract.getUserId().equals(request.userId())) {
      throw new ApiException(ErrorCode.NOT_CONTRACT_OWNER, "只有合同创建人可以邀请签署方", HttpStatus.FORBIDDEN);
    }
    String status = contract.getStatus();
    if (ContractStatus.SIGNED.name().equals(status) || ContractStatus.EXPIRED.name().equals(status)) {
      throw new ApiException(ErrorCode.CONTRACT_TERMINAL, "合同已" + label(status) + "，不得再调整", HttpStatus.CONFLICT);
    }
    if (ContractStatus.PENDING_SIGN.name().equals(status)) {
      throw new ApiException(ErrorCode.SIGNING_NOT_OPEN, "签署方已登记，不能重复邀请", HttpStatus.CONFLICT);
    }

    LocalDateTime now = timeProvider.now();
    Set<String> identities = new HashSet<>();
    Set<Integer> orders = new HashSet<>();
    int orderIndex = 1;
    for (SignerInviteRequest item : request.signers()) {
      validateInvite(item, now, identities, orders, orderIndex);
      orderIndex++;
    }

    for (SignerInviteRequest item : request.signers()) {
      ContractSigner signer = new ContractSigner();
      signer.setContractId(contract.getId());
      signer.setSignerId(item.signerId());
      signer.setSignerName(item.signerName());
      signer.setSignOrder(item.signOrder());
      signer.setDeadline(item.deadline());
      signer.setStatus(SignerStatus.PENDING.name());
      signerMapper.insert(signer);
    }

    int updated = contractMapper.casStatus(contract.getId(),
        ContractStatus.DRAFT.name(), ContractStatus.PENDING_SIGN.name());
    if (updated == 0) {
      // 并发下状态已被其他事务推进，插入随事务回滚，保持原样。
      throw new ApiException(ErrorCode.SIGNING_NOT_OPEN, "合同当前状态不允许登记签署方", HttpStatus.CONFLICT);
    }

    return loadDetail(contractId);
  }

  private void validateInvite(SignerInviteRequest item, LocalDateTime now,
                              Set<String> identities, Set<Integer> orders, int index) {
    if (item == null
        || isBlank(item.signerId())
        || isBlank(item.signerName())) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "第 " + index + " 名签署方缺少身份信息");
    }
    if (!identities.add(item.signerId())) {
      throw new ApiException(ErrorCode.DUPLICATE_SIGNER, "签署方身份重复: " + item.signerId(), HttpStatus.CONFLICT);
    }
    Integer order = item.signOrder();
    if (order == null || order <= 0) {
      throw new ApiException(ErrorCode.INVALID_SIGN_ORDER, "签署顺序必须为正整数");
    }
    if (!orders.add(order)) {
      throw new ApiException(ErrorCode.INVALID_SIGN_ORDER, "签署顺序不能重复: " + order);
    }
    if (item.deadline() == null) {
      throw new ApiException(ErrorCode.INVALID_DEADLINE, "签署方 " + item.signerId() + " 缺少截止时间");
    }
    if (!item.deadline().isAfter(now)) {
      throw new ApiException(ErrorCode.INVALID_DEADLINE, "签署方 " + item.signerId() + " 的截止时间必须晚于当前时间");
    }
  }

  /** 签署确认：身份、顺序、到期校验通过后条件落盘，重复或并发确认只有一次成功。 */
  @Transactional
  public ContractDetail sign(Long contractId, String signerId) {
    if (isBlank(signerId)) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "缺少签署方标识");
    }

    Contract contract = lockContract(contractId);
    checkOpen(contract);

    LocalDateTime now = timeProvider.now();
    if (signerMapper.countOverduePending(contractId, now) > 0) {
      // 到期未签的流转由惰性检查/定时扫描独立提交落盘，本事务只负责拒绝本次确认。
      throw new ApiException(ErrorCode.SIGNING_DEADLINE_PASSED, "存在已到期未签的签署方，合同已过期", HttpStatus.CONFLICT);
    }

    ContractSigner signer = signerMapper.findForUpdate(contractId, signerId);
    if (signer == null) {
      // 越权确认：身份不在本合同签署名单中。
      throw new ApiException(ErrorCode.SIGNER_NOT_FOUND, "签署方不在本合同名单中，无权确认", HttpStatus.FORBIDDEN);
    }
    if (!SignerStatus.PENDING.name().equals(signer.getStatus())) {
      throw new ApiException(ErrorCode.SIGNER_ALREADY_ACTIONED,
          "签署方已" + (SignerStatus.SIGNED.name().equals(signer.getStatus()) ? "签署" : "拒绝"), HttpStatus.CONFLICT);
    }
    if (signerMapper.countEarlierNotSigned(contractId, signer.getSignOrder()) > 0) {
      throw new ApiException(ErrorCode.SIGNING_ORDER_BLOCKED, "前序签署方尚未完成签署", HttpStatus.CONFLICT);
    }

    LocalDateTime signedAt = now;
    int changed = signerMapper.markSigned(signer.getId(), signedAt);
    if (changed == 0) {
      // 并发/重复确认：另一笔已经先落盘，本次不成功，状态与记录保持原样。
      throw new ApiException(ErrorCode.SIGNER_ALREADY_ACTIONED, "该签署方已完成确认，请勿重复操作", HttpStatus.CONFLICT);
    }

    if (signerMapper.countPending(contractId) == 0) {
      int marked = contractMapper.markSigned(contractId, signedAt);
      if (marked == 0) {
        throw new ApiException(ErrorCode.CONTRACT_TERMINAL, "合同状态已变更，签署结果未整体落盘", HttpStatus.CONFLICT);
      }
    }
    return loadDetail(contractId);
  }

  /** 任一名签署方拒绝：其拒绝记录与合同已过期状态在同一事务一次落盘。 */
  @Transactional
  public ContractDetail reject(Long contractId, String signerId) {
    if (isBlank(signerId)) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "缺少签署方标识");
    }

    Contract contract = lockContract(contractId);
    checkOpen(contract);

    ContractSigner signer = signerMapper.findForUpdate(contractId, signerId);
    if (signer == null) {
      throw new ApiException(ErrorCode.SIGNER_NOT_FOUND, "签署方不在本合同名单中，无权确认", HttpStatus.FORBIDDEN);
    }
    if (!SignerStatus.PENDING.name().equals(signer.getStatus())) {
      throw new ApiException(ErrorCode.SIGNER_ALREADY_ACTIONED, "签署方已完成确认，不能重复操作", HttpStatus.CONFLICT);
    }

    LocalDateTime rejectedAt = timeProvider.now();
    int changed = signerMapper.markRejected(signer.getId(), rejectedAt);
    if (changed == 0) {
      throw new ApiException(ErrorCode.SIGNER_ALREADY_ACTIONED, "该签署方的确认已被处理", HttpStatus.CONFLICT);
    }
    int marked = contractMapper.casStatus(contractId,
        ContractStatus.PENDING_SIGN.name(), ContractStatus.EXPIRED.name());
    if (marked == 0) {
      throw new ApiException(ErrorCode.CONTRACT_TERMINAL, "合同状态已变更，拒绝结果未整体落盘", HttpStatus.CONFLICT);
    }
    return loadDetail(contractId);
  }

  /** 读取单份合同时做一次惰性到期检查：单条条件更新，立即提交生效。 */
  public void expireIfDue(Long contractId) {
    contractMapper.expireOneIfDue(contractId, timeProvider.now());
  }

  /** 全量到期扫描：任一人到期未签，合同转为已过期。 */
  @Transactional
  public int sweepExpired() {
    return contractMapper.expireOverdue(timeProvider.now());
  }

  private Contract lockContract(Long contractId) {
    if (contractId == null) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "缺少合同 ID");
    }
    Contract contract = contractMapper.selectByIdForUpdate(contractId);
    if (contract == null) {
      throw new ApiException(ErrorCode.NOT_FOUND, "合同不存在", HttpStatus.NOT_FOUND);
    }
    return contract;
  }

  private void checkOpen(Contract contract) {
    String status = contract.getStatus();
    if (ContractStatus.SIGNED.name().equals(status) || ContractStatus.EXPIRED.name().equals(status)) {
      throw new ApiException(ErrorCode.CONTRACT_TERMINAL, "合同已" + label(status) + "，不得再调整", HttpStatus.CONFLICT);
    }
    if (ContractStatus.DRAFT.name().equals(status)) {
      throw new ApiException(ErrorCode.SIGNING_NOT_OPEN, "合同尚未登记签署方", HttpStatus.CONFLICT);
    }
  }

  private ContractDetail loadDetail(Long contractId) {
    Contract refreshed = contractMapper.selectById(contractId);
    List<ContractSigner> signers = signerMapper.findByContractId(contractId);
    return new ContractDetail(
        refreshed.getId(),
        refreshed.getUserId(),
        refreshed.getTemplateId(),
        refreshed.getTitle(),
        refreshed.getContent(),
        refreshed.getFillParams(),
        refreshed.getStatus(),
        refreshed.getSignedAt(),
        signers);
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private String label(String status) {
    return ContractStatus.SIGNED.name().equals(status) ? "签署" : "过期";
  }
}
