package com.financehub.backend.modules.spending.domain;

import java.time.LocalDate;

public class SpendingGoal {
  private final String id;
  private String title;
  private double limitAmount;
  private String category;
  private String schedule;
  private String startMonth;
  private LocalDate startDate;
  private LocalDate endDate;
  private boolean active;

  public SpendingGoal(
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
    this.id = id;
    this.title = title;
    this.limitAmount = limitAmount;
    this.category = category;
    this.schedule = schedule;
    this.startMonth = startMonth;
    this.startDate = startDate;
    this.endDate = endDate;
    this.active = active;
  }

  public String getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public double getLimitAmount() {
    return limitAmount;
  }

  public void setLimitAmount(double limitAmount) {
    this.limitAmount = limitAmount;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getSchedule() {
    return schedule;
  }

  public void setSchedule(String schedule) {
    this.schedule = schedule;
  }

  public String getStartMonth() {
    return startMonth;
  }

  public void setStartMonth(String startMonth) {
    this.startMonth = startMonth;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public void setStartDate(LocalDate startDate) {
    this.startDate = startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public void setEndDate(LocalDate endDate) {
    this.endDate = endDate;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }
}

