package com.contractapi.service;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.contractapi.constants.ContractStatus;
import com.contractapi.constants.ErrorCode;
import com.contractapi.constants.SignerStatus;
import com.contractapi.dto.SignConfirmRequest;
import com.contractapi.entity.Contract;
import com.contractapi.entity.ContractSigner;
import com.contractapi.exception.ApiException;
import com.contractapi.mapper.ContractMapper;
import com.contractapi.mapper.ContractSignerMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 合同签署流转：签署记录与合同状态在同一事务落盘，任一步失败整体回滚保持原样。
 * 签署方确认与合同状态流转均使用条件更新，重复、越权或并发确认只允许一次成功。
 */
@Service
public class ContractFlowService {
  private static final Logger log = LoggerFactory.getLogger(ContractFlowService.class);

  private final ContractMapper contractMapper;
  private final ContractSignerMapper signerMapper;

  public ContractFlowService(ContractMapper contractMapper, ContractSignerMapper signerMapper) {
    this.contractMapper = contractMapper;
    this.signerMapper = signerMapper;
  }

  /** 签署方按顺序确认签署：记录签署时间，全部完成后合同自动转为已签署 */
  @Transactional
  public void doSign(Long contractId, SignConfirmRequest request) {
    requirePendingSign(contractId, "签署");
    ContractSigner signer = requireSigner(contractId, request);
    long notReady = signerMapper.selectCount(new QueryWrapper<ContractSigner>()
        .eq("contract_id", contractId)
        .lt("sign_order", signer.getSignOrder())
        .ne("status", SignerStatus.SIGNED.name()));
    if (notReady > 0) {
      throw new ApiException(ErrorCode.SIGN_ORDER_NOT_READY, "请先完成前序签署方的签署");
    }
    LocalDateTime now = LocalDateTime.now();
    int signed = signerMapper.markSigned(signer.getId(), now);
    if (signed != 1) {
      throw new ApiException(ErrorCode.SIGNER_ALREADY_CONFIRMED, "该签署方已确认，请勿重复签署");
    }
    Long remaining = signerMapper.selectCount(new QueryWrapper<ContractSigner>()
        .eq("contract_id", contractId)
        .eq("status", SignerStatus.PENDING.name()));
    if (remaining == 0) {
      int closed = contractMapper.closePendingSign(contractId, ContractStatus.SIGNED.name(), now, now);
      if (closed != 1) {
        throw new ApiException(ErrorCode.CONTRACT_STATUS_INVALID, "合同状态已变更，签署失败");
      }
      log.info("合同全部签署完成，转为已签署 id={}", contractId);
    }
  }

  /** 任一签署方拒绝：合同转为已过期 */
  @Transactional
  public void doReject(Long contractId, SignConfirmRequest request) {
    requirePendingSign(contractId, "拒绝");
    ContractSigner signer = requireSigner(contractId, request);
    int rejected = signerMapper.markRejected(signer.getId());
    if (rejected != 1) {
      throw new ApiException(ErrorCode.SIGNER_ALREADY_CONFIRMED, "该签署方已确认，请勿重复操作");
    }
    LocalDateTime now = LocalDateTime.now();
    int closed = contractMapper.closePendingSign(contractId, ContractStatus.EXPIRED.name(), null, now);
    if (closed != 1) {
      throw new ApiException(ErrorCode.CONTRACT_STATUS_INVALID, "合同状态已变更，操作失败");
    }
    log.info("签署方 {} 拒绝，合同转为已过期 id={}", signer.getSignerName(), contractId);
  }

  /** 已签署或已过期不得再调整，草稿尚未进入签署流程 */
  private Contract requirePendingSign(Long contractId, String action) {
    Contract contract = contractMapper.selectById(contractId);
    if (contract == null) {
      throw new ApiException(ErrorCode.CONTRACT_NOT_FOUND, "合同不存在：" + contractId);
    }
    String status = contract.getStatus();
    if (ContractStatus.PENDING_SIGN.name().equals(status)) {
      return contract;
    }
    if (ContractStatus.EXPIRED.name().equals(status)) {
      throw new ApiException(ErrorCode.CONTRACT_EXPIRED, "合同已过期，无法" + action);
    }
    if (ContractStatus.SIGNED.name().equals(status)) {
      throw new ApiException(ErrorCode.CONTRACT_STATUS_INVALID, "合同已签署，无法" + action);
    }
    throw new ApiException(ErrorCode.CONTRACT_STATUS_INVALID, "合同尚未邀请签署方，无法" + action);
  }

  /** 校验签署方为受邀方且身份匹配（越权拦截） */
  private ContractSigner requireSigner(Long contractId, SignConfirmRequest request) {
    if (request == null || request.signerName() == null || request.signerName().isBlank()) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "signerName 不能为空");
    }
    ContractSigner signer = signerMapper.selectOne(new QueryWrapper<ContractSigner>()
        .eq("contract_id", contractId)
        .eq("signer_name", request.signerName().trim()));
    if (signer == null) {
      throw new ApiException(ErrorCode.SIGNER_NOT_FOUND, "该签署方未受邀：" + request.signerName());
    }
    if (signer.getSignerUserId() != null && !signer.getSignerUserId().equals(request.signerUserId())) {
      throw new ApiException(ErrorCode.FORBIDDEN, "签署身份与邀请登记不一致");
    }
    return signer;
  }
}
