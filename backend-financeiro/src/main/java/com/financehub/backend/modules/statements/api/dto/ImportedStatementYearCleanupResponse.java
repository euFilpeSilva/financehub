package com.financehub.backend.modules.statements.api.dto;

import java.time.LocalDate;

public record ImportedStatementYearCleanupResponse(
  int year,
  LocalDate startDate,
  LocalDate endDate,
  String bankAccountId,
  boolean dryRun,
  boolean permanentDelete,
  int matchedBills,
  int matchedIncomes,
  int processedBills,
  int processedIncomes,
  int movedToTrash,
  int deletedPermanently,
  int totalMatched,
  int totalProcessed
) {
}
