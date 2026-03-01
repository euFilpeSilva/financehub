package com.financehub.backend.modules.governance.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "retention_settings")
public class RetentionSettingsJpaEntity {
  @Id
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "trash_retention_days", nullable = false)
  private int trashRetentionDays;

  @Column(name = "audit_retention_days", nullable = false)
  private int auditRetentionDays;

  protected RetentionSettingsJpaEntity() {
  }

  public RetentionSettingsJpaEntity(Long id, int trashRetentionDays, int auditRetentionDays) {
    this.id = id;
    this.trashRetentionDays = trashRetentionDays;
    this.auditRetentionDays = auditRetentionDays;
  }

  public Long getId() {
    return id;
  }

  public int getTrashRetentionDays() {
    return trashRetentionDays;
  }

  public int getAuditRetentionDays() {
    return auditRetentionDays;
  }
}

