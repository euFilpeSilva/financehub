package com.financehub.backend.modules.bills.application;

import com.financehub.backend.modules.bankaccounts.application.BankAccountService;
import com.financehub.backend.modules.bankaccounts.domain.BankAccount;
import com.financehub.backend.modules.bills.api.dto.BillRequest;
import com.financehub.backend.modules.bills.domain.Bill;
import com.financehub.backend.modules.bills.domain.BillRepository;
import com.financehub.backend.modules.governance.application.TrashService;
import com.financehub.backend.shared.api.NotFoundException;
import com.financehub.backend.shared.application.port.AuditPort;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class BillService {
  private final BillRepository repository;
  private final BankAccountService bankAccountService;
  private final AuditPort auditPort;
  private final TrashService trashService;

  public BillService(
    BillRepository repository,
    BankAccountService bankAccountService,
    AuditPort auditPort,
    TrashService trashService
  ) {
    this.repository = repository;
    this.bankAccountService = bankAccountService;
    this.auditPort = auditPort;
    this.trashService = trashService;
  }

  public List<Bill> listAll() {
    return repository.findAll().stream()
      .sorted(Comparator.comparing(Bill::getDueDate))
      .toList();
  }

  public List<Bill> listFiltered(
    String query,
    String category,
    String bankAccountId,
    String status,
    String recurring,
    LocalDate startDate,
    LocalDate endDate
  ) {
    String normalizedQuery = normalizeText(query);
    String normalizedCategory = normalizeOption(category);
    String normalizedBankAccountId = normalizeIdOption(bankAccountId);
    String normalizedStatus = normalizeOption(status);
    String normalizedRecurring = normalizeOption(recurring);

    Optional<BankAccount> selectedBank = resolveSelectedBank(normalizedBankAccountId);
    Set<String> selectedBankTokens = selectedBank
      .map(bank -> resolveBankTokens(bank.getLabel(), bank.getBankId()))
      .orElseGet(Set::of);

    return repository.findAll().stream()
      .filter(bill -> matchesQuery(normalizedQuery, bill.getDescription(), bill.getAmount()))
      .filter(bill -> matchesCategory(normalizedCategory, bill.getCategory()))
      .filter(bill -> matchesStatus(normalizedStatus, bill.isPaid()))
      .filter(bill -> matchesRecurring(normalizedRecurring, bill.isRecurring()))
      .filter(bill -> matchesDateRange(bill.getDueDate(), startDate, endDate))
      .filter(bill -> matchesBankFilter(normalizedBankAccountId, selectedBank.isPresent(), selectedBankTokens, bill))
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

  private boolean matchesQuery(String normalizedQuery, String description, double amount) {
    if (normalizedQuery.isBlank()) {
      return true;
    }
    String normalizedAmount = normalizeText(formatAmountForQuery(amount));
    return normalizeText(description).contains(normalizedQuery)
      || normalizedAmount.contains(normalizedQuery);
  }

  private String formatAmountForQuery(double amount) {
    return String.format(Locale.US, "%.2f", amount);
  }

  private boolean matchesCategory(String normalizedCategory, String category) {
    if (normalizedCategory.isBlank() || "ALL".equals(normalizedCategory)) {
      return true;
    }
    return normalizeText(category).equals(normalizedCategory);
  }

  private boolean matchesStatus(String normalizedStatus, boolean paid) {
    if (normalizedStatus.isBlank() || "ALL".equals(normalizedStatus)) {
      return true;
    }
    if ("PAID".equals(normalizedStatus)) {
      return paid;
    }
    if ("PENDING".equals(normalizedStatus)) {
      return !paid;
    }
    return true;
  }

  private boolean matchesRecurring(String normalizedRecurring, boolean recurring) {
    if (normalizedRecurring.isBlank() || "ALL".equals(normalizedRecurring)) {
      return true;
    }
    if ("YES".equals(normalizedRecurring)) {
      return recurring;
    }
    if ("NO".equals(normalizedRecurring)) {
      return !recurring;
    }
    return true;
  }

  private boolean matchesDateRange(LocalDate dueDate, LocalDate startDate, LocalDate endDate) {
    if (startDate != null && dueDate.isBefore(startDate)) {
      return false;
    }
    if (endDate != null && dueDate.isAfter(endDate)) {
      return false;
    }
    return true;
  }

  private Optional<BankAccount> resolveSelectedBank(String normalizedBankAccountId) {
    if (normalizedBankAccountId.isBlank() || "ALL".equals(normalizedBankAccountId)) {
      return Optional.empty();
    }
    return bankAccountService.listAll().stream()
      .filter(bank -> normalizedBankAccountId.equals(bank.getId()))
      .findFirst();
  }

  private boolean matchesBankFilter(
    String normalizedBankAccountId,
    boolean hasSelectedBank,
    Set<String> selectedBankTokens,
    Bill bill
  ) {
    if (normalizedBankAccountId.isBlank() || "ALL".equals(normalizedBankAccountId)) {
      return true;
    }

    if (normalizedBankAccountId.equals(normalizeOptionalId(bill.getBankAccountId()))) {
      return true;
    }

    if (bill.getBankAccountId() != null && !bill.getBankAccountId().isBlank()) {
      return false;
    }

    if (!hasSelectedBank || selectedBankTokens.isEmpty()) {
      return false;
    }

    String normalizedDescription = normalizeText(bill.getDescription());
    return selectedBankTokens.stream().anyMatch(normalizedDescription::contains);
  }

  private Set<String> resolveBankTokens(String label, String bankId) {
    String normalizedLabel = normalizeText(label);
    Set<String> tokens = new HashSet<>();

    for (String part : normalizedLabel.split(" ")) {
      if (part.length() >= 3) {
        tokens.add(part);
      }
    }

    String digits = bankId == null ? "" : bankId.replaceAll("\\D", "");
    if (!digits.isBlank()) {
      tokens.add(digits);
    }

    if (normalizedLabel.contains("NUBANK") || "260".equals(digits)) {
      tokens.add("NUBANK");
      tokens.add("NU PAGAMENTOS");
    }

    return tokens;
  }

  private String normalizeOption(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    return value.trim().toUpperCase(Locale.ROOT);
  }

  private String normalizeIdOption(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    return value.trim();
  }

  private String normalizeText(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    return Normalizer.normalize(value, Normalizer.Form.NFD)
      .replaceAll("\\p{M}", "")
      .toUpperCase(Locale.ROOT)
      .replaceAll("[^A-Z0-9 ]", " ")
      .replaceAll("\\s{2,}", " ")
      .trim();
  }
}
