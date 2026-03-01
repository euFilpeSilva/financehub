package com.financehub.backend.modules.bankaccounts.domain;

public class BankAccount {
  private final String id;
  private String label;
  private String bankId;
  private String branchId;
  private String accountId;
  private boolean primaryIncome;
  private boolean active;

  public BankAccount(
    String id,
    String label,
    String bankId,
    String branchId,
    String accountId,
    boolean primaryIncome,
    boolean active
  ) {
    this.id = id;
    this.label = label;
    this.bankId = bankId;
    this.branchId = branchId;
    this.accountId = accountId;
    this.primaryIncome = primaryIncome;
    this.active = active;
  }

  public String getId() {
    return id;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public String getBankId() {
    return bankId;
  }

  public void setBankId(String bankId) {
    this.bankId = bankId;
  }

  public String getBranchId() {
    return branchId;
  }

  public void setBranchId(String branchId) {
    this.branchId = branchId;
  }

  public String getAccountId() {
    return accountId;
  }

  public void setAccountId(String accountId) {
    this.accountId = accountId;
  }

  public boolean isPrimaryIncome() {
    return primaryIncome;
  }

  public void setPrimaryIncome(boolean primaryIncome) {
    this.primaryIncome = primaryIncome;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }
}
