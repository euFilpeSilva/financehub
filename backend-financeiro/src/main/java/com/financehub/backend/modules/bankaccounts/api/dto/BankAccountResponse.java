package com.financehub.backend.modules.bankaccounts.api.dto;

public record BankAccountResponse(
  String id,
  String label,
  String bankId,
  String branchId,
  String accountId,
  boolean primaryIncome,
  boolean active
) {
}
