package com.financehub.backend.modules.planning.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanningGoalSpringDataRepository extends JpaRepository<PlanningGoalJpaEntity, String> {
}

