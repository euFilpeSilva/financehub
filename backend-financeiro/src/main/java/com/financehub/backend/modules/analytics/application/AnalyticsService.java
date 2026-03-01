package com.financehub.backend.modules.analytics.application;

import com.financehub.backend.modules.analytics.api.dto.AccountReconciliationResponse;
import com.financehub.backend.modules.analytics.api.dto.DashboardSummaryResponse;
import com.financehub.backend.modules.bankaccounts.application.BankAccountService;
import com.financehub.backend.modules.bankaccounts.domain.BankAccount;
import com.financehub.backend.modules.bills.application.BillService;
import com.financehub.backend.modules.incomes.application.IncomeService;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {
  private static final String NUBANK_BANK_ID = "260";

  private final BillService billService;
  private final IncomeService incomeService;
  private final BankAccountService bankAccountService;

  public AnalyticsService(
    BillService billService,
    IncomeService incomeService,
    BankAccountService bankAccountService
  ) {
    this.billService = billService;
    this.incomeService = incomeService;
    this.bankAccountService = bankAccountService;
  }

  public DashboardSummaryResponse summary(LocalDate startDate, LocalDate endDate) {
    var filteredIncomes = incomeService.listAll().stream()
      .filter(item -> !item.getReceivedAt().isBefore(startDate) && !item.getReceivedAt().isAfter(endDate))
      .toList();

    var filteredBills = billService.listAll().stream()
      .filter(item -> !item.getDueDate().isBefore(startDate) && !item.getDueDate().isAfter(endDate))
      .toList();

    double incomes = filteredIncomes.stream()
      .filter(item -> !item.isInternalTransfer())
      .mapToDouble(item -> item.getAmount())
      .sum();

    double expenses = filteredBills.stream()
      .filter(item -> !item.isInternalTransfer())
      .mapToDouble(item -> item.getAmount())
      .sum();

    double paid = filteredBills.stream()
      .filter(item -> !item.isInternalTransfer() && item.isPaid())
      .mapToDouble(item -> item.getAmount())
      .sum();

    double pending = filteredBills.stream()
      .filter(item -> !item.isInternalTransfer() && !item.isPaid())
      .mapToDouble(item -> item.getAmount())
      .sum();

    return new DashboardSummaryResponse(incomes, expenses, incomes - expenses, paid, pending);
  }

  public AccountReconciliationResponse reconcileAccount(
    String bankAccountId,
    LocalDate startDate,
    LocalDate endDate,
    Double referenceBalance
  ) {
    BankAccount bankAccount = bankAccountService.getById(bankAccountId);
    List<String> bankTokens = resolveBankTokens(bankAccount);
    List<BankAccount> activeAccounts = bankAccountService.listAll().stream()
      .filter(BankAccount::isActive)
      .toList();
    boolean singleActiveAccount = activeAccounts.size() == 1;
    String singleActiveAccountId = singleActiveAccount ? activeAccounts.get(0).getId() : null;

    var filteredIncomes = incomeService.listAll().stream()
      .filter(item -> !item.getReceivedAt().isBefore(startDate) && !item.getReceivedAt().isAfter(endDate))
      .filter(item -> !item.isInternalTransfer())
      .toList();

    var filteredBills = billService.listAll().stream()
      .filter(item -> !item.getDueDate().isBefore(startDate) && !item.getDueDate().isAfter(endDate))
      .filter(item -> !item.isInternalTransfer())
      .toList();

    List<com.financehub.backend.modules.incomes.domain.Income> matchedIncomes = filteredIncomes.stream()
      .filter(item -> matchesBank(
        item.getBankAccountId(),
        item.getCategory(),
        item.getSource(),
        bankAccountId,
        bankTokens,
        singleActiveAccount,
        singleActiveAccountId
      ))
      .toList();

    List<com.financehub.backend.modules.bills.domain.Bill> matchedBills = filteredBills.stream()
      .filter(item -> matchesBank(
        item.getBankAccountId(),
        item.getCategory(),
        item.getDescription(),
        bankAccountId,
        bankTokens,
        singleActiveAccount,
        singleActiveAccountId
      ))
      .toList();

    int legacyIncomeMatches = (int) matchedIncomes.stream()
      .filter(item -> item.getBankAccountId() == null || item.getBankAccountId().isBlank())
      .count();

    int legacyExpenseMatches = (int) matchedBills.stream()
      .filter(item -> item.getBankAccountId() == null || item.getBankAccountId().isBlank())
      .count();

    double incomeTotal = matchedIncomes.stream().mapToDouble(item -> item.getAmount()).sum();
    double expenseTotal = matchedBills.stream().mapToDouble(item -> item.getAmount()).sum();
    double calculatedBalance = incomeTotal - expenseTotal;
    Double difference = referenceBalance == null ? null : calculatedBalance - referenceBalance;

    return new AccountReconciliationResponse(
      bankAccountId,
      bankAccount.getLabel(),
      startDate,
      endDate,
      incomeTotal,
      expenseTotal,
      calculatedBalance,
      referenceBalance,
      difference,
      matchedIncomes.size(),
      matchedBills.size(),
      legacyIncomeMatches,
      legacyExpenseMatches
    );
  }

  private boolean matchesBank(
    String transactionBankAccountId,
    String transactionCategory,
    String text,
    String requestedBankAccountId,
    List<String> bankTokens,
    boolean singleActiveAccount,
    String singleActiveAccountId
  ) {
    if (transactionBankAccountId != null && !transactionBankAccountId.isBlank()) {
      return transactionBankAccountId.equals(requestedBankAccountId);
    }

    if (!isImportedStatementCategory(transactionCategory)) {
      return false;
    }

    if (singleActiveAccount && requestedBankAccountId.equals(singleActiveAccountId)) {
      return true;
    }

    String normalizedText = normalizeText(text);
    String paddedText = " " + normalizedText + " ";
    for (String token : bankTokens) {
      String normalizedToken = normalizeText(token);
      if (!normalizedToken.isBlank() && paddedText.contains(" " + normalizedToken + " ")) {
        return true;
      }
    }
    return false;
  }

  private List<String> resolveBankTokens(BankAccount bankAccount) {
    LinkedHashSet<String> tokens = new LinkedHashSet<>();
    String normalizedLabel = normalizeText(bankAccount.getLabel());
    if (!normalizedLabel.isBlank()) {
      tokens.add(normalizedLabel);
    }
    for (String part : normalizedLabel.split(" ")) {
      if (part.length() >= 4) {
        tokens.add(part);
      }
    }

    String normalizedBankId = bankAccount.getBankId() == null
      ? ""
      : bankAccount.getBankId().replaceAll("\\D", "");

    if (normalizedLabel.contains("NUBANK") || NUBANK_BANK_ID.equals(normalizedBankId)) {
      tokens.add("NUBANK");
      tokens.add("NU PAGAMENTOS");
    }

    return new ArrayList<>(tokens);
  }

  private boolean isImportedStatementCategory(String category) {
    return normalizeText(category).contains("EXTRATO IMPORTADO");
  }

  private String normalizeText(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    return Normalizer.normalize(value, Normalizer.Form.NFD)
      .replaceAll("\\p{M}", "")
      .toUpperCase()
      .replaceAll("[^A-Z0-9 ]", " ")
      .replaceAll("\\s{2,}", " ")
      .trim();
  }
}
