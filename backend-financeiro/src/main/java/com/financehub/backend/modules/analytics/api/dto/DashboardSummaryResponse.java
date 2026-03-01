package com.financehub.backend.modules.analytics.api.dto;

public record DashboardSummaryResponse(
  double incomeTotal,
  double expenseTotal,
  double balance,
  double paidBillsTotal,
  double pendingBillsTotal
) {
}
