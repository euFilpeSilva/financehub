package com.financehub.backend.modules.incomes.infrastructure;

import com.financehub.backend.modules.incomes.domain.Income;
import com.financehub.backend.modules.incomes.domain.IncomeRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

@Repository
@Primary
public class OracleIncomeRepository implements IncomeRepository {
  private final IncomeSpringDataRepository jpaRepository;

  public OracleIncomeRepository(IncomeSpringDataRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public List<Income> findAll() {
    return jpaRepository.findAll().stream().map(this::toDomain).toList();
  }

  @Override
  public Optional<Income> findById(String id) {
    return jpaRepository.findById(id).map(this::toDomain);
  }

  @Override
  public Income save(Income income) {
    IncomeJpaEntity saved = jpaRepository.save(toEntity(income));
    return toDomain(saved);
  }

  @Override
  public void deleteById(String id) {
    jpaRepository.deleteById(id);
  }

  private Income toDomain(IncomeJpaEntity entity) {
    return new Income(
      entity.getId(),
      entity.getSource(),
      entity.getCategory(),
      entity.getAmount().doubleValue(),
      entity.getReceivedAt(),
      entity.isRecurring(),
      entity.isInternalTransfer(),
      entity.getBankAccountId()
    );
  }

  private IncomeJpaEntity toEntity(Income income) {
    return new IncomeJpaEntity(
      income.getId(),
      income.getSource(),
      income.getCategory(),
      BigDecimal.valueOf(income.getAmount()),
      income.getReceivedAt(),
      income.isRecurring(),
      income.isInternalTransfer(),
      income.getBankAccountId()
    );
  }
}
