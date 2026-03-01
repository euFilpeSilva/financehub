package com.financehub.backend.modules.governance.infrastructure;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrashItemSpringDataRepository extends JpaRepository<TrashItemJpaEntity, String> {
  List<TrashItemJpaEntity> findAllByOrderByDeletedAtDesc();
  List<TrashItemJpaEntity> findByPurgeAtBefore(Instant purgeAt);
}

