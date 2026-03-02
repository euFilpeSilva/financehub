package com.financehub.backend.modules.planning.application;

import com.financehub.backend.modules.planning.api.dto.PlanningGoalRequest;
import com.financehub.backend.modules.planning.domain.PlanningGoal;
import com.financehub.backend.modules.planning.domain.PlanningGoalRepository;
import com.financehub.backend.modules.governance.application.TrashService;
import com.financehub.backend.shared.api.NotFoundException;
import com.financehub.backend.shared.application.port.AuditPort;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PlanningGoalService {
  private final PlanningGoalRepository repository;
  private final AuditPort auditPort;
  private final TrashService trashService;

  public PlanningGoalService(PlanningGoalRepository repository, AuditPort auditPort, TrashService trashService) {
    this.repository = repository;
    this.auditPort = auditPort;
    this.trashService = trashService;
  }

  public List<PlanningGoal> listAll() {
    return repository.findAll().stream()
      .sorted(Comparator.comparing(PlanningGoal::getTargetDate))
      .toList();
  }

  public PlanningGoal create(PlanningGoalRequest request) {
    PlanningGoal goal = new PlanningGoal(
      UUID.randomUUID().toString(),
      request.title().trim(),
      request.targetAmount(),
      request.currentAmount(),
      request.targetDate(),
      request.notes() == null ? "" : request.notes().trim(),
      isComplete(request.currentAmount(), request.targetAmount())
    );
    repository.save(goal);
    auditPort.record("planning-goal", goal.getId(), "create", goal.getTitle() + " criada", goal.getTargetAmount());
    return goal;
  }

  public PlanningGoal update(String id, PlanningGoalRequest request) {
    PlanningGoal goal = repository.findById(id).orElseThrow(() -> new NotFoundException("Meta nao encontrada: " + id));
    goal.setTitle(request.title().trim());
    goal.setTargetAmount(request.targetAmount());
    goal.setCurrentAmount(request.currentAmount());
    goal.setTargetDate(request.targetDate());
    goal.setNotes(request.notes() == null ? "" : request.notes().trim());
    goal.setComplete(isComplete(request.currentAmount(), request.targetAmount()));
    repository.save(goal);
    auditPort.record("planning-goal", goal.getId(), "update", goal.getTitle() + " atualizada", goal.getTargetAmount());
    return goal;
  }

  private boolean isComplete(double currentAmount, double targetAmount) {
    return currentAmount >= targetAmount;
  }

  public void delete(String id) {
    PlanningGoal goal = repository.findById(id).orElseThrow(() -> new NotFoundException("Meta nao encontrada: " + id));
    trashService.moveToTrash("planning-goal", goal.getId(), goal.getTitle(), goal);
    repository.deleteById(id);
    auditPort.record("planning-goal", goal.getId(), "delete", goal.getTitle() + " movida para lixeira", goal.getTargetAmount());
  }
}
