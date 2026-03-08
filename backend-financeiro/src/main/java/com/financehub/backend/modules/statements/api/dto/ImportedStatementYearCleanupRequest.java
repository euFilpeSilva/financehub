package com.financehub.backend.modules.statements.api.dto;

public record ImportedStatementYearCleanupRequest(
  int year,
  String bankAccountId,
  boolean dryRun,
  boolean permanentDelete
) {
}
