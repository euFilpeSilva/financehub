package com.financehub.backend.modules.statements.application;

import com.financehub.backend.modules.bankaccounts.application.BankAccountService;
import com.financehub.backend.modules.bankaccounts.domain.BankAccount;
import com.financehub.backend.modules.bills.api.dto.BillRequest;
import com.financehub.backend.modules.bills.application.BillService;
import com.financehub.backend.modules.bills.domain.Bill;
import com.financehub.backend.modules.incomes.api.dto.IncomeRequest;
import com.financehub.backend.modules.incomes.application.IncomeService;
import com.financehub.backend.modules.incomes.domain.Income;
import com.financehub.backend.modules.statements.api.dto.OfxImportResponse;
import com.financehub.backend.modules.transfers.application.InternalTransferService;
import com.financehub.backend.shared.application.port.AuditPort;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class OfxImportService {
  private static final String IMPORTED_STATEMENT_CATEGORY = "Extrato importado";
  private static final Set<String> IGNORED_MEMO_MARKERS = Set.of(
    "RESGATE RDB",
    "APLICACAO RDB",
    "TRANSFERENCIA DE SALDO NUINVEST",
    "TRANSFERENCIA DE SALDO NUIVEST",
    "COMPRA DE FII",
    "COMPRA FII"
  );
  private static final Set<String> SAME_OWNERSHIP_MARKERS = Set.of(
    "MESMA TITULARIDADE",
    "MESMA TITULAR",
    "PROPRIA TITULARIDADE",
    "CONTA PROPRIA",
    "ENTRE CONTAS"
  );
  private static final Set<String> LOAN_CREDIT_MARKERS = Set.of(
    "EMPRESTIMO",
    "CREDITO REFERENTE A EMPRESTIMO",
    "CREDITO EMPRESTIMO",
    "CREDITO CONTRATADO"
  );
  private static final Pattern START_TAG_PATTERN = Pattern.compile("(?is)<DTSTART>(.*?)</DTSTART>");
  private static final Pattern END_TAG_PATTERN = Pattern.compile("(?is)<DTEND>(.*?)</DTEND>");

  private final BankAccountService bankAccountService;
  private final BillService billService;
  private final IncomeService incomeService;
  private final InternalTransferService internalTransferService;
  private final AuditPort auditPort;

  public OfxImportService(
    BankAccountService bankAccountService,
    BillService billService,
    IncomeService incomeService,
    InternalTransferService internalTransferService,
    AuditPort auditPort
  ) {
    this.bankAccountService = bankAccountService;
    this.billService = billService;
    this.incomeService = incomeService;
    this.internalTransferService = internalTransferService;
    this.auditPort = auditPort;
  }

  public OfxImportResponse importOfx(
    MultipartFile file,
    String ownerName,
    String ownerCpf,
    boolean applyInternalTransferDetection
  ) {
    if (!bankAccountService.hasActiveEligibleIncomeAccount()) {
      throw new IllegalArgumentException(
        "Importacao OFX bloqueada. Cadastre e marque pelo menos uma conta ativa como elegivel para entrada antes de importar."
      );
    }

    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("Arquivo OFX obrigatorio.");
    }
    String fileName = file.getOriginalFilename() == null ? "arquivo.ofx" : file.getOriginalFilename();
    if (!fileName.toLowerCase(Locale.ROOT).endsWith(".ofx")) {
      throw new IllegalArgumentException("Arquivo invalido. Envie um OFX.");
    }

    String content = readOfxContent(file);
    if (!content.toUpperCase(Locale.ROOT).contains("<OFX>")) {
      throw new IllegalArgumentException("Conteudo OFX invalido.");
    }

    BankAccount importedBankAccount = bankAccountService.findByOfxIdentifiers(
      extractTag(content, "BANKID"),
      extractTag(content, "ACCTID")
    ).orElse(null);
    String importedBankAccountId = importedBankAccount == null ? null : importedBankAccount.getId();

    Set<String> existingBillKeys = new HashSet<>();
    for (Bill bill : billService.listAll()) {
      existingBillKeys.add(buildBillKey(bill.getDueDate(), bill.getAmount(), bill.getDescription()));
    }
    Set<String> existingIncomeKeys = new HashSet<>();
    for (Income income : incomeService.listAll()) {
      existingIncomeKeys.add(buildIncomeKey(income.getReceivedAt(), income.getAmount(), income.getSource()));
    }

    int total = 0;
    int createdBills = 0;
    int createdIncomes = 0;
    int skippedDuplicates = 0;

    for (String block : extractTransactionBlocks(content)) {
      OfxTransaction tx = parseTransaction(block);
      if (tx == null) {
        continue;
      }
      if (shouldIgnoreTransaction(tx.memo())) {
        continue;
      }
      total += 1;
      boolean creditTransaction = tx.amount().compareTo(BigDecimal.ZERO) > 0;
      boolean internalTransfer = detectInternalByMemo(
        tx.memo(),
        ownerCpf,
        creditTransaction
      );

      if (tx.amount().compareTo(BigDecimal.ZERO) < 0) {
        double absAmount = tx.amount().abs().doubleValue();
        String key = buildBillKey(tx.postedAt(), absAmount, tx.memo());
        if (existingBillKeys.contains(key)) {
          skippedDuplicates += 1;
          continue;
        }
        billService.create(new BillRequest(
          tx.memo(),
          IMPORTED_STATEMENT_CATEGORY,
          absAmount,
          tx.postedAt(),
          false,
          true,
          internalTransfer,
          importedBankAccountId
        ));
        existingBillKeys.add(key);
        createdBills += 1;
      } else if (tx.amount().compareTo(BigDecimal.ZERO) > 0) {
        double amount = tx.amount().doubleValue();
        String key = buildIncomeKey(tx.postedAt(), amount, tx.memo());
        if (existingIncomeKeys.contains(key)) {
          skippedDuplicates += 1;
          continue;
        }
        incomeService.create(new IncomeRequest(
          tx.memo(),
          IMPORTED_STATEMENT_CATEGORY,
          amount,
          tx.postedAt(),
          false,
          internalTransfer,
          importedBankAccountId
        ));
        existingIncomeKeys.add(key);
        createdIncomes += 1;
      }
    }

    for (OfxTransaction tx : extractBalanceIncomeBlocks(content)) {
      total += 1;
      boolean internalTransfer = detectInternalByMemo(tx.memo(), ownerCpf, true);
      double amount = tx.amount().doubleValue();
      String key = buildIncomeKey(tx.postedAt(), amount, tx.memo());
      if (existingIncomeKeys.contains(key)) {
        skippedDuplicates += 1;
        continue;
      }
      incomeService.create(new IncomeRequest(
        tx.memo(),
        IMPORTED_STATEMENT_CATEGORY,
        amount,
        tx.postedAt(),
        false,
        internalTransfer,
        importedBankAccountId
      ));
      existingIncomeKeys.add(key);
      createdIncomes += 1;
    }

    int markedInternalTransfers = 0;
    if (applyInternalTransferDetection && hasTransferDetectionEvidence(ownerName, ownerCpf)) {
      List<?> suggestions = internalTransferService.detectInternalTransfers(ownerName, ownerCpf, 1, true);
      markedInternalTransfers = suggestions.size();
    }

    String periodInfo = extractPeriodInfo(content);
    String auditEntityId = (importedBankAccountId == null || importedBankAccountId.isBlank())
      ? "ofx-import"
      : importedBankAccountId;

    auditPort.record(
      "statement",
      auditEntityId,
      "import",
      "Importacao OFX concluida (" + fileName + ") " + periodInfo + " com " + total + " transacoes",
      null
    );

    return new OfxImportResponse(fileName, total, createdBills, createdIncomes, skippedDuplicates, markedInternalTransfers);
  }

  private String readOfxContent(MultipartFile file) {
    try {
      byte[] bytes = file.getBytes();
      String preview = new String(bytes, StandardCharsets.US_ASCII);
      Charset charset = resolveCharset(preview);
      return new String(bytes, charset);
    } catch (Exception ex) {
      throw new IllegalArgumentException("Falha ao ler arquivo OFX.", ex);
    }
  }

  private Charset resolveCharset(String preview) {
    String upper = preview.toUpperCase(Locale.ROOT);
    if (upper.contains("ENCODING:UTF-8")) {
      return StandardCharsets.UTF_8;
    }
    return StandardCharsets.ISO_8859_1;
  }

  private OfxTransaction parseTransaction(String block) {
    String dtPostedRaw = extractTag(block, "DTPOSTED");
    String trnAmtRaw = extractTag(block, "TRNAMT");
    String memoRaw = extractTag(block, "MEMO");
    if (dtPostedRaw == null || trnAmtRaw == null) {
      return null;
    }
    LocalDate postedAt = parseDate(dtPostedRaw);
    BigDecimal amount = parseAmount(trnAmtRaw);
    if (postedAt == null || amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
      return null;
    }
    String memo = sanitizeMemo(memoRaw);
    return new OfxTransaction(postedAt, amount, memo);
  }

  private LocalDate parseDate(String raw) {
    try {
      String normalized = raw.replaceAll("[^0-9]", "");
      if (normalized.length() < 8) {
        return null;
      }
      String date = normalized.substring(0, 8);
      int year = Integer.parseInt(date.substring(0, 4));
      int month = Integer.parseInt(date.substring(4, 6));
      int day = Integer.parseInt(date.substring(6, 8));
      return LocalDate.of(year, month, day);
    } catch (Exception ex) {
      return null;
    }
  }

  private BigDecimal parseAmount(String raw) {
    try {
      String normalized = raw.replace(",", ".").trim();
      return new BigDecimal(normalized);
    } catch (Exception ex) {
      return null;
    }
  }

  private String extractTag(String block, String tag) {
    Pattern pattern = Pattern.compile("(?is)<" + tag + ">(.*?)</" + tag + ">");
    Matcher matcher = pattern.matcher(block);
    if (!matcher.find()) {
      Pattern linePattern = Pattern.compile("(?im)<" + tag + ">([^<\\r\\n]+)");
      Matcher lineMatcher = linePattern.matcher(block);
      if (!lineMatcher.find()) {
        return null;
      }
      return lineMatcher.group(1).trim();
    }
    return matcher.group(1).trim();
  }

  private String sanitizeMemo(String memoRaw) {
    String memo = memoRaw == null || memoRaw.isBlank() ? "Movimentacao OFX" : memoRaw;
    memo = memo
      .replace('\n', ' ')
      .replace('\r', ' ')
      .replace('<', ' ')
      .replace('>', ' ')
      .replaceAll("\\s{2,}", " ")
      .trim();
    if (memo.length() > 120) {
      memo = memo.substring(0, 120);
    }
    return memo;
  }

  private boolean detectInternalByMemo(
    String memo,
    String ownerCpf,
    boolean creditTransaction
  ) {
    String normalized = normalizeText(memo);

    if (creditTransaction && isLoanCreditMemo(normalized)) {
      return false;
    }

    boolean hasTransferWord =
      normalized.contains("PIX") ||
      normalized.contains("TRANSFER") ||
      normalized.contains("TED") ||
      normalized.contains("DOC");
    if (!hasTransferWord) {
      return false;
    }

    if (SAME_OWNERSHIP_MARKERS.stream().anyMatch(normalized::contains)) {
      return true;
    }

    String cpfDigits = ownerCpf == null ? "" : ownerCpf.replaceAll("\\D", "");
    if (cpfDigits.isBlank()) {
      return false;
    }

    return memo.replaceAll("\\D", "").contains(cpfDigits);
  }

  private boolean isLoanCreditMemo(String normalizedMemo) {
    if (normalizedMemo == null || normalizedMemo.isBlank()) {
      return false;
    }
    return LOAN_CREDIT_MARKERS.stream().anyMatch(normalizedMemo::contains);
  }

  private boolean shouldIgnoreTransaction(String memo) {
    String normalized = normalizeText(memo);
    if (normalized.isBlank()) {
      return false;
    }
    return IGNORED_MEMO_MARKERS.stream().anyMatch(normalized::contains);
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

  private boolean hasTransferDetectionEvidence(String ownerName, String ownerCpf) {
    boolean hasName = ownerName != null && !ownerName.isBlank();
    boolean hasCpf = ownerCpf != null && !ownerCpf.replaceAll("\\D", "").isBlank();
    return hasName || hasCpf;
  }

  private String buildBillKey(LocalDate dueDate, double amount, String description) {
    return dueDate + "|" + String.format(Locale.US, "%.2f", amount) + "|" + normalizeText(description);
  }

  private String buildIncomeKey(LocalDate receivedAt, double amount, String source) {
    return receivedAt + "|" + String.format(Locale.US, "%.2f", amount) + "|" + normalizeText(source);
  }

  private String extractPeriodInfo(String content) {
    String start = extractByPattern(content, START_TAG_PATTERN);
    String end = extractByPattern(content, END_TAG_PATTERN);
    if (start == null) {
      start = extractTag(content, "DTSTART");
    }
    if (end == null) {
      end = extractTag(content, "DTEND");
    }
    if (start == null || end == null) {
      return "";
    }
    return "(periodo: " + start + " a " + end + ")";
  }

  private String extractByPattern(String content, Pattern pattern) {
    Matcher matcher = pattern.matcher(content);
    if (!matcher.find()) {
      return null;
    }
    return matcher.group(1).trim();
  }

  private List<String> extractTransactionBlocks(String content) {
    String[] chunks = content.split("(?i)<STMTTRN>");
    if (chunks.length <= 1) {
      return List.of();
    }
    List<String> blocks = new java.util.ArrayList<>();
    for (int i = 1; i < chunks.length; i += 1) {
      String chunk = chunks[i];
      int endIndex = indexOfIgnoreCase(chunk, "</STMTTRN>");
      String block = endIndex >= 0 ? chunk.substring(0, endIndex) : chunk;
      blocks.add(block);
    }
    return blocks;
  }

  private List<OfxTransaction> extractBalanceIncomeBlocks(String content) {
    String[] chunks = content.split("(?i)<BAL>");
    if (chunks.length <= 1) {
      return List.of();
    }

    List<OfxTransaction> blocks = new java.util.ArrayList<>();
    String fallbackDateRaw = extractTag(content, "DTASOF");
    if (fallbackDateRaw == null || fallbackDateRaw.isBlank()) {
      fallbackDateRaw = extractTag(content, "DTEND");
    }

    for (int i = 1; i < chunks.length; i += 1) {
      String chunk = chunks[i];
      int endIndex = indexOfIgnoreCase(chunk, "</BAL>");
      String block = endIndex >= 0 ? chunk.substring(0, endIndex) : chunk;

      String nameRaw = extractTag(block, "NAME");
      String descRaw = extractTag(block, "DESC");
      String valueRaw = extractTag(block, "VALUE");
      if (valueRaw == null) {
        continue;
      }

      String nameNormalized = normalizeText(nameRaw);
      String descNormalized = normalizeText(descRaw);
      boolean rendimento = nameNormalized.contains("RENDIMENTO") || descNormalized.contains("RENDIMENTO");
      if (!rendimento) {
        continue;
      }

      BigDecimal amount = parseAmount(valueRaw);
      if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
        continue;
      }

      String balanceDateRaw = extractTag(block, "DTASOF");
      if (balanceDateRaw == null || balanceDateRaw.isBlank()) {
        balanceDateRaw = fallbackDateRaw;
      }
      LocalDate postedAt = parseDate(balanceDateRaw);
      if (postedAt == null) {
        continue;
      }

      String source = buildBalanceMemo(nameRaw, descRaw);
      blocks.add(new OfxTransaction(postedAt, amount, source));
    }

    return blocks;
  }

  private String buildBalanceMemo(String nameRaw, String descRaw) {
    String name = nameRaw == null ? "" : nameRaw.trim();
    String desc = descRaw == null ? "" : descRaw.trim();
    if (!name.isBlank() && !desc.isBlank()) {
      return sanitizeMemo(name + " - " + desc);
    }
    if (!desc.isBlank()) {
      return sanitizeMemo(desc);
    }
    if (!name.isBlank()) {
      return sanitizeMemo(name);
    }
    return sanitizeMemo("Rendimento OFX");
  }

  private int indexOfIgnoreCase(String text, String needle) {
    return text.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
  }

  private record OfxTransaction(LocalDate postedAt, BigDecimal amount, String memo) {
  }
}
