package com.outpost.framework.security.web;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Optional;

/**
 * A request body a signature filter reads only up to a maximum size. A signature covers the exact
 * raw body, so the body must be buffered before the caller can be authenticated; the maximum size
 * keeps an unauthenticated caller from making that buffer arbitrarily large.
 */
public final class SizeBoundedRequestBody {
  /**
   * The maximum body size in bytes, 1 MiB. Each deployable's container uses the same size for form
   * content it parses and for an unread body it drains before closing the connection; neither
   * setting limits a body the application reads itself.
   */
  public static final int MAX_SIZE_BYTES = 1_048_576;

  private SizeBoundedRequestBody() {}

  /**
   * Reads the whole request body when it is no larger than the maximum size.
   *
   * <p>A body whose declared length exceeds the maximum size is not read at all. A body without a
   * declared length is read at most one byte past the maximum size.
   *
   * @return the body, or empty when it exceeds the maximum size
   */
  public static Optional<byte[]> read(HttpServletRequest request) throws IOException {
    if (request.getContentLengthLong() > MAX_SIZE_BYTES) {
      return Optional.empty();
    }
    byte[] body = request.getInputStream().readNBytes(MAX_SIZE_BYTES + 1);
    return body.length > MAX_SIZE_BYTES ? Optional.empty() : Optional.of(body);
  }
}
