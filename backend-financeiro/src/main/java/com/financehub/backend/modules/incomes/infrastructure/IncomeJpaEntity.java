package com.financehub.backend.modules.incomes.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "incomes")
public class IncomeJpaEntity {
  @Id
  @Column(name = "id", nullable = false, length = 36)
  private String id;

  @Column(name = "source", nullable = false, length = 255)
  private String source;

  @Column(name = "category", nullable = false, length = 120)
  private String category;

  @Column(name = "amount", nullable = false, precision = 15, scale = 2)
  private BigDecimal amount;

  @Column(name = "received_at", nullable = false)
  private LocalDate receivedAt;

  @Column(name = "recurring", nullable = false)
  private boolean recurring;

  @Column(name = "internal_transfer", nullable = false)
  private boolean internalTransfer;

  @Column(name = "bank_account_id", length = 36)
  private String bankAccountId;

  protected IncomeJpaEntity() {
  }

  public IncomeJpaEntity(
    String id,
    String source,
    String category,
    BigDecimal amount,
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

  public String getCategory() {
    return category;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public LocalDate getReceivedAt() {
    return receivedAt;
  }

  public boolean isRecurring() {
    return recurring;
  }

  public boolean isInternalTransfer() {
    return internalTransfer;
  }

  public String getBankAccountId() {
    return bankAccountId;
  }
}
