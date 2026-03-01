package com.financehub.backend.modules.transfers.api.dto;

import jakarta.validation.constraints.NotBlank;

public record InternalTransferLinkRequest(
  @NotBlank String billId,
  @NotBlank String incomeId
) {
}

