package com.financehub.backend.modules.spending.api.dto;

import java.time.LocalDate;

public record SpendingGoalResponse(
  String id,
  String title,
  double limitAmount,
  String category,
  String schedule,
  String startMonth,
  LocalDate startDate,
  LocalDate endDate,
  boolean active
) {
}

