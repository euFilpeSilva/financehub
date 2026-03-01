package com.financehub.backend.modules.bills.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "bills")
public class BillJpaEntity {
  @Id
  @Column(name = "id", nullable = false, length = 36)
  private String id;

  @Column(name = "description", nullable = false, length = 255)
  private String description;

  @Column(name = "category", nullable = false, length = 120)
  private String category;

  @Column(name = "amount", nullable = false, precision = 15, scale = 2)
  private BigDecimal amount;

  @Column(name = "due_date", nullable = false)
  private LocalDate dueDate;

  @Column(name = "recurring", nullable = false)
  private boolean recurring;

  @Column(name = "paid", nullable = false)
  private boolean paid;

  @Column(name = "internal_transfer", nullable = false)
  private boolean internalTransfer;

  @Column(name = "bank_account_id", length = 36)
  private String bankAccountId;

  protected BillJpaEntity() {
  }

  public BillJpaEntity(
    String id,
    String description,
    String category,
    BigDecimal amount,
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

  public String getCategory() {
    return category;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public boolean isRecurring() {
    return recurring;
  }

  public boolean isPaid() {
    return paid;
  }

  public boolean isInternalTransfer() {
    return internalTransfer;
  }

  public String getBankAccountId() {
    return bankAccountId;
  }
}
