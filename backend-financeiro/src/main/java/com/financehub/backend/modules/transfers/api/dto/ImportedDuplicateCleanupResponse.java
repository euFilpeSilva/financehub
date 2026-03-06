package com.financehub.backend.modules.transfers.api.dto;

public record ImportedDuplicateCleanupResponse(
  int groupsDetected,
  int billsDetected,
  int billsRemoved,
  boolean dryRun
) {
}
