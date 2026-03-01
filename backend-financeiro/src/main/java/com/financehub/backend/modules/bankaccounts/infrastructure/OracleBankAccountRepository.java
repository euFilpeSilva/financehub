package com.financehub.backend.modules.bankaccounts.infrastructure;

import com.financehub.backend.modules.bankaccounts.domain.BankAccount;
import com.financehub.backend.modules.bankaccounts.domain.BankAccountRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

@Repository
@Primary
public class OracleBankAccountRepository implements BankAccountRepository {
  private final BankAccountSpringDataRepository jpaRepository;

  public OracleBankAccountRepository(BankAccountSpringDataRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public List<BankAccount> findAll() {
    return jpaRepository.findAll().stream().map(this::toDomain).toList();
  }

  @Override
  public Optional<BankAccount> findById(String id) {
    return jpaRepository.findById(id).map(this::toDomain);
  }

  @Override
  public BankAccount save(BankAccount bankAccount) {
    BankAccountJpaEntity saved = jpaRepository.save(toEntity(bankAccount));
    return toDomain(saved);
  }

  @Override
  public void deleteById(String id) {
    jpaRepository.deleteById(id);
  }

  private BankAccount toDomain(BankAccountJpaEntity entity) {
    return new BankAccount(
      entity.getId(),
      entity.getLabel(),
      entity.getBankId(),
      entity.getBranchId(),
      entity.getAccountId(),
      entity.isPrimaryIncome(),
      entity.isActive()
    );
  }

  private BankAccountJpaEntity toEntity(BankAccount bankAccount) {
    return new BankAccountJpaEntity(
      bankAccount.getId(),
      bankAccount.getLabel(),
      bankAccount.getBankId(),
      bankAccount.getBranchId(),
      bankAccount.getAccountId(),
      bankAccount.isPrimaryIncome(),
      bankAccount.isActive()
    );
  }
}
