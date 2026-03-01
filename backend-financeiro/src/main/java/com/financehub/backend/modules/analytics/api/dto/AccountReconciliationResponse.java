package com.financehub.backend.modules.analytics.api.dto;

import java.time.LocalDate;

public record AccountReconciliationResponse(
  String bankAccountId,
  String bankAccountLabel,
  LocalDate startDate,
  LocalDate endDate,
  double incomeTotal,
  double expenseTotal,
  double calculatedBalance,
  Double referenceBalance,
  Double difference,
  int incomeCount,
  int expenseCount,
  int legacyIncomeMatches,
  int legacyExpenseMatches
) {
}
