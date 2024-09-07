package com.igormaznitsa.mvnjlink.utils;

import java.io.IOException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.http.HttpResponse;

public class HttpResponseException extends IOException {

  private final HttpResponse causeResponse;

  public HttpResponseException(@Nullable final String message,
                               @Nullable final HttpResponse causeResponse) {
    super(message);
    this.causeResponse = causeResponse;
  }

  @Nullable
  public HttpResponse getCauseResponse() {
    return this.causeResponse;
  }

  @Override
  @Nonnull
  public String toString() {
    return "HttpResponseException{" + "message=" + this.getMessage() + ", causeResponse=" +
        causeResponse + '}';
  }
}
