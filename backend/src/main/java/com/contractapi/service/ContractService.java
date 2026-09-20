package com.contractapi.service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.contractapi.constants.ContractStatus;
import com.contractapi.constants.ErrorCode;
import com.contractapi.constants.SignerStatus;
import com.contractapi.dto.GenerateContractRequest;
import com.contractapi.dto.InviteSignersRequest;
import com.contractapi.dto.SignConfirmRequest;
import com.contractapi.entity.Contract;
import com.contractapi.entity.ContractSigner;
import com.contractapi.entity.ContractTemplate;
import com.contractapi.exception.ApiException;
import com.contractapi.mapper.ContractMapper;
import com.contractapi.mapper.ContractSignerMapper;
import com.contractapi.utils.TemplateRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContractService {
  private static final Logger log = LoggerFactory.getLogger(ContractService.class);

  private final ContractMapper contractMapper;
  private final ContractSignerMapper signerMapper;
  private final TemplateService templateService;
  private final ContractExpiryService expiryService;
  private final ContractFlowService flowService;
  private final TemplateRenderer renderer;
  private final ObjectMapper objectMapper;

  public ContractService(ContractMapper contractMapper, ContractSignerMapper signerMapper,
      TemplateService templateService, ContractExpiryService expiryService,
      ContractFlowService flowService, TemplateRenderer renderer, ObjectMapper objectMapper) {
    this.contractMapper = contractMapper;
    this.signerMapper = signerMapper;
    this.templateService = templateService;
    this.expiryService = expiryService;
    this.flowService = flowService;
    this.renderer = renderer;
    this.objectMapper = objectMapper;
  }

  /** 从可用模板生成合同，留存渲染后的正文与填充参数，初始状态为草稿 */
  @Transactional
  public Contract generate(GenerateContractRequest request) {
    if (request.userId() == null) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "userId 不能为空");
    }
    ContractTemplate template = templateService.find(request.templateId());
    Map<String, String> variables = request.variables() == null ? Map.of() : request.variables();
    LocalDateTime now = LocalDateTime.now();
    Contract contract = new Contract();
    contract.setUserId(request.userId());
    contract.setTemplateId(template.getId());
    contract.setTitle(request.title() == null || request.title().isBlank() ? template.getTitle() : request.title());
    contract.setContent(renderer.render(template.getContent(), variables));
    contract.setVariables(toJson(variables));
    contract.setFormat(request.format() == null || request.format().isBlank() ? "TEXT" : request.format());
    contract.setStatus(ContractStatus.DRAFT.name());
    contract.setCreatedAt(now);
    contract.setUpdatedAt(now);
    contractMapper.insert(contract);
    log.info("合同生成成功 id={} userId={} templateId={}", contract.getId(), request.userId(), template.getId());
    return contract;
  }

  /** 草稿邀请多名签署方：登记身份、顺序和截止时间，合同转为待签署 */
  @Transactional
  public Contract invite(Long contractId, InviteSignersRequest request) {
    validateInvite(request);
    Contract contract = requireContract(contractId);
    if (!contract.getUserId().equals(request.userId())) {
      throw new ApiException(ErrorCode.FORBIDDEN, "仅合同创建人可邀请签署方");
    }
    if (!ContractStatus.DRAFT.name().equals(contract.getStatus())) {
      throw new ApiException(ErrorCode.CONTRACT_STATUS_INVALID, "仅草稿状态可邀请签署方，当前状态：" + contract.getStatus());
    }
    LocalDateTime now = LocalDateTime.now();
    for (InviteSignersRequest.SignerItem item : request.signers()) {
      ContractSigner signer = new ContractSigner();
      signer.setContractId(contract.getId());
      signer.setSignerName(item.signerName().trim());
      signer.setSignerUserId(item.signerUserId());
      signer.setSignOrder(item.signOrder());
      signer.setStatus(SignerStatus.PENDING.name());
      signerMapper.insert(signer);
    }
    int updated = contractMapper.markPendingSign(contract.getId(), request.deadline(), now);
    if (updated != 1) {
      throw new ApiException(ErrorCode.CONTRACT_STATUS_INVALID, "合同状态已变更，邀请失败");
    }
    log.info("合同邀请签署方成功 id={} signers={} deadline={}", contract.getId(), request.signers().size(), request.deadline());
    return detail(contract.getId());
  }

  /** 签署方确认签署：先惰性处理到期，再事务化签署 */
  public Contract sign(Long contractId, SignConfirmRequest request) {
    expiryService.expireIfOverdue(contractId);
    flowService.doSign(contractId, request);
    return detail(contractId);
  }

  /** 任一签署方拒绝：合同转为已过期 */
  public Contract reject(Long contractId, SignConfirmRequest request) {
    expiryService.expireIfOverdue(contractId);
    flowService.doReject(contractId, request);
    return detail(contractId);
  }

  /** 合同库：按用户与状态筛选，直接回读数据库保证一致 */
  public List<Contract> list(Long userId, String status) {
    QueryWrapper<Contract> query = new QueryWrapper<>();
    if (userId != null) {
      query.eq("user_id", userId);
    }
    if (status != null && !status.isBlank()) {
      try {
        ContractStatus.valueOf(status);
      } catch (IllegalArgumentException ex) {
        throw new ApiException(ErrorCode.VALIDATION_FAILED, "非法的合同状态：" + status);
      }
      query.eq("status", status);
    }
    query.orderByAsc("id");
    return contractMapper.selectList(query);
  }

  /** 合同详情：包含签署方列表 */
  public Contract detail(Long id) {
    Contract contract = requireContract(id);
    contract.setSigners(signerMapper.selectList(new QueryWrapper<ContractSigner>()
        .eq("contract_id", id)
        .orderByAsc("sign_order")));
    return contract;
  }

  public String exportPdf(Long id) {
    requireContract(id);
    return "wkhtmltopdf 已在 Docker 镜像安装，合同 " + id + " 可导出到 /tmp/contracts/" + id + ".pdf";
  }

  private Contract requireContract(Long id) {
    Contract contract = id == null ? null : contractMapper.selectById(id);
    if (contract == null) {
      throw new ApiException(ErrorCode.CONTRACT_NOT_FOUND, "合同不存在：" + id);
    }
    return contract;
  }

  private void validateInvite(InviteSignersRequest request) {
    if (request == null || request.userId() == null) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "userId 不能为空");
    }
    if (request.deadline() == null || !request.deadline().isAfter(LocalDateTime.now())) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "签署截止时间必须晚于当前时间");
    }
    List<InviteSignersRequest.SignerItem> signers = request.signers();
    if (signers == null || signers.isEmpty()) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "至少邀请一名签署方");
    }
    Set<String> names = new HashSet<>();
    Set<Integer> orders = new HashSet<>();
    for (InviteSignersRequest.SignerItem item : signers) {
      if (item.signerName() == null || item.signerName().isBlank()) {
        throw new ApiException(ErrorCode.VALIDATION_FAILED, "签署方身份不能为空");
      }
      if (item.signOrder() == null || item.signOrder() < 1) {
        throw new ApiException(ErrorCode.VALIDATION_FAILED, "签署顺序必须为大于 0 的整数");
      }
      if (!names.add(item.signerName().trim())) {
        throw new ApiException(ErrorCode.VALIDATION_FAILED, "签署方身份重复：" + item.signerName());
      }
      if (!orders.add(item.signOrder())) {
        throw new ApiException(ErrorCode.VALIDATION_FAILED, "签署顺序重复：" + item.signOrder());
      }
    }
  }

  private String toJson(Map<String, String> variables) {
    try {
      return objectMapper.writeValueAsString(variables);
    } catch (Exception ex) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "填充参数序列化失败");
    }
  }
}
