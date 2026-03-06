package com.financehub.backend.modules.incomes.application;

import com.financehub.backend.modules.bankaccounts.application.BankAccountService;
import com.financehub.backend.modules.bankaccounts.domain.BankAccount;
import com.financehub.backend.modules.governance.application.TrashService;
import com.financehub.backend.modules.incomes.api.dto.IncomeRequest;
import com.financehub.backend.modules.incomes.domain.Income;
import com.financehub.backend.modules.incomes.domain.IncomeRepository;
import com.financehub.backend.shared.api.NotFoundException;
import com.financehub.backend.shared.application.port.AuditPort;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class IncomeService {
  private final IncomeRepository repository;
  private final BankAccountService bankAccountService;
  private final AuditPort auditPort;
  private final TrashService trashService;

  public IncomeService(
    IncomeRepository repository,
    BankAccountService bankAccountService,
    AuditPort auditPort,
    TrashService trashService
  ) {
    this.repository = repository;
    this.bankAccountService = bankAccountService;
    this.auditPort = auditPort;
    this.trashService = trashService;
  }

  public List<Income> listAll() {
    return repository.findAll().stream()
      .sorted(Comparator.comparing(Income::getReceivedAt))
      .toList();
  }

  public List<Income> listFiltered(
    String query,
    String category,
    String bankAccountId,
    String recurring,
    LocalDate startDate,
    LocalDate endDate,
    Double minAmount,
    Double maxAmount
  ) {
    String normalizedQuery = normalizeText(query);
    String normalizedCategory = normalizeOption(category);
    String normalizedBankAccountId = normalizeOption(bankAccountId);
    String normalizedRecurring = normalizeOption(recurring);

    Optional<BankAccount> selectedBank = resolveSelectedBank(normalizedBankAccountId);
    Set<String> selectedBankTokens = selectedBank
      .map(bank -> resolveBankTokens(bank.getLabel(), bank.getBankId()))
      .orElseGet(Set::of);

    return repository.findAll().stream()
      .filter(income -> matchesQuery(normalizedQuery, income.getSource()))
      .filter(income -> matchesCategory(normalizedCategory, income.getCategory()))
      .filter(income -> matchesRecurring(normalizedRecurring, income.isRecurring()))
      .filter(income -> matchesDateRange(income.getReceivedAt(), startDate, endDate))
      .filter(income -> matchesAmountRange(income.getAmount(), minAmount, maxAmount))
      .filter(income -> matchesBankFilter(normalizedBankAccountId, selectedBank.isPresent(), selectedBankTokens, income))
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

  private boolean matchesQuery(String normalizedQuery, String source) {
    if (normalizedQuery.isBlank()) {
      return true;
    }
    return normalizeText(source).contains(normalizedQuery);
  }

  private boolean matchesCategory(String normalizedCategory, String category) {
    if (normalizedCategory.isBlank() || "ALL".equals(normalizedCategory)) {
      return true;
    }
    return normalizeText(category).equals(normalizedCategory);
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

  private boolean matchesDateRange(LocalDate receivedAt, LocalDate startDate, LocalDate endDate) {
    if (startDate != null && receivedAt.isBefore(startDate)) {
      return false;
    }
    if (endDate != null && receivedAt.isAfter(endDate)) {
      return false;
    }
    return true;
  }

  private boolean matchesAmountRange(double amount, Double minAmount, Double maxAmount) {
    if (minAmount != null && amount < minAmount) {
      return false;
    }
    if (maxAmount != null && amount > maxAmount) {
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
    Income income
  ) {
    if (normalizedBankAccountId.isBlank() || "ALL".equals(normalizedBankAccountId)) {
      return true;
    }

    if (normalizedBankAccountId.equals(normalizeOptionalId(income.getBankAccountId()))) {
      return true;
    }

    if (income.getBankAccountId() != null && !income.getBankAccountId().isBlank()) {
      return false;
    }

    if (!hasSelectedBank || selectedBankTokens.isEmpty()) {
      return false;
    }

    String normalizedSource = normalizeText(income.getSource());
    return selectedBankTokens.stream().anyMatch(normalizedSource::contains);
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
