package com.financehub.backend.modules.governance.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public record AppPreferencesRequest(
  @NotBlank String defaultBillCategory,
  boolean defaultBillRecurring,
  @Min(1) @Max(31) int defaultBillDueDay,
  @NotBlank String defaultIncomeCategory,
  boolean defaultIncomeRecurring,
  @Min(1) @Max(31) int defaultIncomeReceivedDay,
  @NotBlank @Pattern(regexp = "month|range") String defaultDashboardMode,
  @Min(1) @Max(12) int defaultDashboardMonthComparisonOffset,
  @NotEmpty List<@NotBlank String> billCategories,
  @NotEmpty List<@NotBlank String> incomeCategories
) {
}
