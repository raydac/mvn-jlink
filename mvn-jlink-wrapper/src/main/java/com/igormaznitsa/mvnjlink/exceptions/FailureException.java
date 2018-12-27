package com.igormaznitsa.mvnjlink.exceptions;

import javax.annotation.Nonnull;

public class FailureException extends RuntimeException {
  public FailureException(@Nonnull final String text) {
    super(text);
  }

  public FailureException(@Nonnull final String message, @Nonnull final Throwable cause) {
    super(message, cause);
  }
}
