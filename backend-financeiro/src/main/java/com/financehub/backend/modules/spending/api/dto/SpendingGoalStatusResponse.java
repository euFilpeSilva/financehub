package com.financehub.backend.modules.spending.api.dto;

public record SpendingGoalStatusResponse(
  SpendingGoalResponse goal,
  double spentAmount,
  double remainingAmount,
  double usagePercent,
  boolean onTrack,
  String periodLabel
) {
}
