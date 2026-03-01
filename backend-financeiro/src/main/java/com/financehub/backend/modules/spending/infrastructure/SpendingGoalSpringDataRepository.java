package com.financehub.backend.modules.spending.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpendingGoalSpringDataRepository extends JpaRepository<SpendingGoalJpaEntity, String> {
}

