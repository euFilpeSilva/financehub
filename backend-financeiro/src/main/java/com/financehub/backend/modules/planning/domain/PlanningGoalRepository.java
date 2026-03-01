package com.financehub.backend.modules.planning.domain;

import java.util.List;
import java.util.Optional;

public interface PlanningGoalRepository {
  List<PlanningGoal> findAll();
  Optional<PlanningGoal> findById(String id);
  PlanningGoal save(PlanningGoal goal);
  void deleteById(String id);
}
