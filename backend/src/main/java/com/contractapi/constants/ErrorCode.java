package com.contractapi.constants;

public final class ErrorCode {
  public static final String NOT_FOUND = "NOT_FOUND";
  public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
  public static final String PDF_EXPORT_FAILED = "PDF_EXPORT_FAILED";
  public static final String TEMPLATE_UNAVAILABLE = "TEMPLATE_UNAVAILABLE";
  public static final String MISSING_TEMPLATE_VARIABLE = "MISSING_TEMPLATE_VARIABLE";
  public static final String DUPLICATE_SIGNER = "DUPLICATE_SIGNER";
  public static final String INVALID_SIGN_ORDER = "INVALID_SIGN_ORDER";
  public static final String INVALID_DEADLINE = "INVALID_DEADLINE";
  public static final String SIGNERS_REQUIRED = "SIGNERS_REQUIRED";
  public static final String SIGNER_NOT_FOUND = "SIGNER_NOT_FOUND";
  public static final String NOT_CONTRACT_OWNER = "NOT_CONTRACT_OWNER";
  public static final String SIGNING_NOT_OPEN = "SIGNING_NOT_OPEN";
  public static final String CONTRACT_TERMINAL = "CONTRACT_TERMINAL";
  public static final String SIGNER_ALREADY_ACTIONED = "SIGNER_ALREADY_ACTIONED";
  public static final String SIGNING_ORDER_BLOCKED = "SIGNING_ORDER_BLOCKED";
  public static final String SIGNING_DEADLINE_PASSED = "SIGNING_DEADLINE_PASSED";
  private ErrorCode() {}
}
