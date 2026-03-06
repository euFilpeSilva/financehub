package com.financehub.backend.modules.transfers.api.dto;

public record InternalTransferReclassificationResponse(
  int billsMarked,
  int incomesMarked,
  int totalMarked
) {
}
