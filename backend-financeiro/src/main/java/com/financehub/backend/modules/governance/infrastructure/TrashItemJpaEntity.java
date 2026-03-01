package com.financehub.backend.modules.governance.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "trash_items")
public class TrashItemJpaEntity {
  @Id
  @Column(name = "id", nullable = false, length = 36)
  private String id;

  @Column(name = "entity_type", nullable = false, length = 60)
  private String entityType;

  @Column(name = "entity_id", nullable = false, length = 120)
  private String entityId;

  @Column(name = "label", nullable = false, length = 255)
  private String label;

  @Lob
  @Column(name = "payload", nullable = false)
  private String payload;

  @Column(name = "deleted_at", nullable = false)
  private Instant deletedAt;

  @Column(name = "purge_at", nullable = false)
  private Instant purgeAt;

  protected TrashItemJpaEntity() {
  }

  public TrashItemJpaEntity(
    String id,
    String entityType,
    String entityId,
    String label,
    String payload,
    Instant deletedAt,
    Instant purgeAt
  ) {
    this.id = id;
    this.entityType = entityType;
    this.entityId = entityId;
    this.label = label;
    this.payload = payload;
    this.deletedAt = deletedAt;
    this.purgeAt = purgeAt;
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

  public String getLabel() {
    return label;
  }

  public String getPayload() {
    return payload;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public Instant getPurgeAt() {
    return purgeAt;
  }
}

