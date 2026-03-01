package com.financehub.backend.modules.planning.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "planning_goals")
public class PlanningGoalJpaEntity {
  @Id
  @Column(name = "id", nullable = false, length = 36)
  private String id;

  @Column(name = "title", nullable = false, length = 180)
  private String title;

  @Column(name = "target_amount", nullable = false, precision = 15, scale = 2)
  private BigDecimal targetAmount;

  @Column(name = "current_amount", nullable = false, precision = 15, scale = 2)
  private BigDecimal currentAmount;

  @Column(name = "target_date", nullable = false)
  private LocalDate targetDate;

  @Lob
  @Column(name = "notes")
  private String notes;

  @Column(name = "complete", nullable = false)
  private boolean complete;

  protected PlanningGoalJpaEntity() {
  }

  public PlanningGoalJpaEntity(
    String id,
    String title,
    BigDecimal targetAmount,
    BigDecimal currentAmount,
    LocalDate targetDate,
    String notes,
    boolean complete
  ) {
    this.id = id;
    this.title = title;
    this.targetAmount = targetAmount;
    this.currentAmount = currentAmount;
    this.targetDate = targetDate;
    this.notes = notes;
    this.complete = complete;
  }

  public String getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public BigDecimal getTargetAmount() {
    return targetAmount;
  }

  public BigDecimal getCurrentAmount() {
    return currentAmount;
  }

  public LocalDate getTargetDate() {
    return targetDate;
  }

  public String getNotes() {
    return notes;
  }

  public boolean isComplete() {
    return complete;
  }
}

