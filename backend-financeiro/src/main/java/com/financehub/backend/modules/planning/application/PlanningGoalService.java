package com.financehub.backend.modules.planning.application;

import com.financehub.backend.modules.governance.application.TrashService;
import com.financehub.backend.modules.planning.api.dto.PlanningGoalRequest;
import com.financehub.backend.modules.planning.domain.PlanningGoal;
import com.financehub.backend.modules.planning.domain.PlanningGoalRepository;
import com.financehub.backend.shared.api.NotFoundException;
import com.financehub.backend.shared.application.port.AuditPort;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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

  public List<PlanningGoal> listFiltered(String query, String status, LocalDate startDate, LocalDate endDate) {
    String normalizedQuery = normalize(query);
    String normalizedStatus = normalize(status);

    return repository.findAll().stream()
      .filter(goal -> matchesQuery(normalizedQuery, goal.getTitle()))
      .filter(goal -> matchesStatus(normalizedStatus, goal.isComplete()))
      .filter(goal -> matchesDateRange(goal.getTargetDate(), startDate, endDate))
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

  private boolean matchesQuery(String normalizedQuery, String title) {
    if (normalizedQuery.isBlank()) {
      return true;
    }
    return normalize(title).contains(normalizedQuery);
  }

  private boolean matchesStatus(String normalizedStatus, boolean complete) {
    if (normalizedStatus.isBlank() || "ALL".equals(normalizedStatus)) {
      return true;
    }
    if ("COMPLETE".equals(normalizedStatus)) {
      return complete;
    }
    if ("IN_PROGRESS".equals(normalizedStatus)) {
      return !complete;
    }
    return true;
  }

  private boolean matchesDateRange(LocalDate targetDate, LocalDate startDate, LocalDate endDate) {
    if (startDate != null && targetDate.isBefore(startDate)) {
      return false;
    }
    if (endDate != null && targetDate.isAfter(endDate)) {
      return false;
    }
    return true;
  }

  private String normalize(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    return value.trim().toUpperCase(Locale.ROOT);
  }
}
