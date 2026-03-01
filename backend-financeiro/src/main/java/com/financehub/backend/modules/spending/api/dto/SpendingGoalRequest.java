package com.financehub.backend.modules.spending.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record SpendingGoalRequest(
  @NotBlank String title,
  @DecimalMin("0.01") double limitAmount,
  @NotBlank String category,
  @NotBlank String schedule,
  String startMonth,
  LocalDate startDate,
  LocalDate endDate,
  boolean active
) {
}

