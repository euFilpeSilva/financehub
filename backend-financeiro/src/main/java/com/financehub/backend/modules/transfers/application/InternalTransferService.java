package com.financehub.backend.modules.transfers.application;

import com.financehub.backend.modules.bankaccounts.application.BankAccountService;
import com.financehub.backend.modules.bankaccounts.domain.BankAccount;
import com.financehub.backend.modules.bills.domain.Bill;
import com.financehub.backend.modules.bills.domain.BillRepository;
import com.financehub.backend.modules.governance.application.TrashService;
import com.financehub.backend.modules.incomes.domain.Income;
import com.financehub.backend.modules.incomes.domain.IncomeRepository;
import com.financehub.backend.modules.transfers.api.dto.ImportedDuplicateCleanupResponse;
import com.financehub.backend.modules.transfers.api.dto.InternalTransferSuggestionResponse;
import com.financehub.backend.modules.transfers.api.dto.InternalTransferReclassificationResponse;
import com.financehub.backend.shared.api.NotFoundException;
import com.financehub.backend.shared.application.port.AuditPort;
import java.text.Normalizer;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InternalTransferService {
  private static final Set<String> LOAN_INCOME_MARKERS = Set.of(
    "EMPRESTIMO",
    "CREDITO REFERENTE A EMPRESTIMO",
    "CREDITO EMPRESTIMO",
    "CREDITO CONTRATADO"
  );
  private static final Set<String> LEGACY_BROKER_MARKERS = Set.of(
    "EASYNVEST",
    "NUINVEST",
    "NUBANK CORRETORA"
  );
  private static final Set<String> PICPAY_MARKERS = Set.of(
    "PICPAY",
    "PIC PAY"
  );
  private static final Set<String> MERCADO_PAGO_MARKERS = Set.of(
    "MERCADO PAGO",
    "MERCADOPAGO"
  );
  private static final Set<String> SAME_OWNERSHIP_MARKERS = Set.of(
    "MESMA TITULARIDADE",
    "MESMA TITULAR",
    "PROPRIA TITULARIDADE",
    "CONTA PROPRIA",
    "ENTRE CONTAS"
  );
  private static final Set<String> INVESTMENT_PURCHASE_MARKERS = Set.of(
    "COMPRA DE BDR",
    "COMPRA BDR"
  );
  private static final String IMPORTED_STATEMENT_CATEGORY = "EXTRATO IMPORTADO";
  private static final Set<String> GENERIC_DUPLICATE_MEMOS = Set.of(
    "TRANSFERENCIA ENVIADA PELO PIX",
    "TRANSFERENCIA ENVIADA",
    "PIX ENVIADO",
    "TRANSFERENCIA RECEBIDA PELO PIX",
    "TRANSFERENCIA RECEBIDA",
    "PIX RECEBIDO",
    "COMPRA NO DEBITO",
    "MOVIMENTACAO OFX",
    "INT ITAU MC"
  );
  private final BillRepository billRepository;
  private final IncomeRepository incomeRepository;
  private final BankAccountService bankAccountService;
  private final TrashService trashService;
  private final AuditPort auditPort;

  public InternalTransferService(
    BillRepository billRepository,
    IncomeRepository incomeRepository,
    BankAccountService bankAccountService,
    TrashService trashService,
    AuditPort auditPort
  ) {
    this.billRepository = billRepository;
    this.incomeRepository = incomeRepository;
    this.bankAccountService = bankAccountService;
    this.trashService = trashService;
    this.auditPort = auditPort;
  }

  @Transactional
  public ImportedDuplicateCleanupResponse cleanupImportedExpenseDuplicates(boolean dryRun) {
    List<Bill> candidates = billRepository.findAll().stream()
      .filter(item -> normalizeText(item.getCategory()).contains(IMPORTED_STATEMENT_CATEGORY))
      .toList();

    Map<String, List<Bill>> groups = new HashMap<>();
    for (Bill bill : candidates) {
      String signature = duplicateSignature(bill.getDescription());
      if (signature.isBlank()) {
        continue;
      }
      String key = bill.getDueDate() + "|" + formatAmountKey(bill.getAmount()) + "|" + normalizeOptionalId(bill.getBankAccountId()) + "|" + signature;
      groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(bill);
    }

    int groupsDetected = 0;
    int billsDetected = 0;
    int billsRemoved = 0;

    for (List<Bill> group : groups.values()) {
      if (group.size() <= 1) {
        continue;
      }

      List<Bill> genericEntries = group.stream()
        .filter(item -> isGenericDuplicateMemo(normalizeText(item.getDescription())))
        .toList();
      if (genericEntries.isEmpty()) {
        continue;
      }

      groupsDetected += 1;
      billsDetected += genericEntries.size();

      if (dryRun) {
        continue;
      }

      for (Bill duplicate : genericEntries) {
        trashService.moveToTrash("bill", duplicate.getId(), duplicate.getDescription(), duplicate);
        billRepository.deleteById(duplicate.getId());
        billsRemoved += 1;
      }
    }

    if (!dryRun && billsRemoved > 0) {
      auditPort.record(
        "transfer",
        "cleanup-imported-duplicates",
        "delete",
        "Limpeza de duplicidades importadas executada: " + billsRemoved + " despesas removidas",
        null
      );
    }

    return new ImportedDuplicateCleanupResponse(groupsDetected, billsDetected, billsRemoved, dryRun);
  }

  @Transactional
  public void linkInternalTransfer(String billId, String incomeId) {
    Bill bill = billRepository.findById(billId)
      .orElseThrow(() -> new NotFoundException("Conta nao encontrada: " + billId));
    Income income = incomeRepository.findById(incomeId)
      .orElseThrow(() -> new NotFoundException("Entrada nao encontrada: " + incomeId));

    bill.setInternalTransfer(true);
    income.setInternalTransfer(true);

    billRepository.save(bill);
    incomeRepository.save(income);

    auditPort.record(
      "transfer",
      billId + ":" + incomeId,
      "link-internal",
      "Transferencia interna vinculada entre conta e entrada",
      bill.getAmount()
    );
  }

  @Transactional
  public List<InternalTransferSuggestionResponse> detectInternalTransfers(
    String ownerName,
    String ownerCpf,
    int dateToleranceDays,
    boolean autoApply
  ) {
    List<Bill> bills = billRepository.findAll().stream().filter(item -> !item.isInternalTransfer()).toList();
    List<Income> incomes = incomeRepository.findAll().stream().filter(item -> !item.isInternalTransfer()).toList();

    String normalizedOwnerCpf = onlyDigits(ownerCpf);
    Set<String> ownerTokens = tokenizeOwnerName(ownerName);
    Map<String, String> accountLabelsById = new HashMap<>();
    for (BankAccount account : bankAccountService.listAll()) {
      accountLabelsById.put(account.getId(), account.getLabel());
    }

    List<MatchCandidate> candidates = new ArrayList<>();
    for (Bill bill : bills) {
      for (Income income : incomes) {
        if (isLoanOriginIncome(income)) {
          continue;
        }

        if (Math.abs(bill.getAmount() - income.getAmount()) > 0.009) {
          continue;
        }
        long daysDiff = Math.abs(ChronoUnit.DAYS.between(bill.getDueDate(), income.getReceivedAt()));
        if (daysDiff > dateToleranceDays) {
          continue;
        }

        int score = 60;
        List<String> reasons = new ArrayList<>();
        reasons.add("Valor igual");
        reasons.add("Data dentro da tolerancia");

        String billText = normalizeText(bill.getDescription());
        String incomeText = normalizeText(income.getSource());
        String combinedText = billText + " " + incomeText;

        boolean hasCpfEvidence = !normalizedOwnerCpf.isBlank() &&
          (billText.contains(normalizedOwnerCpf) || incomeText.contains(normalizedOwnerCpf));
        if (hasCpfEvidence) {
          score += 25;
          reasons.add("CPF do titular detectado no historico");
        }

        int matchedTokenCount = countMatchedOwnerTokens(combinedText, ownerTokens);
        boolean hasNameEvidence = matchedTokenCount >= 2;
        if (hasNameEvidence) {
          score += 20;
          reasons.add("Nome do titular detectado no historico");
        }

        boolean hasSameOwnershipEvidence = SAME_OWNERSHIP_MARKERS.stream().anyMatch(combinedText::contains);
        if (hasSameOwnershipEvidence) {
          score += 25;
          reasons.add("Marcador explicito de mesma titularidade");
        }

        boolean hasWalletEvidence = PICPAY_MARKERS.stream().anyMatch(combinedText::contains)
          || MERCADO_PAGO_MARKERS.stream().anyMatch(combinedText::contains);
        boolean hasOwnerIdentityEvidence = hasCpfEvidence || hasNameEvidence;
        boolean hasWalletOwnershipEvidence = hasWalletEvidence && hasOwnerIdentityEvidence;
        if (hasWalletOwnershipEvidence) {
          score += 15;
          reasons.add("Marcador de carteira digital (PicPay/Mercado Pago)");
        }

        boolean hasTransferPatternEvidence = (billText.contains("PIX") || billText.contains("TRANSFER"))
          && (incomeText.contains("PIX") || incomeText.contains("TRANSFER"));
        if (hasTransferPatternEvidence) {
          score += 10;
          reasons.add("Padrao de transferencia identificado");
        }

        boolean hasCoreOwnershipEvidence = hasOwnerIdentityEvidence || hasSameOwnershipEvidence || hasWalletOwnershipEvidence;
        if (!hasCoreOwnershipEvidence) {
          continue;
        }

        if (score >= 70) {
          candidates.add(new MatchCandidate(bill, income, score, daysDiff, reasons));
        }
      }
    }

    candidates.sort(Comparator
      .comparingInt(MatchCandidate::score).reversed()
      .thenComparingLong(MatchCandidate::daysDiff));

    Set<String> usedBills = new HashSet<>();
    Set<String> usedIncomes = new HashSet<>();
    List<InternalTransferSuggestionResponse> suggestions = new ArrayList<>();

    for (MatchCandidate candidate : candidates) {
      if (usedBills.contains(candidate.bill().getId()) || usedIncomes.contains(candidate.income().getId())) {
        continue;
      }

      usedBills.add(candidate.bill().getId());
      usedIncomes.add(candidate.income().getId());

      if (autoApply) {
        candidate.bill().setInternalTransfer(true);
        candidate.income().setInternalTransfer(true);
        billRepository.save(candidate.bill());
        incomeRepository.save(candidate.income());
      }

      suggestions.add(new InternalTransferSuggestionResponse(
        candidate.bill().getId(),
        candidate.income().getId(),
        candidate.bill().getDescription(),
        candidate.income().getSource(),
        candidate.bill().getBankAccountId(),
        candidate.income().getBankAccountId(),
        resolveBankLabel(candidate.bill().getBankAccountId(), accountLabelsById),
        resolveBankLabel(candidate.income().getBankAccountId(), accountLabelsById),
        candidate.bill().getDueDate(),
        candidate.income().getReceivedAt(),
        candidate.bill().getAmount(),
        candidate.score(),
        resolveConfidence(candidate.score()),
        candidate.reasons()
      ));
    }

    if (autoApply && !suggestions.isEmpty()) {
      auditPort.record(
        "transfer",
        "auto-detect",
        "auto-link-internal",
        "Transferencias internas vinculadas automaticamente: " + suggestions.size(),
        null
      );
    }

    return suggestions;
  }

  @Transactional
  public InternalTransferReclassificationResponse reclassifyLegacyTransfers(
    String ownerName,
    String ownerCpf,
    boolean includePicpay,
    boolean includeLegacyBroker,
    boolean includeInvestmentPurchases
  ) {
    Set<String> ownerTokens = tokenizeOwnerName(ownerName);
    String ownerCpfDigits = onlyDigits(ownerCpf);

    int billsMarked = 0;
    int incomesMarked = 0;

    for (Bill bill : billRepository.findAll()) {
      if (bill.isInternalTransfer()) {
        continue;
      }
      String normalized = normalizeText(bill.getDescription());
      if (!shouldMarkLegacyInternalTransfer(
        normalized,
        normalizeText(bill.getCategory()),
        ownerTokens,
        ownerCpfDigits,
        includePicpay,
        includeLegacyBroker,
        includeInvestmentPurchases
      )) {
        continue;
      }
      bill.setInternalTransfer(true);
      billRepository.save(bill);
      billsMarked += 1;
    }

    for (Income income : incomeRepository.findAll()) {
      if (income.isInternalTransfer() || isLoanOriginIncome(income)) {
        continue;
      }
      String normalized = normalizeText(income.getSource());
      if (!shouldMarkLegacyInternalTransfer(
        normalized,
        normalizeText(income.getCategory()),
        ownerTokens,
        ownerCpfDigits,
        includePicpay,
        includeLegacyBroker,
        includeInvestmentPurchases
      )) {
        continue;
      }
      income.setInternalTransfer(true);
      incomeRepository.save(income);
      incomesMarked += 1;
    }

    int totalMarked = billsMarked + incomesMarked;
    if (totalMarked > 0) {
      auditPort.record(
        "transfer",
        "reclassify-legacy",
        "reclassify-internal",
        "Reclassificacao retroativa de transferencias internas: " + totalMarked,
        null
      );
    }

    return new InternalTransferReclassificationResponse(billsMarked, incomesMarked, totalMarked);
  }

  private boolean shouldMarkLegacyInternalTransfer(
    String normalizedText,
    String normalizedCategory,
    Set<String> ownerTokens,
    String ownerCpfDigits,
    boolean includePicpay,
    boolean includeLegacyBroker,
    boolean includeInvestmentPurchases
  ) {
    if (normalizedText == null || normalizedText.isBlank()) {
      return false;
    }

    if (includeInvestmentPurchases && isImportedStatementCategory(normalizedCategory) && isInvestmentPurchaseMemo(normalizedText)) {
      return true;
    }

    boolean hasTransferKeyword = normalizedText.contains("PIX") ||
      normalizedText.contains("TRANSFER") ||
      normalizedText.contains("TED") ||
      normalizedText.contains("DOC");
    if (!hasTransferKeyword) {
      return false;
    }

    if (includeLegacyBroker && LEGACY_BROKER_MARKERS.stream().anyMatch(normalizedText::contains)) {
      return true;
    }

    boolean hasWalletMarker =
      PICPAY_MARKERS.stream().anyMatch(normalizedText::contains) ||
      MERCADO_PAGO_MARKERS.stream().anyMatch(normalizedText::contains);
    if (!includePicpay || !hasWalletMarker) {
      return false;
    }

    if (!ownerCpfDigits.isBlank()) {
      String textDigits = normalizedText.replaceAll("\\D", "");
      if (textDigits.contains(ownerCpfDigits)) {
        return true;
      }
    }

    return countMatchedOwnerTokens(normalizedText, ownerTokens) >= 2;
  }

  private boolean isInvestmentPurchaseMemo(String normalizedText) {
    if (normalizedText == null || normalizedText.isBlank()) {
      return false;
    }
    return INVESTMENT_PURCHASE_MARKERS.stream().anyMatch(normalizedText::contains);
  }

  private boolean isImportedStatementCategory(String normalizedCategory) {
    return normalizedCategory != null && normalizedCategory.contains(IMPORTED_STATEMENT_CATEGORY);
  }

  private Set<String> tokenizeOwnerName(String ownerName) {
    String normalized = normalizeText(ownerName);
    if (normalized.isBlank()) {
      return Set.of();
    }
    Set<String> tokens = new HashSet<>();
    for (String token : normalized.split(" ")) {
      if (token.length() >= 3) {
        tokens.add(token);
      }
    }
    return tokens;
  }

  private int countMatchedOwnerTokens(String text, Set<String> tokens) {
    if (tokens.isEmpty()) {
      return 0;
    }
    int matched = 0;
    for (String token : tokens) {
      if (text.contains(token)) {
        matched += 1;
      }
    }
    return matched;
  }

  private String normalizeText(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
      .replaceAll("\\p{M}", "")
      .toUpperCase(Locale.ROOT)
      .replaceAll("[^A-Z0-9 ]", " ")
      .replaceAll("\\s{2,}", " ")
      .trim();
    return normalized;
  }

  private String onlyDigits(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    return value.replaceAll("\\D", "");
  }

  private boolean isLoanOriginIncome(Income income) {
    String normalized = normalizeText(income.getSource());
    if (normalized.isBlank()) {
      return false;
    }
    return LOAN_INCOME_MARKERS.stream().anyMatch(normalized::contains);
  }

  private String duplicateSignature(String description) {
    String normalized = normalizeText(description);
    if (normalized.isBlank()) {
      return "";
    }
    if (normalized.equals("INT ITAU MC") || normalized.startsWith("ITAU MC ")) {
      return "ITAU_MC_PAYMENT";
    }
    if (normalized.contains("PAGAMENTO DE FATURA") || normalized.contains("PAGAMENTO DA FATURA")) {
      return "CARD_INVOICE_PAYMENT";
    }
    if (normalized.startsWith("TRANSFERENCIA ENVIADA PELO PIX") || normalized.startsWith("PIX ENVIADO") || normalized.startsWith("TRANSFERENCIA ENVIADA")) {
      return "PIX_OUT";
    }
    if (normalized.startsWith("TRANSFERENCIA RECEBIDA PELO PIX") || normalized.startsWith("PIX RECEBIDO") || normalized.startsWith("TRANSFERENCIA RECEBIDA")) {
      return "PIX_IN";
    }
    if (normalized.startsWith("COMPRA NO DEBITO")) {
      return "DEBIT_PURCHASE";
    }
    return "";
  }

  private boolean isGenericDuplicateMemo(String normalizedDescription) {
    if (normalizedDescription == null || normalizedDescription.isBlank()) {
      return false;
    }
    return GENERIC_DUPLICATE_MEMOS.stream().anyMatch(normalizedDescription::equals);
  }

  private String formatAmountKey(double value) {
    return String.format(Locale.US, "%.2f", value);
  }

  private String normalizeOptionalId(String value) {
    if (value == null || value.isBlank()) {
      return "NULL";
    }
    return value.trim();
  }

  private String resolveConfidence(int score) {
    if (score >= 105) {
      return "ALTA";
    }
    if (score >= 85) {
      return "MEDIA";
    }
    return "BAIXA";
  }

  private String resolveBankLabel(String bankAccountId, Map<String, String> accountLabelsById) {
    if (bankAccountId == null || bankAccountId.isBlank()) {
      return "Sem conta vinculada";
    }
    return accountLabelsById.getOrDefault(bankAccountId, "Conta nao localizada");
  }

  private record MatchCandidate(
    Bill bill,
    Income income,
    int score,
    long daysDiff,
    List<String> reasons
  ) {
  }
}
