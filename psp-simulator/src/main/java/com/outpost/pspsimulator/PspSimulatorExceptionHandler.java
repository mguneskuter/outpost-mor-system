package com.outpost.pspsimulator;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps the API's error vocabulary to HTTP statuses. */
@RestControllerAdvice
public class PspSimulatorExceptionHandler {

  /** Maps unknown references and PSP codes to 404. */
  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<Void> notFound(NotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
  }

  /** Maps state conflicts and idempotency mismatches to 409. */
  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<Void> conflict(ConflictException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT).build();
  }

  /** Maps a missing or invalid API key to 401. */
  @ExceptionHandler(UnauthorizedException.class)
  public ResponseEntity<Void> unauthorized(UnauthorizedException exception) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
  }

  /** Maps invalid request values to 400. */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Void> invalidRequest(IllegalArgumentException exception) {
    return ResponseEntity.badRequest().build();
  }
}
