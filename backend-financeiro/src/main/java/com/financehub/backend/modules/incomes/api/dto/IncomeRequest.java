package com.financehub.backend.modules.incomes.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record IncomeRequest(
  @NotBlank String source,
  @NotBlank String category,
  @DecimalMin("0.01") double amount,
  @NotNull LocalDate receivedAt,
  boolean recurring,
  boolean internalTransfer,
  String bankAccountId
) {
}
