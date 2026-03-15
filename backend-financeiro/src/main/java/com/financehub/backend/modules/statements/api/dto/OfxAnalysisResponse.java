package com.financehub.backend.modules.statements.api.dto;

import java.util.List;

public record OfxAnalysisResponse(
  int totalFiles,
  int totalTransactions,
  int totalCredits,
  int totalDebits,
  List<Integer> availableYears,
  List<String> availableYearMonths,
  List<OfxAnalysisGroupResponse> groups,
  List<OfxAnalysisTransactionResponse> transactions,
  List<String> fileWarnings
) {
}
