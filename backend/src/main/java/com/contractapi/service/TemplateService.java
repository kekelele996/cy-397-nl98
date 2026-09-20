package com.contractapi.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.contractapi.constants.ErrorCode;
import com.contractapi.entity.ContractTemplate;
import com.contractapi.exception.ApiException;
import com.contractapi.mapper.ContractTemplateMapper;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TemplateService {
  private final ContractTemplateMapper templateMapper;

  public TemplateService(ContractTemplateMapper templateMapper) {
    this.templateMapper = templateMapper;
  }

  /** 合同生成只允许使用处于可用状态的模板。 */
  public List<ContractTemplate> list() {
    return templateMapper.selectList(new QueryWrapper<ContractTemplate>()
        .eq("enabled", true)
        .orderByAsc("id"));
  }

  @Transactional
  public ContractTemplate create(ContractTemplate template) {
    if (template.getEnabled() == null) {
      template.setEnabled(true);
    }
    templateMapper.insert(template);
    return template;
  }

  public ContractTemplate findAvailable(Long id) {
    if (id == null) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "缺少模板 ID");
    }
    ContractTemplate template = templateMapper.selectById(id);
    if (template == null) {
      throw new ApiException(ErrorCode.NOT_FOUND, "模板不存在", HttpStatus.NOT_FOUND);
    }
    if (!Boolean.TRUE.equals(template.getEnabled())) {
      throw new ApiException(ErrorCode.TEMPLATE_UNAVAILABLE, "模板已停用，不能用于生成合同");
    }
    return template;
  }
}
