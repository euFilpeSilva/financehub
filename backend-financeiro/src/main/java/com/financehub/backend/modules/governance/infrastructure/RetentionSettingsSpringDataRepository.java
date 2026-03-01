package com.financehub.backend.modules.governance.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RetentionSettingsSpringDataRepository extends JpaRepository<RetentionSettingsJpaEntity, Long> {
}

