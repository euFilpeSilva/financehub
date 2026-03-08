package com.financehub.backend.modules.statements.api.dto;

import java.util.List;

public record ImportedStatementYearCleanupRequest(
  int year,
  Integer month,
  List<String> bankAccountIds,
  boolean dryRun,
  boolean permanentDelete
) {
}
