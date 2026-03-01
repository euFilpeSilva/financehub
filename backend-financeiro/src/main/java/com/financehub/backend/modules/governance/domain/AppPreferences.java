package com.financehub.backend.modules.governance.domain;

import java.util.List;

public record AppPreferences(
  String defaultBillCategory,
  boolean defaultBillRecurring,
  int defaultBillDueDay,
  String defaultIncomeCategory,
  boolean defaultIncomeRecurring,
  int defaultIncomeReceivedDay,
  String defaultDashboardMode,
  int defaultDashboardMonthComparisonOffset,
  List<String> billCategories,
  List<String> incomeCategories
) {
}
