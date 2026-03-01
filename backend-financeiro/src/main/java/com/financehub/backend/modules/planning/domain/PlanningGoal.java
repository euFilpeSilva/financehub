package com.financehub.backend.modules.planning.domain;

import java.time.LocalDate;

public class PlanningGoal {
  private final String id;
  private String title;
  private double targetAmount;
  private double currentAmount;
  private LocalDate targetDate;
  private String notes;
  private boolean complete;

  public PlanningGoal(String id, String title, double targetAmount, double currentAmount, LocalDate targetDate, String notes, boolean complete) {
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

  public void setTitle(String title) {
    this.title = title;
  }

  public double getTargetAmount() {
    return targetAmount;
  }

  public void setTargetAmount(double targetAmount) {
    this.targetAmount = targetAmount;
  }

  public double getCurrentAmount() {
    return currentAmount;
  }

  public void setCurrentAmount(double currentAmount) {
    this.currentAmount = currentAmount;
  }

  public LocalDate getTargetDate() {
    return targetDate;
  }

  public void setTargetDate(LocalDate targetDate) {
    this.targetDate = targetDate;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public boolean isComplete() {
    return complete;
  }

  public void setComplete(boolean complete) {
    this.complete = complete;
  }
}
