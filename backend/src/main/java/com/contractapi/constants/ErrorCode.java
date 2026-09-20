package com.contractapi.constants;

public final class ErrorCode {
  public static final String NOT_FOUND = "NOT_FOUND";
  public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
  public static final String PDF_EXPORT_FAILED = "PDF_EXPORT_FAILED";
  public static final String TEMPLATE_NOT_FOUND = "TEMPLATE_NOT_FOUND";
  public static final String CONTRACT_NOT_FOUND = "CONTRACT_NOT_FOUND";
  public static final String CONTRACT_STATUS_INVALID = "CONTRACT_STATUS_INVALID";
  public static final String CONTRACT_EXPIRED = "CONTRACT_EXPIRED";
  public static final String SIGNER_NOT_FOUND = "SIGNER_NOT_FOUND";
  public static final String SIGNER_ALREADY_CONFIRMED = "SIGNER_ALREADY_CONFIRMED";
  public static final String SIGN_ORDER_NOT_READY = "SIGN_ORDER_NOT_READY";
  public static final String FORBIDDEN = "FORBIDDEN";
  private ErrorCode() {}
}
