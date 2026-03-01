package com.financehub.backend.modules.bankaccounts.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BankAccountRequest(
  @NotBlank @Size(max = 120) String label,
  @NotBlank @Size(max = 20) String bankId,
  @Size(max = 20) String branchId,
  @NotBlank @Size(max = 40) String accountId,
  boolean primaryIncome,
  boolean active
) {
}
