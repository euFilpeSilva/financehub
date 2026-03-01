package com.financehub.backend.modules.transfers.api.dto;

import java.time.LocalDate;
import java.util.List;

public record InternalTransferSuggestionResponse(
  String billId,
  String incomeId,
  LocalDate billDate,
  LocalDate incomeDate,
  double amount,
  int score,
  List<String> reasons
) {
}

