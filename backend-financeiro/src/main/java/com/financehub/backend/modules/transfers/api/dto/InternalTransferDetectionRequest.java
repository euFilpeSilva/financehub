package com.financehub.backend.modules.transfers.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record InternalTransferDetectionRequest(
  String ownerName,
  String ownerCpf,
  @Min(0) @Max(5) int dateToleranceDays,
  boolean autoApply
) {
}

