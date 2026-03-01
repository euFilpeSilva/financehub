package com.financehub.backend.modules.bills.api.dto;

import java.time.LocalDate;

public record BillResponse(
  String id,
  String description,
  String category,
  double amount,
  LocalDate dueDate,
  boolean recurring,
  boolean paid,
  boolean internalTransfer,
  String bankAccountId
) {
}
