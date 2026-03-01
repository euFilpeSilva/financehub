package com.financehub.backend.modules.governance.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "audit_events")
public class AuditEventJpaEntity {
  @Id
  @Column(name = "id", nullable = false, length = 36)
  private String id;

  @Column(name = "entity_type", nullable = false, length = 60)
  private String entityType;

  @Column(name = "entity_id", nullable = false, length = 120)
  private String entityId;

  @Column(name = "action", nullable = false, length = 40)
  private String action;

  @Column(name = "message", nullable = false, length = 500)
  private String message;

  @Column(name = "amount", precision = 15, scale = 2)
  private BigDecimal amount;

  @Column(name = "event_timestamp", nullable = false)
  private Instant timestamp;

  protected AuditEventJpaEntity() {
  }

  public AuditEventJpaEntity(
    String id,
    String entityType,
    String entityId,
    String action,
    String message,
    BigDecimal amount,
    Instant timestamp
  ) {
    this.id = id;
    this.entityType = entityType;
    this.entityId = entityId;
    this.action = action;
    this.message = message;
    this.amount = amount;
    this.timestamp = timestamp;
  }

  public String getId() {
    return id;
  }

  public String getEntityType() {
    return entityType;
  }

  public String getEntityId() {
    return entityId;
  }

  public String getAction() {
    return action;
  }

  public String getMessage() {
    return message;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public Instant getTimestamp() {
    return timestamp;
  }
}

