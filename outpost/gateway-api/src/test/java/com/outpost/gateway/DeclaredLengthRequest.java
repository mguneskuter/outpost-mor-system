package com.outpost.gateway;

import jakarta.servlet.ServletInputStream;
import org.springframework.mock.web.MockHttpServletRequest;

/** A POST that declares a body length and fails the test if its body is read. */
public final class DeclaredLengthRequest extends MockHttpServletRequest {
  private final long declaredLength;

  /** Creates a POST to {@code requestUri} declaring {@code declaredLength} bytes of body. */
  public DeclaredLengthRequest(String requestUri, long declaredLength) {
    super("POST", requestUri);
    this.declaredLength = declaredLength;
  }

  @Override
  public long getContentLengthLong() {
    return declaredLength;
  }

  @Override
  public ServletInputStream getInputStream() {
    throw new AssertionError("the body was read");
  }
}
