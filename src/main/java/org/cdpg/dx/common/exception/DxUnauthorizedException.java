package org.cdpg.dx.common.exception;

import org.cdpg.dx.auth.authentication.exception.AuthenticationException;

public class DxUnauthorizedException extends AuthenticationException {
  public DxUnauthorizedException(String message) {
    super(message);
  }

  /* public DxUnauthorizedException(String message, Throwable cause) {
      super(DxErrorCodes.UNAUTHORIZED, message, cause);
  }*/
}
