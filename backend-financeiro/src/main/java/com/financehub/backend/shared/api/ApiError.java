package com.financehub.backend.shared.api;

import java.time.Instant;
import java.util.List;

public record ApiError(
  Instant timestamp,
  int status,
  String error,
  String message,
  List<String> details
) {
}
