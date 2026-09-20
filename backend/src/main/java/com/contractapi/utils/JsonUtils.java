package com.contractapi.utils;

import com.contractapi.constants.ErrorCode;
import com.contractapi.exception.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class JsonUtils {
  private final ObjectMapper objectMapper;

  public JsonUtils(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "数据序列化失败");
    }
  }

  public <T> T fromJson(String json, Class<T> type) {
    try {
      return objectMapper.readValue(json, type);
    } catch (JsonProcessingException ex) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "数据反序列化失败");
    }
  }

  public List<String> toStringList(String json) {
    try {
      return objectMapper.readValue(json, new TypeReference<List<String>>() {});
    } catch (JsonProcessingException ex) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "模板变量解析失败");
    }
  }
}
