package com.financehub.backend.modules.statements.api.dto;

import java.time.LocalDate;

public record OfxAnalysisTransactionResponse(
  String fileName,
  String ofxOwnerBankAccountId,
  String ofxOwnerBankLabel,
  LocalDate postedAt,
  int year,
  String yearMonth,
  String sourceType,
  double amount,
  String direction,
  String memo,
  String normalizedMemo,
  String patternKey,
  boolean ignoredByMarker,
  boolean likelyInternalTransfer,
  boolean itauPairDuplicateCandidate
) {
}
