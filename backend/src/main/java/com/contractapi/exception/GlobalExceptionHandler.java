package com.contractapi.exception;

import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@ControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<Map<String, Object>> handleApi(ApiException ex) {
    return build(ex.getStatus(), ex.getCode(), ex.getMessage());
  }

  @ExceptionHandler({
      MethodArgumentNotValidException.class,
      HttpMessageNotReadableException.class,
      MissingServletRequestParameterException.class,
      MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<Map<String, Object>> handleValidation(Exception ex) {
    return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "请求参数不合法");
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleUnknown(Exception ex) {
    log.error("Unhandled exception", ex);
    return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务器内部错误");
  }

  private ResponseEntity<Map<String, Object>> build(HttpStatus status, String code, String message) {
    return ResponseEntity.status(status).body(Map.of(
        "success", false,
        "code", code,
        "message", message,
        "timestamp", Instant.now().toString()));
  }
}
