package com.contractapi.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.contractapi.constants.ContractStatus;
import com.contractapi.constants.ErrorCode;
import com.contractapi.dto.ContractDetail;
import com.contractapi.dto.GenerateContractRequest;
import com.contractapi.entity.Contract;
import com.contractapi.entity.ContractSigner;
import com.contractapi.entity.ContractTemplate;
import com.contractapi.exception.ApiException;
import com.contractapi.mapper.ContractMapper;
import com.contractapi.mapper.ContractSignerMapper;
import com.contractapi.utils.JsonUtils;
import com.contractapi.utils.TemplateRenderer;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContractService {
  private final TemplateService templateService;
  private final TemplateRenderer renderer;
  private final JsonUtils jsonUtils;
  private final ContractMapper contractMapper;
  private final ContractSignerMapper signerMapper;
  private final SigningService signingService;

  public ContractService(TemplateService templateService,
                         TemplateRenderer renderer,
                         JsonUtils jsonUtils,
                         ContractMapper contractMapper,
                         ContractSignerMapper signerMapper,
                         SigningService signingService) {
    this.templateService = templateService;
    this.renderer = renderer;
    this.jsonUtils = jsonUtils;
    this.contractMapper = contractMapper;
    this.signerMapper = signerMapper;
    this.signingService = signingService;
  }

  /** 从可用模板生成合同：渲染后的正文与填充参数一并落盘，失败则整体回滚。 */
  @Transactional
  public Contract generate(GenerateContractRequest request) {
    if (request == null || request.userId() == null) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "缺少用户信息");
    }
    Map<String, String> variables = request.variables() == null ? Map.of() : request.variables();
    ContractTemplate template = templateService.findAvailable(request.templateId());
    validateVariables(template, variables);

    String body = renderer.render(template.getContent(), variables);
    Contract contract = new Contract();
    contract.setUserId(request.userId());
    contract.setTemplateId(template.getId());
    contract.setTitle(request.title() == null || request.title().isBlank() ? template.getTitle() : request.title());
    contract.setContent(body);
    contract.setFillParams(jsonUtils.toJson(variables));
    contract.setStatus(ContractStatus.DRAFT.name());
    contract.setSigners("[]");
    contractMapper.insert(contract);
    return contract;
  }

  private void validateVariables(ContractTemplate template, Map<String, String> variables) {
    if (template.getVariables() == null || template.getVariables().isBlank()) {
      return;
    }
    for (String key : jsonUtils.toStringList(template.getVariables())) {
      String value = variables.get(key);
      if (value == null || value.isBlank()) {
        throw new ApiException(ErrorCode.MISSING_TEMPLATE_VARIABLE, "模板变量 " + key + " 未填充");
      }
    }
  }

  public ContractDetail getDetail(Long id) {
    // 读取前先驱动到期流转（独立事务立即提交），保证回读到的状态与签署记录一致。
    signingService.expireIfDue(id);
    Contract contract = requireContract(id);
    List<ContractSigner> signers = signerMapper.findByContractId(id);
    return toDetail(contract, signers);
  }

  /** 用户合同库：按状态筛选，回读结果与库中落盘状态保持一致。 */
  public List<Contract> list(Long userId, String status) {
    if (userId == null) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "缺少用户信息");
    }
    signingService.sweepExpired();
    QueryWrapper<Contract> wrapper = new QueryWrapper<Contract>().eq("user_id", userId);
    if (status != null && !status.isBlank()) {
      validateStatus(status);
      wrapper.eq("status", status);
    }
    wrapper.orderByDesc("id");
    return contractMapper.selectList(wrapper);
  }

  private void validateStatus(String status) {
    try {
      ContractStatus.valueOf(status);
    } catch (IllegalArgumentException ex) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "不支持的合同状态: " + status);
    }
  }

  private Contract requireContract(Long id) {
    if (id == null) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "缺少合同 ID");
    }
    Contract contract = contractMapper.selectById(id);
    if (contract == null) {
      throw new ApiException(ErrorCode.NOT_FOUND, "合同不存在", org.springframework.http.HttpStatus.NOT_FOUND);
    }
    return contract;
  }

  private ContractDetail toDetail(Contract contract, List<ContractSigner> signers) {
    return new ContractDetail(
        contract.getId(),
        contract.getUserId(),
        contract.getTemplateId(),
        contract.getTitle(),
        contract.getContent(),
        contract.getFillParams(),
        contract.getStatus(),
        contract.getSignedAt(),
        signers);
  }

  public String exportPdf(Long id) {
    requireContract(id);
    return "wkhtmltopdf 已在 Docker 镜像安装，合同 " + id + " 可导出到 /tmp/contracts/" + id + ".pdf";
  }
}
