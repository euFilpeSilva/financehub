package com.financehub.backend.modules.governance.domain;

import java.time.Instant;

public record TrashItem(
  String id,
  String entityType,
  String entityId,
  String label,
  String payload,
  Instant deletedAt,
  Instant purgeAt
) {
}

