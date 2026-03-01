package com.financehub.backend.modules.bills.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BillSpringDataRepository extends JpaRepository<BillJpaEntity, String> {
}

