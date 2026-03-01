package com.financehub.backend.modules.governance.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_preferences")
public class AppPreferencesJpaEntity {
  @Id
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "default_bill_category", nullable = false, length = 120)
  private String defaultBillCategory;

  @Column(name = "default_bill_recurring", nullable = false)
  private boolean defaultBillRecurring;

  @Column(name = "default_bill_due_day", nullable = false)
  private int defaultBillDueDay;

  @Column(name = "default_income_category", nullable = false, length = 120)
  private String defaultIncomeCategory;

  @Column(name = "default_income_recurring", nullable = false)
  private boolean defaultIncomeRecurring;

  @Column(name = "default_income_received_day", nullable = false)
  private int defaultIncomeReceivedDay;

  @Column(name = "default_dashboard_mode", nullable = false, length = 20)
  private String defaultDashboardMode;

  @Column(name = "default_dashboard_month_comparison_offset", nullable = false)
  private int defaultDashboardMonthComparisonOffset;

  @Column(name = "bill_categories", nullable = false, length = 2000)
  private String billCategories;

  @Column(name = "income_categories", nullable = false, length = 2000)
  private String incomeCategories;

  protected AppPreferencesJpaEntity() {
  }

  public AppPreferencesJpaEntity(
    Long id,
    String defaultBillCategory,
    boolean defaultBillRecurring,
    int defaultBillDueDay,
    String defaultIncomeCategory,
    boolean defaultIncomeRecurring,
    int defaultIncomeReceivedDay,
    String defaultDashboardMode,
    int defaultDashboardMonthComparisonOffset,
    String billCategories,
    String incomeCategories
  ) {
    this.id = id;
    this.defaultBillCategory = defaultBillCategory;
    this.defaultBillRecurring = defaultBillRecurring;
    this.defaultBillDueDay = defaultBillDueDay;
    this.defaultIncomeCategory = defaultIncomeCategory;
    this.defaultIncomeRecurring = defaultIncomeRecurring;
    this.defaultIncomeReceivedDay = defaultIncomeReceivedDay;
    this.defaultDashboardMode = defaultDashboardMode;
    this.defaultDashboardMonthComparisonOffset = defaultDashboardMonthComparisonOffset;
    this.billCategories = billCategories;
    this.incomeCategories = incomeCategories;
  }

  public Long getId() {
    return id;
  }

  public String getDefaultBillCategory() {
    return defaultBillCategory;
  }

  public boolean isDefaultBillRecurring() {
    return defaultBillRecurring;
  }

  public int getDefaultBillDueDay() {
    return defaultBillDueDay;
  }

  public String getDefaultIncomeCategory() {
    return defaultIncomeCategory;
  }

  public boolean isDefaultIncomeRecurring() {
    return defaultIncomeRecurring;
  }

  public int getDefaultIncomeReceivedDay() {
    return defaultIncomeReceivedDay;
  }

  public String getDefaultDashboardMode() {
    return defaultDashboardMode;
  }

  public int getDefaultDashboardMonthComparisonOffset() {
    return defaultDashboardMonthComparisonOffset;
  }

  public String getBillCategories() {
    return billCategories;
  }

  public String getIncomeCategories() {
    return incomeCategories;
  }
}
