package com.contractapi.service;

import java.util.List;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.contractapi.constants.ErrorCode;
import com.contractapi.entity.ContractTemplate;
import com.contractapi.exception.ApiException;
import com.contractapi.mapper.ContractTemplateMapper;
import org.springframework.stereotype.Service;

@Service
public class TemplateService {
  private final ContractTemplateMapper mapper;

  public TemplateService(ContractTemplateMapper mapper) {
    this.mapper = mapper;
  }

  public List<ContractTemplate> list() {
    return mapper.selectList(new QueryWrapper<ContractTemplate>().orderByAsc("id"));
  }

  public ContractTemplate create(ContractTemplate template) {
    if (template.getType() == null || template.getType().isBlank()
        || template.getTitle() == null || template.getTitle().isBlank()
        || template.getContent() == null || template.getContent().isBlank()) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "模板类型、标题与内容不能为空");
    }
    mapper.insert(template);
    return template;
  }

  /** 可用模板必须存在，否则不允许生成合同 */
  public ContractTemplate find(Long id) {
    ContractTemplate template = id == null ? null : mapper.selectById(id);
    if (template == null) {
      throw new ApiException(ErrorCode.TEMPLATE_NOT_FOUND, "模板不存在或已下架：" + id);
    }
    return template;
  }
}
