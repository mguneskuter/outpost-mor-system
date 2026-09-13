package com.outpost.framework.security.web;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * A request whose body a filter has already read in full, served again from those bytes so the body
 * stays readable downstream.
 */
public final class CachedBodyRequest extends HttpServletRequestWrapper {
  private final byte[] body;

  /** Wraps {@code request} so that its input is {@code body}. */
  public CachedBodyRequest(HttpServletRequest request, byte[] body) {
    super(request);
    this.body = body.clone();
  }

  @Override
  public ServletInputStream getInputStream() {
    ByteArrayInputStream input = new ByteArrayInputStream(body);
    return new ServletInputStream() {
      @Override
      public int read() {
        return input.read();
      }

      @Override
      public int read(byte[] bytes, int offset, int length) {
        return input.read(bytes, offset, length);
      }

      @Override
      public boolean isFinished() {
        return input.available() == 0;
      }

      @Override
      public boolean isReady() {
        return true;
      }

      @Override
      public void setReadListener(ReadListener listener) {}
    };
  }

  @Override
  public BufferedReader getReader() {
    return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
  }
}
