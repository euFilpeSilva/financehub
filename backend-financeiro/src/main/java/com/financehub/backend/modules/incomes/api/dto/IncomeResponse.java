package com.financehub.backend.modules.incomes.api.dto;

import java.time.LocalDate;

public record IncomeResponse(
  String id,
  String source,
  String category,
  double amount,
  LocalDate receivedAt,
  boolean recurring,
  boolean internalTransfer,
  String bankAccountId
) {
}
