package com.financehub.backend.modules.planning.infrastructure;

import com.financehub.backend.modules.planning.domain.PlanningGoal;
import com.financehub.backend.modules.planning.domain.PlanningGoalRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

@Repository
@Primary
public class OraclePlanningGoalRepository implements PlanningGoalRepository {
  private final PlanningGoalSpringDataRepository jpaRepository;

  public OraclePlanningGoalRepository(PlanningGoalSpringDataRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public List<PlanningGoal> findAll() {
    return jpaRepository.findAll().stream().map(this::toDomain).toList();
  }

  @Override
  public Optional<PlanningGoal> findById(String id) {
    return jpaRepository.findById(id).map(this::toDomain);
  }

  @Override
  public PlanningGoal save(PlanningGoal goal) {
    PlanningGoalJpaEntity saved = jpaRepository.save(toEntity(goal));
    return toDomain(saved);
  }

  @Override
  public void deleteById(String id) {
    jpaRepository.deleteById(id);
  }

  private PlanningGoal toDomain(PlanningGoalJpaEntity entity) {
    return new PlanningGoal(
      entity.getId(),
      entity.getTitle(),
      entity.getTargetAmount().doubleValue(),
      entity.getCurrentAmount().doubleValue(),
      entity.getTargetDate(),
      entity.getNotes() == null ? "" : entity.getNotes(),
      entity.isComplete()
    );
  }

  private PlanningGoalJpaEntity toEntity(PlanningGoal goal) {
    return new PlanningGoalJpaEntity(
      goal.getId(),
      goal.getTitle(),
      BigDecimal.valueOf(goal.getTargetAmount()),
      BigDecimal.valueOf(goal.getCurrentAmount()),
      goal.getTargetDate(),
      goal.getNotes(),
      goal.isComplete()
    );
  }
}

