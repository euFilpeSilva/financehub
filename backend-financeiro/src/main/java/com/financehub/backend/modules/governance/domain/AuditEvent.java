package com.financehub.backend.modules.governance.domain;

import java.time.Instant;

public record AuditEvent(
  String id,
  String entityType,
  String entityId,
  String action,
  String message,
  Double amount,
  Instant timestamp
) {
}
