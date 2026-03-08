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
import java.util.List;
import java.util.Locale;
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
    String normalizedBankAccountId = normalizeOptionalId(request.bankAccountId());
    LocalDate startDate = LocalDate.of(year, 1, 1);
    LocalDate endDate = LocalDate.of(year, 12, 31);

    List<Bill> matchedBills = billRepository.findAll().stream()
      .filter(bill -> isImportedCategory(bill.getCategory()))
      .filter(bill -> isDateWithinYear(bill.getDueDate(), startDate, endDate))
      .filter(bill -> matchesBankAccount(normalizedBankAccountId, bill.getBankAccountId()))
      .toList();

    List<Income> matchedIncomes = incomeRepository.findAll().stream()
      .filter(income -> isImportedCategory(income.getCategory()))
      .filter(income -> isDateWithinYear(income.getReceivedAt(), startDate, endDate))
      .filter(income -> matchesBankAccount(normalizedBankAccountId, income.getBankAccountId()))
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
        String scope = normalizedBankAccountId == null ? "todas as contas" : "conta " + normalizedBankAccountId;
        String mode = request.permanentDelete() ? "exclusao definitiva" : "envio para lixeira";
        auditPort.record(
          "statement",
          "imported-cleanup-" + year,
          request.permanentDelete() ? "purge" : "delete",
          "Limpeza de importados do ano " + year + " executada para " + scope + " em modo " + mode
            + ": " + processedBills + " saidas e " + processedIncomes + " entradas.",
          null
        );
      }
    }

    int totalMatched = matchedBills.size() + matchedIncomes.size();
    int totalProcessed = processedBills + processedIncomes;

    return new ImportedStatementYearCleanupResponse(
      year,
      startDate,
      endDate,
      normalizedBankAccountId,
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

  private boolean isImportedCategory(String category) {
    return normalizeText(category).contains(IMPORTED_STATEMENT_CATEGORY_MARKER);
  }

  private boolean isDateWithinYear(LocalDate value, LocalDate startDate, LocalDate endDate) {
    if (value == null) {
      return false;
    }
    return !value.isBefore(startDate) && !value.isAfter(endDate);
  }

  private boolean matchesBankAccount(String selectedBankAccountId, String entityBankAccountId) {
    if (selectedBankAccountId == null) {
      return true;
    }
    return selectedBankAccountId.equals(normalizeOptionalId(entityBankAccountId));
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
