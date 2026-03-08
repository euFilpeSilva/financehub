package com.financehub.backend.modules.statements.api.dto;

public record OfxImportResponse(
  String fileName,
  int totalTransactions,
  int createdBills,
  int createdIncomes,
  int skippedDuplicates,
  int ignoredAlreadyImported,
  int internalTransfersMarked
) {
}

