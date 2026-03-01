package com.financehub.backend.modules.governance.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RetentionSettingsRequest(
  @Min(1) @Max(3650) int trashRetentionDays,
  @Min(1) @Max(3650) int auditRetentionDays
) {
}
