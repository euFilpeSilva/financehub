package com.financehub.backend.modules.incomes.application;

import com.financehub.backend.modules.incomes.api.dto.IncomeRequest;
import com.financehub.backend.modules.incomes.domain.Income;
import com.financehub.backend.modules.incomes.domain.IncomeRepository;
import com.financehub.backend.modules.governance.application.TrashService;
import com.financehub.backend.shared.api.NotFoundException;
import com.financehub.backend.shared.application.port.AuditPort;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class IncomeService {
  private final IncomeRepository repository;
  private final AuditPort auditPort;
  private final TrashService trashService;

  public IncomeService(IncomeRepository repository, AuditPort auditPort, TrashService trashService) {
    this.repository = repository;
    this.auditPort = auditPort;
    this.trashService = trashService;
  }

  public List<Income> listAll() {
    return repository.findAll().stream()
      .sorted(Comparator.comparing(Income::getReceivedAt))
      .toList();
  }

  public Income create(IncomeRequest request) {
    Income income = new Income(
      UUID.randomUUID().toString(),
      request.source().trim(),
      request.category(),
      request.amount(),
      request.receivedAt(),
      request.recurring(),
      request.internalTransfer(),
      normalizeOptionalId(request.bankAccountId())
    );
    repository.save(income);
    auditPort.record("income", income.getId(), "create", income.getSource() + " criado", income.getAmount());
    return income;
  }

  public Income update(String id, IncomeRequest request) {
    Income income = repository.findById(id).orElseThrow(() -> new NotFoundException("Entrada nao encontrada: " + id));
    income.setSource(request.source().trim());
    income.setCategory(request.category());
    income.setAmount(request.amount());
    income.setReceivedAt(request.receivedAt());
    income.setRecurring(request.recurring());
    income.setInternalTransfer(request.internalTransfer());
    income.setBankAccountId(normalizeOptionalId(request.bankAccountId()));
    repository.save(income);
    auditPort.record("income", income.getId(), "update", income.getSource() + " atualizado", income.getAmount());
    return income;
  }

  public void delete(String id) {
    Income income = repository.findById(id).orElseThrow(() -> new NotFoundException("Entrada nao encontrada: " + id));
    trashService.moveToTrash("income", income.getId(), income.getSource(), income);
    repository.deleteById(id);
    auditPort.record("income", income.getId(), "delete", income.getSource() + " movido para lixeira", income.getAmount());
  }

  private String normalizeOptionalId(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
