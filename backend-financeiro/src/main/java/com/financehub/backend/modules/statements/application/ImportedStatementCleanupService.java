package com.financehub.backend.modules.statements.application;

import com.financehub.backend.modules.bills.domain.Bill;
import com.financehub.backend.modules.bills.domain.BillRepository;
import com.financehub.backend.modules.governance.application.TrashService;
import com.financehub.backend.modules.incomes.domain.Income;
import com.financehub.backend.modules.incomes.domain.IncomeRepository;
import com.financehub.backend.modules.statements.api.dto.ImportedStatementYearCleanupRequest;
import com.financehub.backend.modules.statements.api.dto.ImportedStatementYearCleanupResponse;
import com.financehub.backend.shared.application.port.AuditPort;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImportedStatementCleanupService {
  private static final String IMPORTED_STATEMENT_CATEGORY_MARKER = "EXTRATO IMPORTADO";

  private final BillRepository billRepository;
  private final IncomeRepository incomeRepository;
  private final TrashService trashService;
  private final AuditPort auditPort;

  public ImportedStatementCleanupService(
    BillRepository billRepository,
    IncomeRepository incomeRepository,
    TrashService trashService,
    AuditPort auditPort
  ) {
    this.billRepository = billRepository;
    this.incomeRepository = incomeRepository;
    this.trashService = trashService;
    this.auditPort = auditPort;
  }

  @Transactional
  public ImportedStatementYearCleanupResponse cleanupByYear(ImportedStatementYearCleanupRequest request) {
    int year = validateYear(request.year());
    Integer month = validateMonth(request.month());
    Set<String> normalizedBankAccountIds = normalizeOptionalIds(request.bankAccountIds());
    LocalDate startDate = resolveStartDate(year, month);
    LocalDate endDate = resolveEndDate(year, month);

    List<Bill> matchedBills = billRepository.findAll().stream()
      .filter(bill -> isImportedCategory(bill.getCategory()))
      .filter(bill -> isDateWithinRange(bill.getDueDate(), startDate, endDate))
      .filter(bill -> matchesBankAccount(normalizedBankAccountIds, bill.getBankAccountId()))
      .toList();

    List<Income> matchedIncomes = incomeRepository.findAll().stream()
      .filter(income -> isImportedCategory(income.getCategory()))
      .filter(income -> isDateWithinRange(income.getReceivedAt(), startDate, endDate))
      .filter(income -> matchesBankAccount(normalizedBankAccountIds, income.getBankAccountId()))
      .toList();

    int processedBills = 0;
    int processedIncomes = 0;
    int movedToTrash = 0;
    int deletedPermanently = 0;

    if (!request.dryRun()) {
      for (Bill bill : matchedBills) {
        if (request.permanentDelete()) {
          billRepository.deleteById(bill.getId());
          deletedPermanently += 1;
        } else {
          trashService.moveToTrash("bill", bill.getId(), bill.getDescription(), bill);
          billRepository.deleteById(bill.getId());
          movedToTrash += 1;
        }
        processedBills += 1;
      }

      for (Income income : matchedIncomes) {
        if (request.permanentDelete()) {
          incomeRepository.deleteById(income.getId());
          deletedPermanently += 1;
        } else {
          trashService.moveToTrash("income", income.getId(), income.getSource(), income);
          incomeRepository.deleteById(income.getId());
          movedToTrash += 1;
        }
        processedIncomes += 1;
      }

      int totalProcessed = processedBills + processedIncomes;
      if (totalProcessed > 0) {
        String scope = normalizedBankAccountIds.isEmpty()
          ? "todas as contas"
          : normalizedBankAccountIds.size() == 1
            ? "conta " + normalizedBankAccountIds.iterator().next()
            : normalizedBankAccountIds.size() + " contas selecionadas";
        String mode = request.permanentDelete() ? "exclusao definitiva" : "envio para lixeira";
        auditPort.record(
          "statement",
          "imported-cleanup-" + year,
          request.permanentDelete() ? "purge" : "delete",
          "Limpeza de importados do ano " + year + resolveMonthAuditSuffix(month) + " executada para " + scope + " em modo " + mode
            + ": " + processedBills + " saidas e " + processedIncomes + " entradas.",
          null
        );
      }
    }

    int totalMatched = matchedBills.size() + matchedIncomes.size();
    int totalProcessed = processedBills + processedIncomes;

    return new ImportedStatementYearCleanupResponse(
      year,
      month,
      startDate,
      endDate,
      List.copyOf(normalizedBankAccountIds),
      request.dryRun(),
      request.permanentDelete(),
      matchedBills.size(),
      matchedIncomes.size(),
      processedBills,
      processedIncomes,
      movedToTrash,
      deletedPermanently,
      totalMatched,
      totalProcessed
    );
  }

  private int validateYear(int year) {
    if (year < 2000 || year > 2100) {
      throw new IllegalArgumentException("Ano invalido para limpeza. Use um ano entre 2000 e 2100.");
    }
    return year;
  }

  private Integer validateMonth(Integer month) {
    if (month == null) {
      return null;
    }
    if (month < 1 || month > 12) {
      throw new IllegalArgumentException("Mes invalido para limpeza. Use um valor entre 1 e 12.");
    }
    return month;
  }

  private LocalDate resolveStartDate(int year, Integer month) {
    if (month == null) {
      return LocalDate.of(year, 1, 1);
    }
    return LocalDate.of(year, month, 1);
  }

  private LocalDate resolveEndDate(int year, Integer month) {
    if (month == null) {
      return LocalDate.of(year, 12, 31);
    }
    YearMonth yearMonth = YearMonth.of(year, month);
    return yearMonth.atEndOfMonth();
  }

  private String resolveMonthAuditSuffix(Integer month) {
    if (month == null) {
      return "";
    }
    return ", mes " + String.format(Locale.ROOT, "%02d", month);
  }

  private boolean isImportedCategory(String category) {
    return normalizeText(category).contains(IMPORTED_STATEMENT_CATEGORY_MARKER);
  }

  private boolean isDateWithinRange(LocalDate value, LocalDate startDate, LocalDate endDate) {
    if (value == null) {
      return false;
    }
    return !value.isBefore(startDate) && !value.isAfter(endDate);
  }

  private boolean matchesBankAccount(Set<String> selectedBankAccountIds, String entityBankAccountId) {
    if (selectedBankAccountIds.isEmpty()) {
      return true;
    }
    String normalizedEntityBankAccountId = normalizeOptionalId(entityBankAccountId);
    return normalizedEntityBankAccountId != null && selectedBankAccountIds.contains(normalizedEntityBankAccountId);
  }

  private Set<String> normalizeOptionalIds(List<String> values) {
    if (values == null || values.isEmpty()) {
      return Set.of();
    }
    return values.stream()
      .map(this::normalizeOptionalId)
      .filter(item -> item != null && !item.isBlank())
      .collect(Collectors.toSet());
  }

  private String normalizeOptionalId(String value) {
    if (value == null || value.isBlank()) {
      return null;
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
