package com.financehub.backend.modules.bills.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record BillRequest(
  @NotBlank String description,
  @NotBlank String category,
  @DecimalMin("0.01") double amount,
  @NotNull LocalDate dueDate,
  boolean recurring,
  boolean paid,
  boolean internalTransfer,
  String bankAccountId
) {
}
