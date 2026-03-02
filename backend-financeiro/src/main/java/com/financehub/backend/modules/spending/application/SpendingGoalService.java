package com.financehub.backend.modules.spending.application;

import com.financehub.backend.modules.spending.api.dto.SpendingGoalRequest;
import com.financehub.backend.modules.spending.api.dto.SpendingGoalResponse;
import com.financehub.backend.modules.spending.api.dto.SpendingGoalStatusResponse;
import com.financehub.backend.modules.bills.domain.Bill;
import com.financehub.backend.modules.bills.domain.BillRepository;
import com.financehub.backend.modules.spending.domain.SpendingGoal;
import com.financehub.backend.modules.spending.domain.SpendingGoalRepository;
import com.financehub.backend.modules.governance.application.TrashService;
import com.financehub.backend.shared.api.NotFoundException;
import com.financehub.backend.shared.application.port.AuditPort;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SpendingGoalService {
  private final SpendingGoalRepository repository;
  private final BillRepository billRepository;
  private final AuditPort auditPort;
  private final TrashService trashService;

  public SpendingGoalService(
    SpendingGoalRepository repository,
    BillRepository billRepository,
    AuditPort auditPort,
    TrashService trashService
  ) {
    this.repository = repository;
    this.billRepository = billRepository;
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

  public List<SpendingGoalStatusResponse> listStatusesByMonth(String selectedMonthRaw) {
    YearMonth selectedMonth = parseYearMonth(selectedMonthRaw, "selectedMonth");
    List<Bill> bills = billRepository.findAll();
    return listAll().stream()
      .map((goal) -> toStatusResponse(goal, selectedMonth, bills))
      .toList();
  }

  private SpendingGoalStatusResponse toStatusResponse(SpendingGoal goal, YearMonth selectedMonth, List<Bill> bills) {
    GoalPeriod period = resolveGoalPeriod(goal, selectedMonth);
    double spentAmount = bills.stream()
      .filter((item) -> !item.isInternalTransfer())
      .filter((item) -> !item.getDueDate().isBefore(period.start()) && !item.getDueDate().isAfter(period.end()))
      .filter((item) -> "ALL".equals(goal.getCategory()) || goal.getCategory().equals(item.getCategory()))
      .mapToDouble((item) -> item.getAmount())
      .sum();
    double remainingAmount = goal.getLimitAmount() - spentAmount;
    double usagePercent = goal.getLimitAmount() != 0 ? (spentAmount / goal.getLimitAmount()) * 100 : 0;

    SpendingGoalResponse goalResponse = new SpendingGoalResponse(
      goal.getId(),
      goal.getTitle(),
      goal.getLimitAmount(),
      goal.getCategory(),
      goal.getSchedule(),
      goal.getStartMonth(),
      goal.getStartDate(),
      goal.getEndDate(),
      goal.isActive()
    );

    return new SpendingGoalStatusResponse(
      goalResponse,
      spentAmount,
      remainingAmount,
      usagePercent,
      spentAmount <= goal.getLimitAmount(),
      period.label()
    );
  }

  private GoalPeriod resolveGoalPeriod(SpendingGoal goal, YearMonth selectedMonth) {
    if ("custom".equals(goal.getSchedule())) {
      LocalDate start = goal.getStartDate() != null ? goal.getStartDate() : selectedMonth.atDay(1);
      LocalDate end = goal.getEndDate() != null ? goal.getEndDate() : selectedMonth.atEndOfMonth();
      return new GoalPeriod(start, end, start + " a " + end);
    }

    YearMonth startMonth = goal.getStartMonth() != null && !goal.getStartMonth().isBlank()
      ? parseYearMonth(goal.getStartMonth(), "startMonth")
      : selectedMonth;
    YearMonth month = selectedMonth.isBefore(startMonth) ? startMonth : selectedMonth;
    return new GoalPeriod(month.atDay(1), month.atEndOfMonth(), "Mensal (" + month + ")");
  }

  private YearMonth parseYearMonth(String value, String fieldName) {
    try {
      return YearMonth.parse(value);
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException(fieldName + " invalido. Use o formato yyyy-MM.");
    }
  }

  private record GoalPeriod(LocalDate start, LocalDate end, String label) {
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
