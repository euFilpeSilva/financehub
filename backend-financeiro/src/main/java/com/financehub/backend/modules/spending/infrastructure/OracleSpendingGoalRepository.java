package com.financehub.backend.modules.spending.infrastructure;

import com.financehub.backend.modules.spending.domain.SpendingGoal;
import com.financehub.backend.modules.spending.domain.SpendingGoalRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

@Repository
@Primary
public class OracleSpendingGoalRepository implements SpendingGoalRepository {
  private final SpendingGoalSpringDataRepository jpaRepository;

  public OracleSpendingGoalRepository(SpendingGoalSpringDataRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public List<SpendingGoal> findAll() {
    return jpaRepository.findAll().stream().map(this::toDomain).toList();
  }

  @Override
  public Optional<SpendingGoal> findById(String id) {
    return jpaRepository.findById(id).map(this::toDomain);
  }

  @Override
  public SpendingGoal save(SpendingGoal goal) {
    SpendingGoalJpaEntity saved = jpaRepository.save(toEntity(goal));
    return toDomain(saved);
  }

  @Override
  public void deleteById(String id) {
    jpaRepository.deleteById(id);
  }

  private SpendingGoal toDomain(SpendingGoalJpaEntity entity) {
    return new SpendingGoal(
      entity.getId(),
      entity.getTitle(),
      entity.getLimitAmount().doubleValue(),
      entity.getCategory(),
      entity.getSchedule(),
      entity.getStartMonth(),
      entity.getStartDate(),
      entity.getEndDate(),
      entity.isActive()
    );
  }

  private SpendingGoalJpaEntity toEntity(SpendingGoal goal) {
    return new SpendingGoalJpaEntity(
      goal.getId(),
      goal.getTitle(),
      BigDecimal.valueOf(goal.getLimitAmount()),
      goal.getCategory(),
      goal.getSchedule(),
      goal.getStartMonth(),
      goal.getStartDate(),
      goal.getEndDate(),
      goal.isActive()
    );
  }
}

