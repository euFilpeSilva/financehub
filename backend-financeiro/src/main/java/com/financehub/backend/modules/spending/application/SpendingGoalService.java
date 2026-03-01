package com.financehub.backend.modules.spending.application;

import com.financehub.backend.modules.spending.api.dto.SpendingGoalRequest;
import com.financehub.backend.modules.spending.domain.SpendingGoal;
import com.financehub.backend.modules.spending.domain.SpendingGoalRepository;
import com.financehub.backend.modules.governance.application.TrashService;
import com.financehub.backend.shared.api.NotFoundException;
import com.financehub.backend.shared.application.port.AuditPort;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SpendingGoalService {
  private final SpendingGoalRepository repository;
  private final AuditPort auditPort;
  private final TrashService trashService;

  public SpendingGoalService(SpendingGoalRepository repository, AuditPort auditPort, TrashService trashService) {
    this.repository = repository;
    this.auditPort = auditPort;
    this.trashService = trashService;
  }

  public List<SpendingGoal> listAll() {
    return repository.findAll().stream()
      .sorted(Comparator.comparing(SpendingGoal::getTitle))
      .toList();
  }

  public SpendingGoal create(SpendingGoalRequest request) {
    validateSchedule(request.schedule(), request.startMonth(), request.startDate(), request.endDate());
    SpendingGoal goal = new SpendingGoal(
      UUID.randomUUID().toString(),
      request.title().trim(),
      request.limitAmount(),
      request.category(),
      request.schedule(),
      request.startMonth(),
      request.startDate(),
      request.endDate(),
      request.active()
    );
    repository.save(goal);
    auditPort.record("spending-goal", goal.getId(), "create", goal.getTitle() + " criado", goal.getLimitAmount());
    return goal;
  }

  public SpendingGoal update(String id, SpendingGoalRequest request) {
    SpendingGoal goal = repository.findById(id).orElseThrow(() -> new NotFoundException("Meta de gasto nao encontrada: " + id));
    validateSchedule(request.schedule(), request.startMonth(), request.startDate(), request.endDate());
    goal.setTitle(request.title().trim());
    goal.setLimitAmount(request.limitAmount());
    goal.setCategory(request.category());
    goal.setSchedule(request.schedule());
    goal.setStartMonth(request.startMonth());
    goal.setStartDate(request.startDate());
    goal.setEndDate(request.endDate());
    goal.setActive(request.active());
    repository.save(goal);
    auditPort.record("spending-goal", goal.getId(), "update", goal.getTitle() + " atualizado", goal.getLimitAmount());
    return goal;
  }

  public void delete(String id) {
    SpendingGoal goal = repository.findById(id).orElseThrow(() -> new NotFoundException("Meta de gasto nao encontrada: " + id));
    trashService.moveToTrash("spending-goal", goal.getId(), goal.getTitle(), goal);
    repository.deleteById(id);
    auditPort.record("spending-goal", goal.getId(), "delete", goal.getTitle() + " movido para lixeira", goal.getLimitAmount());
  }

  private void validateSchedule(String schedule, String startMonth, java.time.LocalDate startDate, java.time.LocalDate endDate) {
    if (!"monthly".equals(schedule) && !"custom".equals(schedule)) {
      throw new IllegalArgumentException("Schedule invalido. Use 'monthly' ou 'custom'.");
    }
    if ("monthly".equals(schedule) && (startMonth == null || startMonth.isBlank())) {
      throw new IllegalArgumentException("startMonth e obrigatorio para schedule monthly.");
    }
    if ("custom".equals(schedule) && (startDate == null || endDate == null)) {
      throw new IllegalArgumentException("startDate e endDate sao obrigatorios para schedule custom.");
    }
    if ("custom".equals(schedule) && endDate != null && startDate != null && endDate.isBefore(startDate)) {
      throw new IllegalArgumentException("endDate nao pode ser menor que startDate.");
    }
  }
}
