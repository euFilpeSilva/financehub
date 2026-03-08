package com.financehub.backend.modules.statements.api.dto;

import java.util.List;

public record OfxAnalysisGroupResponse(
  String patternKey,
  String sampleMemo,
  int totalCount,
  int creditCount,
  int debitCount,
  int ignoredCount,
  int likelyInternalCount,
  int itauPairCandidateCount,
  double totalCreditAmount,
  double totalDebitAmount,
  List<Integer> years,
  List<String> yearMonths
) {
}
