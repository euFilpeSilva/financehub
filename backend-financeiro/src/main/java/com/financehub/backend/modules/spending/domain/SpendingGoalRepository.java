package com.financehub.backend.modules.spending.domain;

import java.util.List;
import java.util.Optional;

public interface SpendingGoalRepository {
  List<SpendingGoal> findAll();
  Optional<SpendingGoal> findById(String id);
  SpendingGoal save(SpendingGoal goal);
  void deleteById(String id);
}

