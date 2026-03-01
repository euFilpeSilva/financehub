package com.financehub.backend.modules.planning.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record PlanningGoalRequest(
  @NotBlank String title,
  @DecimalMin("0.01") double targetAmount,
  @DecimalMin("0.00") double currentAmount,
  @NotNull LocalDate targetDate,
  String notes,
  boolean complete
) {
}
