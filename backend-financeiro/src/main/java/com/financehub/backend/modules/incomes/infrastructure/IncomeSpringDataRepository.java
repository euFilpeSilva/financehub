package com.financehub.backend.modules.incomes.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IncomeSpringDataRepository extends JpaRepository<IncomeJpaEntity, String> {
}

