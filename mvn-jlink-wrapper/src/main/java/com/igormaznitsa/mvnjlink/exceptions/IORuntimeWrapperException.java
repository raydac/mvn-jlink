package com.igormaznitsa.mvnjlink.exceptions;

import javax.annotation.Nonnull;
import java.io.IOException;

import static com.igormaznitsa.meta.common.utils.Assertions.assertNotNull;

public class IORuntimeWrapperException extends RuntimeException {
  public IORuntimeWrapperException(@Nonnull IOException exception) {
    super(assertNotNull(exception));
  }

  @Nonnull
  public IOException getWrapped() {
    return (IOException) this.getCause();
  }
}
