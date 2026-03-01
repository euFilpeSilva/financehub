package com.financehub.backend.modules.bills.domain;

import java.time.LocalDate;

public class Bill {
  private final String id;
  private String description;
  private String category;
  private double amount;
  private LocalDate dueDate;
  private boolean recurring;
  private boolean paid;
  private boolean internalTransfer;
  private String bankAccountId;

  public Bill(
    String id,
    String description,
    String category,
    double amount,
    LocalDate dueDate,
    boolean recurring,
    boolean paid,
    boolean internalTransfer,
    String bankAccountId
  ) {
    this.id = id;
    this.description = description;
    this.category = category;
    this.amount = amount;
    this.dueDate = dueDate;
    this.recurring = recurring;
    this.paid = paid;
    this.internalTransfer = internalTransfer;
    this.bankAccountId = bankAccountId;
  }

  public String getId() {
    return id;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public double getAmount() {
    return amount;
  }

  public void setAmount(double amount) {
    this.amount = amount;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public void setDueDate(LocalDate dueDate) {
    this.dueDate = dueDate;
  }

  public boolean isRecurring() {
    return recurring;
  }

  public void setRecurring(boolean recurring) {
    this.recurring = recurring;
  }

  public boolean isPaid() {
    return paid;
  }

  public void setPaid(boolean paid) {
    this.paid = paid;
  }

  public boolean isInternalTransfer() {
    return internalTransfer;
  }

  public void setInternalTransfer(boolean internalTransfer) {
    this.internalTransfer = internalTransfer;
  }

  public String getBankAccountId() {
    return bankAccountId;
  }

  public void setBankAccountId(String bankAccountId) {
    this.bankAccountId = bankAccountId;
  }
}
