package com.financehub.backend.modules.governance.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppPreferencesSpringDataRepository extends JpaRepository<AppPreferencesJpaEntity, Long> {
}

