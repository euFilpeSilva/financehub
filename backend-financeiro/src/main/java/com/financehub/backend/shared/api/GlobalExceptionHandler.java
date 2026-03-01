package com.financehub.backend.shared.api;

import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ApiError> handleNotFound(NotFoundException ex) {
    return build(HttpStatus.NOT_FOUND, ex.getMessage(), List.of());
  }

  @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class, IllegalArgumentException.class})
  public ResponseEntity<ApiError> handleBadRequest(Exception ex) {
    List<String> details = List.of();
    if (ex instanceof MethodArgumentNotValidException validationEx) {
      details = validationEx.getBindingResult()
        .getFieldErrors()
        .stream()
        .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
        .toList();
    }
    return build(HttpStatus.BAD_REQUEST, ex.getMessage(), details);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleGeneric(Exception ex) {
    return build(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno inesperado", List.of(ex.getMessage()));
  }

  private ResponseEntity<ApiError> build(HttpStatus status, String message, List<String> details) {
    return ResponseEntity.status(status).body(
      new ApiError(
        Instant.now(),
        status.value(),
        status.getReasonPhrase(),
        message,
        details
      )
    );
  }
}
