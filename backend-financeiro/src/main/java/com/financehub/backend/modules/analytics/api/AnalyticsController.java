package com.financehub.backend.modules.analytics.api;

import com.financehub.backend.modules.analytics.api.dto.AccountReconciliationResponse;
import com.financehub.backend.modules.analytics.api.dto.DashboardSummaryResponse;
import com.financehub.backend.modules.analytics.application.AnalyticsService;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {
  private final AnalyticsService service;

  public AnalyticsController(AnalyticsService service) {
    this.service = service;
  }

  @GetMapping("/dashboard-summary")
  public DashboardSummaryResponse dashboardSummary(
    @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
    @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
  ) {
    if (endDate.isBefore(startDate)) {
      throw new IllegalArgumentException("A data final precisa ser maior ou igual a data inicial.");
    }
    return service.summary(startDate, endDate);
  }

  @GetMapping("/account-reconciliation")
  public AccountReconciliationResponse accountReconciliation(
    @RequestParam @NotNull String bankAccountId,
    @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
    @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
    @RequestParam(required = false) Double referenceBalance
  ) {
    if (endDate.isBefore(startDate)) {
      throw new IllegalArgumentException("A data final precisa ser maior ou igual a data inicial.");
    }
    return service.reconcileAccount(bankAccountId, startDate, endDate, referenceBalance);
  }
}
