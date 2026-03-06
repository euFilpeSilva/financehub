package com.financehub.backend.modules.transfers.api.dto;

import java.time.LocalDate;
import java.util.List;

public record InternalTransferSuggestionResponse(
  String billId,
  String incomeId,
  String billDescription,
  String incomeSource,
  String billBankAccountId,
  String incomeBankAccountId,
  String billBankAccountLabel,
  String incomeBankAccountLabel,
  LocalDate billDate,
  LocalDate incomeDate,
  double amount,
  int score,
  String confidence,
  List<String> reasons
) {
}

