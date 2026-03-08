package com.financehub.backend.modules.statements.api.dto;

import java.time.LocalDate;
import java.util.List;

public record ImportedStatementYearCleanupResponse(
  int year,
  Integer month,
  LocalDate startDate,
  LocalDate endDate,
  List<String> bankAccountIds,
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
