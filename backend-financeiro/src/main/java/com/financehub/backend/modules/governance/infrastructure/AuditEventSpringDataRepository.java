package com.financehub.backend.modules.governance.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface AuditEventSpringDataRepository extends JpaRepository<AuditEventJpaEntity, String> {
  List<AuditEventJpaEntity> findAllByOrderByTimestampDesc();
  @Transactional
  void deleteByTimestampBefore(java.time.Instant timestamp);
}
