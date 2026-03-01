package com.financehub.backend.shared.application.port;

public interface AuditPort {
  void record(String entityType, String entityId, String action, String message, Double amount);
}
