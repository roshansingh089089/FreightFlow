package com.freightflow.common.exception;

public class ApiException extends RuntimeException {
  public final int status;
  public final String code;

  public ApiException(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public ApiException(int status, String code, String message, Throwable cause) {
    super(message, cause);
    this.status = status;
    this.code = code;
  }
}
