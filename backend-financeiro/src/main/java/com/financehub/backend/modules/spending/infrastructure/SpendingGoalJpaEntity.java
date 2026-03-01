package com.financehub.backend.modules.spending.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "spending_goals")
public class SpendingGoalJpaEntity {
  @Id
  @Column(name = "id", nullable = false, length = 36)
  private String id;

  @Column(name = "title", nullable = false, length = 180)
  private String title;

  @Column(name = "limit_amount", nullable = false, precision = 15, scale = 2)
  private BigDecimal limitAmount;

  @Column(name = "category", nullable = false, length = 120)
  private String category;

  @Column(name = "schedule", nullable = false, length = 20)
  private String schedule;

  @Column(name = "start_month", length = 7)
  private String startMonth;

  @Column(name = "start_date")
  private LocalDate startDate;

  @Column(name = "end_date")
  private LocalDate endDate;

  @Column(name = "active", nullable = false)
  private boolean active;

  protected SpendingGoalJpaEntity() {
  }

  public SpendingGoalJpaEntity(
    String id,
    String title,
    BigDecimal limitAmount,
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

  public BigDecimal getLimitAmount() {
    return limitAmount;
  }

  public String getCategory() {
    return category;
  }

  public String getSchedule() {
    return schedule;
  }

  public String getStartMonth() {
    return startMonth;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public boolean isActive() {
    return active;
  }
}

