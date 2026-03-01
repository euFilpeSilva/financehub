package com.financehub.backend.modules.bankaccounts.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BankAccountSpringDataRepository extends JpaRepository<BankAccountJpaEntity, String> {
}
