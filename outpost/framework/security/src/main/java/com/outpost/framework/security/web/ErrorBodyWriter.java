package com.outpost.framework.security.web;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Writes the deployable's error body for a request its security filters reject, so a rejection
 * answered before any controller carries the same shape as one answered by a controller.
 */
@FunctionalInterface
public interface ErrorBodyWriter {
  /** Sets the status and writes the body for the error {@code code}. */
  void write(HttpServletResponse response, int status, String code) throws IOException;
}
