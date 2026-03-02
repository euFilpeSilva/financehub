package com.financehub.backend.modules.bills.application;

import com.financehub.backend.modules.bills.api.dto.BillRequest;
import com.financehub.backend.modules.bills.domain.Bill;
import com.financehub.backend.modules.bills.domain.BillRepository;
import com.financehub.backend.modules.governance.application.TrashService;
import com.financehub.backend.shared.api.NotFoundException;
import com.financehub.backend.shared.application.port.AuditPort;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class BillService {
  private final BillRepository repository;
  private final AuditPort auditPort;
  private final TrashService trashService;

  public BillService(BillRepository repository, AuditPort auditPort, TrashService trashService) {
    this.repository = repository;
    this.auditPort = auditPort;
    this.trashService = trashService;
  }

  public List<Bill> listAll() {
    return repository.findAll().stream()
      .sorted(Comparator.comparing(Bill::getDueDate))
      .toList();
  }

  public Bill create(BillRequest request) {
    Bill bill = new Bill(
      UUID.randomUUID().toString(),
      request.description().trim(),
      request.category(),
      request.amount(),
      request.dueDate(),
      request.recurring(),
      request.paid(),
      request.internalTransfer(),
      normalizeOptionalId(request.bankAccountId())
    );
    repository.save(bill);
    auditPort.record("bill", bill.getId(), "create", bill.getDescription() + " criado", bill.getAmount());
    return bill;
  }

  public Bill update(String id, BillRequest request) {
    Bill bill = repository.findById(id).orElseThrow(() -> new NotFoundException("Conta nao encontrada: " + id));
    bill.setDescription(request.description().trim());
    bill.setCategory(request.category());
    bill.setAmount(request.amount());
    bill.setDueDate(request.dueDate());
    bill.setRecurring(request.recurring());
    bill.setPaid(request.paid());
    bill.setInternalTransfer(request.internalTransfer());
    bill.setBankAccountId(normalizeOptionalId(request.bankAccountId()));
    repository.save(bill);
    auditPort.record("bill", bill.getId(), "update", bill.getDescription() + " atualizada", bill.getAmount());
    return bill;
  }

  public void delete(String id) {
    Bill bill = repository.findById(id).orElseThrow(() -> new NotFoundException("Conta nao encontrada: " + id));
    trashService.moveToTrash("bill", bill.getId(), bill.getDescription(), bill);
    repository.deleteById(id);
    auditPort.record("bill", bill.getId(), "delete", bill.getDescription() + " movida para lixeira", bill.getAmount());
  }

  public List<Bill> createRecurringForMonth(String month) {
    YearMonth targetMonth;
    try {
      targetMonth = YearMonth.parse(month);
    } catch (Exception ex) {
      throw new IllegalArgumentException("Mes invalido. Use o formato yyyy-MM.");
    }

    List<Bill> current = repository.findAll();
    List<Bill> recurringTemplates = current.stream()
      .filter(Bill::isRecurring)
      .toList();

    List<Bill> created = recurringTemplates.stream()
      .filter(template -> !existsInMonth(current, targetMonth, template))
      .map(template -> new Bill(
        UUID.randomUUID().toString(),
        template.getDescription(),
        template.getCategory(),
        template.getAmount(),
        createSafeDate(targetMonth, template.getDueDate().getDayOfMonth()),
        true,
        false,
        false,
        null
      ))
      .map(repository::save)
      .toList();

    if (!created.isEmpty()) {
      auditPort.record("bill", targetMonth.toString(), "create", "Contas recorrentes geradas para " + targetMonth, null);
    }

    return created;
  }

  private boolean existsInMonth(List<Bill> bills, YearMonth month, Bill template) {
    return bills.stream().anyMatch(item ->
      item.getDueDate().getYear() == month.getYear()
        && item.getDueDate().getMonthValue() == month.getMonthValue()
        && item.getDescription().equalsIgnoreCase(template.getDescription())
        && item.getCategory().equals(template.getCategory())
    );
  }

  private java.time.LocalDate createSafeDate(YearMonth month, int day) {
    int safeDay = Math.max(1, Math.min(day, month.lengthOfMonth()));
    return month.atDay(safeDay);
  }

  private String normalizeOptionalId(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
