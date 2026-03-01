package com.financehub.backend.modules.bankaccounts.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "bank_accounts")
public class BankAccountJpaEntity {
  @Id
  @Column(name = "id", nullable = false, length = 36)
  private String id;

  @Column(name = "label", nullable = false, length = 120)
  private String label;

  @Column(name = "bank_id", nullable = false, length = 20)
  private String bankId;

  @Column(name = "branch_id", length = 20)
  private String branchId;

  @Column(name = "account_id", nullable = false, length = 40)
  private String accountId;

  @Column(name = "primary_income", nullable = false)
  private boolean primaryIncome;

  @Column(name = "active", nullable = false)
  private boolean active;

  protected BankAccountJpaEntity() {
  }

  public BankAccountJpaEntity(
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

  public String getBankId() {
    return bankId;
  }

  public String getBranchId() {
    return branchId;
  }

  public String getAccountId() {
    return accountId;
  }

  public boolean isPrimaryIncome() {
    return primaryIncome;
  }

  public boolean isActive() {
    return active;
  }
}
