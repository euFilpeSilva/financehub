package com.financehub.backend.modules.planning.api.dto;

import java.time.LocalDate;

public record PlanningGoalResponse(
  String id,
  String title,
  double targetAmount,
  double currentAmount,
  LocalDate targetDate,
  String notes,
  boolean complete
) {
}
