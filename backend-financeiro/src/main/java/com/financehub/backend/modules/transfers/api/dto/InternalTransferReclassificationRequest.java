package com.financehub.backend.modules.transfers.api.dto;

public record InternalTransferReclassificationRequest(
  String ownerName,
  String ownerCpf,
  Boolean includePicpay,
  Boolean includeLegacyBroker,
  Boolean includeInvestmentPurchases
) {
}
