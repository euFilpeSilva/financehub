package com.financehub.backend.modules.transfers.application;

import com.financehub.backend.modules.bills.domain.Bill;
import com.financehub.backend.modules.bills.domain.BillRepository;
import com.financehub.backend.modules.incomes.domain.Income;
import com.financehub.backend.modules.incomes.domain.IncomeRepository;
import com.financehub.backend.modules.transfers.api.dto.InternalTransferSuggestionResponse;
import com.financehub.backend.modules.transfers.api.dto.InternalTransferReclassificationResponse;
import com.financehub.backend.shared.api.NotFoundException;
import com.financehub.backend.shared.application.port.AuditPort;
import java.text.Normalizer;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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

  private final BillRepository billRepository;
  private final IncomeRepository incomeRepository;
  private final AuditPort auditPort;

  public InternalTransferService(
    BillRepository billRepository,
    IncomeRepository incomeRepository,
    AuditPort auditPort
  ) {
    this.billRepository = billRepository;
    this.incomeRepository = incomeRepository;
    this.auditPort = auditPort;
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

        if ((billText.contains("PIX") || billText.contains("TRANSFER")) && (incomeText.contains("PIX") || incomeText.contains("TRANSFER"))) {
          score += 10;
          reasons.add("Padrao de transferencia identificado");
        }

        if (!(hasCpfEvidence || hasNameEvidence)) {
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
        candidate.bill().getDueDate(),
        candidate.income().getReceivedAt(),
        candidate.bill().getAmount(),
        candidate.score(),
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
    boolean includeLegacyBroker
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
      if (!shouldMarkLegacyInternalTransfer(normalized, ownerTokens, ownerCpfDigits, includePicpay, includeLegacyBroker)) {
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
      if (!shouldMarkLegacyInternalTransfer(normalized, ownerTokens, ownerCpfDigits, includePicpay, includeLegacyBroker)) {
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
    Set<String> ownerTokens,
    String ownerCpfDigits,
    boolean includePicpay,
    boolean includeLegacyBroker
  ) {
    if (normalizedText == null || normalizedText.isBlank()) {
      return false;
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

  private record MatchCandidate(
    Bill bill,
    Income income,
    int score,
    long daysDiff,
    List<String> reasons
  ) {
  }
}
