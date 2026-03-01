package com.financehub.backend.modules.incomes.domain;

import java.time.LocalDate;

public class Income {
  private final String id;
  private String source;
  private String category;
  private double amount;
  private LocalDate receivedAt;
  private boolean recurring;
  private boolean internalTransfer;
  private String bankAccountId;

  public Income(
    String id,
    String source,
    String category,
    double amount,
    LocalDate receivedAt,
    boolean recurring,
    boolean internalTransfer,
    String bankAccountId
  ) {
    this.id = id;
    this.source = source;
    this.category = category;
    this.amount = amount;
    this.receivedAt = receivedAt;
    this.recurring = recurring;
    this.internalTransfer = internalTransfer;
    this.bankAccountId = bankAccountId;
  }

  public String getId() {
    return id;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
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

  public LocalDate getReceivedAt() {
    return receivedAt;
  }

  public void setReceivedAt(LocalDate receivedAt) {
    this.receivedAt = receivedAt;
  }

  public boolean isRecurring() {
    return recurring;
  }

  public void setRecurring(boolean recurring) {
    this.recurring = recurring;
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
