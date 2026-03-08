package com.financehub.backend.modules.statements.application;

import com.financehub.backend.modules.bankaccounts.application.BankAccountService;
import com.financehub.backend.modules.bankaccounts.api.dto.BankAccountRequest;
import com.financehub.backend.modules.bankaccounts.domain.BankAccount;
import com.financehub.backend.modules.bills.api.dto.BillRequest;
import com.financehub.backend.modules.bills.application.BillService;
import com.financehub.backend.modules.bills.domain.Bill;
import com.financehub.backend.modules.incomes.api.dto.IncomeRequest;
import com.financehub.backend.modules.incomes.application.IncomeService;
import com.financehub.backend.modules.incomes.domain.Income;
import com.financehub.backend.modules.statements.api.dto.OfxImportResponse;
import com.financehub.backend.modules.statements.api.dto.OfxAnalysisGroupResponse;
import com.financehub.backend.modules.statements.api.dto.OfxAnalysisResponse;
import com.financehub.backend.modules.statements.api.dto.OfxAnalysisTransactionResponse;
import com.financehub.backend.modules.transfers.application.InternalTransferService;
import com.financehub.backend.shared.application.port.AuditPort;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class OfxImportService {
  private static final String IMPORTED_STATEMENT_CATEGORY = "Extrato importado";
  private static final String LEGACY_INVESTMENT_TECHNICAL_LABEL = "Investimentos - Legado Easynvest";
  private static final String LEGACY_INVESTMENT_TECHNICAL_BANK_ID = "999";
  private static final String LEGACY_INVESTMENT_TECHNICAL_ACCOUNT_ID = "LEGADOEASYNVEST";
  private static final String PICPAY_TECHNICAL_LABEL = "Carteira Tecnica - PicPay";
  private static final String PICPAY_TECHNICAL_BANK_ID = "995";
  private static final String PICPAY_TECHNICAL_ACCOUNT_ID = "TECNICAPICPAY";
  private static final String MERCADO_PAGO_TECHNICAL_LABEL = "Carteira Tecnica - Mercado Pago";
  private static final String MERCADO_PAGO_TECHNICAL_BANK_ID = "994";
  private static final String MERCADO_PAGO_TECHNICAL_ACCOUNT_ID = "TECNICAMERCADOPAGO";
  private static final Set<String> IGNORED_MEMO_MARKERS = Set.of(
    "RESGATE RDB",
    "APLICACAO RDB",
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
  private static final Set<String> LEGACY_INVESTMENT_MARKERS = Set.of(
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
  private static final Set<String> INVESTMENT_PURCHASE_MARKERS = Set.of(
    "COMPRA DE BDR",
    "COMPRA BDR"
  );
  private static final String ITAU_CARD_PAYMENT_INTERNAL_MEMO = "INT ITAU MC";
  private static final String ITAU_CARD_PAYMENT_DETAILED_PREFIX = "ITAU MC";
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

    BankAccount importedBankAccount = resolveImportedBankAccount(content);
    String importedBankAccountId = importedBankAccount == null ? null : importedBankAccount.getId();

    Set<String> existingBillKeys = new HashSet<>();
    for (Bill bill : billService.listAll()) {
      existingBillKeys.add(buildBillKey(bill.getDueDate(), bill.getAmount(), bill.getDescription()));
    }
    Set<String> preImportedBillKeys = new HashSet<>(existingBillKeys);
    Set<String> existingIncomeKeys = new HashSet<>();
    for (Income income : incomeService.listAll()) {
      existingIncomeKeys.add(buildIncomeKey(income.getReceivedAt(), income.getAmount(), income.getSource()));
    }
    Set<String> preImportedIncomeKeys = new HashSet<>(existingIncomeKeys);

    int total = 0;
    int createdBills = 0;
    int createdIncomes = 0;
    int skippedDuplicates = 0;
    int ignoredAlreadyImported = 0;
    List<String> ignoredAlreadyImportedSamples = new ArrayList<>();
    boolean legacyTechnicalAccountEnsured = false;
    boolean picPayTechnicalAccountEnsured = false;
    boolean mercadoPagoTechnicalAccountEnsured = false;

    List<OfxTransaction> parsedTransactions = extractTransactionBlocks(content).stream()
      .map(this::parseTransaction)
      .filter(tx -> tx != null)
      .toList();
    Set<String> itauCardPaymentPairKeys = buildItauCardPaymentPairKeys(parsedTransactions);

    for (OfxTransaction tx : parsedTransactions) {
      if (shouldIgnoreTransaction(tx.memo())) {
        continue;
      }

      if (shouldSkipItauCardPairDuplicate(tx, itauCardPaymentPairKeys)) {
        skippedDuplicates += 1;
        continue;
      }

      String normalizedMemo = normalizeText(tx.memo());

      if (!legacyTechnicalAccountEnsured && isLegacyInvestmentTransferMemoNormalized(normalizedMemo)) {
        ensureLegacyInvestmentTechnicalAccount();
        legacyTechnicalAccountEnsured = true;
      }

      if (!picPayTechnicalAccountEnsured && isPicPayOwnTransferMemo(normalizedMemo, ownerName, ownerCpf)) {
        ensurePicPayTechnicalAccount();
        picPayTechnicalAccountEnsured = true;
      }

      if (!mercadoPagoTechnicalAccountEnsured && isMercadoPagoOwnTransferMemo(normalizedMemo, ownerName, ownerCpf)) {
        ensureMercadoPagoTechnicalAccount();
        mercadoPagoTechnicalAccountEnsured = true;
      }

      total += 1;
      boolean creditTransaction = tx.amount().compareTo(BigDecimal.ZERO) > 0;
      boolean internalTransfer = detectInternalByMemo(
        tx.memo(),
        ownerName,
        ownerCpf,
        creditTransaction
      );

      if (tx.amount().compareTo(BigDecimal.ZERO) < 0) {
        double absAmount = tx.amount().abs().doubleValue();
        String key = buildBillKey(tx.postedAt(), absAmount, tx.memo());
        if (existingBillKeys.contains(key)) {
          skippedDuplicates += 1;
          if (preImportedBillKeys.contains(key)) {
            ignoredAlreadyImported += 1;
            if (ignoredAlreadyImportedSamples.size() < 20) {
              ignoredAlreadyImportedSamples.add(buildIgnoredSample("SAIDA", tx.postedAt(), absAmount, tx.memo()));
            }
          }
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
          if (preImportedIncomeKeys.contains(key)) {
            ignoredAlreadyImported += 1;
            if (ignoredAlreadyImportedSamples.size() < 20) {
              ignoredAlreadyImportedSamples.add(buildIgnoredSample("ENTRADA", tx.postedAt(), amount, tx.memo()));
            }
          }
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
      boolean internalTransfer = detectInternalByMemo(tx.memo(), ownerName, ownerCpf, true);
      double amount = tx.amount().doubleValue();
      String key = buildIncomeKey(tx.postedAt(), amount, tx.memo());
      if (existingIncomeKeys.contains(key)) {
        skippedDuplicates += 1;
        if (preImportedIncomeKeys.contains(key)) {
          ignoredAlreadyImported += 1;
          if (ignoredAlreadyImportedSamples.size() < 20) {
            ignoredAlreadyImportedSamples.add(buildIgnoredSample("ENTRADA", tx.postedAt(), amount, tx.memo()));
          }
        }
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
      "Importacao OFX concluida (" + fileName + ") " + periodInfo + " com " + total + " transacoes"
        + ", ignoradas: " + skippedDuplicates
        + " (ja importadas: " + ignoredAlreadyImported + ")",
      null
    );

    if (ignoredAlreadyImported > 0) {
      String details = ignoredAlreadyImportedSamples.isEmpty()
        ? ""
        : " Amostras: " + String.join(" | ", ignoredAlreadyImportedSamples);
      auditPort.record(
        "statement",
        auditEntityId,
        "import",
        "Importacao OFX ignorou " + ignoredAlreadyImported + " registros ja importados anteriormente." + details,
        null
      );
    }

    return new OfxImportResponse(
      fileName,
      total,
      createdBills,
      createdIncomes,
      skippedDuplicates,
      ignoredAlreadyImported,
      markedInternalTransfers
    );
  }

  public OfxAnalysisResponse analyzeOfx(List<MultipartFile> files, String ownerName, String ownerCpf) {
    if (files == null || files.isEmpty()) {
      throw new IllegalArgumentException("Envie ao menos um arquivo OFX para analise.");
    }

    List<OfxAnalysisTransactionResponse> transactions = new ArrayList<>();
    Map<String, GroupAccumulator> grouped = new LinkedHashMap<>();
    Set<Integer> availableYears = new TreeSet<>();
    Set<String> availableYearMonths = new TreeSet<>();
    int totalCredits = 0;
    int totalDebits = 0;

    for (MultipartFile file : files) {
      if (file == null || file.isEmpty()) {
        continue;
      }

      String fileName = file.getOriginalFilename() == null ? "arquivo.ofx" : file.getOriginalFilename();
      if (!fileName.toLowerCase(Locale.ROOT).endsWith(".ofx")) {
        throw new IllegalArgumentException("Arquivo invalido na analise: " + fileName + ". Envie apenas OFX.");
      }

      String content = readOfxContent(file);
      if (!content.toUpperCase(Locale.ROOT).contains("<OFX>")) {
        throw new IllegalArgumentException("Conteudo OFX invalido no arquivo: " + fileName + ".");
      }

      BankAccount ownerBankAccount = resolveImportedBankAccount(content);
      String ofxOwnerBankAccountId = ownerBankAccount == null ? null : ownerBankAccount.getId();
      String ofxOwnerBankLabel = ownerBankAccount == null ? "Banco nao identificado" : ownerBankAccount.getLabel();

      List<OfxTransaction> parsedTransactions = extractTransactionBlocks(content).stream()
        .map(this::parseTransaction)
        .filter(tx -> tx != null)
        .toList();
      Set<String> itauCardPaymentPairKeys = buildItauCardPaymentPairKeys(parsedTransactions);

      for (OfxTransaction tx : parsedTransactions) {
        String normalizedMemo = normalizeText(tx.memo());
        String patternKey = buildMemoPatternKey(normalizedMemo);
        boolean ignoredByMarker = shouldIgnoreTransaction(tx.memo());
        boolean itauPairDuplicateCandidate = shouldSkipItauCardPairDuplicate(tx, itauCardPaymentPairKeys);
        boolean likelyInternalTransfer = detectInternalByMemo(
          tx.memo(),
          ownerName,
          ownerCpf,
          tx.amount().compareTo(BigDecimal.ZERO) > 0
        );

        LocalDate postedAt = tx.postedAt();
        int year = postedAt.getYear();
        String yearMonth = formatYearMonth(postedAt);
        availableYears.add(year);
        availableYearMonths.add(yearMonth);

        double amount = tx.amount().doubleValue();
        String direction = amount >= 0 ? "credit" : "debit";
        if (amount >= 0) {
          totalCredits += 1;
        } else {
          totalDebits += 1;
        }

        transactions.add(new OfxAnalysisTransactionResponse(
          fileName,
          ofxOwnerBankAccountId,
          ofxOwnerBankLabel,
          postedAt,
          year,
          yearMonth,
          "STMTTRN",
          amount,
          direction,
          tx.memo(),
          normalizedMemo,
          patternKey,
          ignoredByMarker,
          likelyInternalTransfer,
          itauPairDuplicateCandidate
        ));

        GroupAccumulator accumulator = grouped.computeIfAbsent(patternKey, (key) -> new GroupAccumulator(patternKey));
        accumulator.register(
          tx.memo(),
          amount,
          ignoredByMarker,
          likelyInternalTransfer,
          itauPairDuplicateCandidate,
          year,
          yearMonth
        );
      }

      for (OfxTransaction tx : extractBalanceIncomeBlocks(content)) {
        String normalizedMemo = normalizeText(tx.memo());
        String patternKey = buildMemoPatternKey(normalizedMemo);
        boolean likelyInternalTransfer = detectInternalByMemo(tx.memo(), ownerName, ownerCpf, true);
        LocalDate postedAt = tx.postedAt();
        int year = postedAt.getYear();
        String yearMonth = formatYearMonth(postedAt);
        availableYears.add(year);
        availableYearMonths.add(yearMonth);

        double amount = tx.amount().doubleValue();
        totalCredits += 1;

        transactions.add(new OfxAnalysisTransactionResponse(
          fileName,
          ofxOwnerBankAccountId,
          ofxOwnerBankLabel,
          postedAt,
          year,
          yearMonth,
          "BAL",
          amount,
          "credit",
          tx.memo(),
          normalizedMemo,
          patternKey,
          false,
          likelyInternalTransfer,
          false
        ));

        GroupAccumulator accumulator = grouped.computeIfAbsent(patternKey, (key) -> new GroupAccumulator(patternKey));
        accumulator.register(tx.memo(), amount, false, likelyInternalTransfer, false, year, yearMonth);
      }
    }

    List<OfxAnalysisGroupResponse> groups = grouped.values().stream()
      .map(GroupAccumulator::toResponse)
      .sorted((first, second) -> Integer.compare(second.totalCount(), first.totalCount()))
      .toList();

    return new OfxAnalysisResponse(
      files.size(),
      transactions.size(),
      totalCredits,
      totalDebits,
      new ArrayList<>(availableYears),
      new ArrayList<>(availableYearMonths),
      groups,
      transactions
    );
  }

  private String formatYearMonth(LocalDate date) {
    return String.format(Locale.ROOT, "%04d-%02d", date.getYear(), date.getMonthValue());
  }

  private String buildMemoPatternKey(String normalizedMemo) {
    if (normalizedMemo == null || normalizedMemo.isBlank()) {
      return "SEM DESCRICAO";
    }
    return normalizedMemo
      .replaceAll("\\b\\d{2,}\\b", "<NUM>")
      .replaceAll("\\s{2,}", " ")
      .trim();
  }

  private String buildIgnoredSample(String type, LocalDate date, double amount, String memo) {
    return type + " " + date + " R$" + String.format(Locale.US, "%.2f", amount) + " " + sanitizeMemo(memo);
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
    String ownerName,
    String ownerCpf,
    boolean creditTransaction
  ) {
    String normalized = normalizeText(memo);

    if (creditTransaction && isLoanCreditMemo(normalized)) {
      return false;
    }

    if (isInvestmentPurchaseMemo(normalized)) {
      return true;
    }

    boolean hasTransferWord = hasTransferKeyword(normalized);
    if (!hasTransferWord) {
      return false;
    }

    if (isLegacyInvestmentTransferMemoNormalized(normalized)) {
      return true;
    }

    if (isPicPayOwnTransferMemo(normalized, ownerName, ownerCpf)) {
      return true;
    }

    if (isMercadoPagoOwnTransferMemo(normalized, ownerName, ownerCpf)) {
      return true;
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

  private boolean isPicPayOwnTransferMemo(String normalizedMemo, String ownerName, String ownerCpf) {
    if (normalizedMemo == null || normalizedMemo.isBlank()) {
      return false;
    }
    if (!hasTransferKeyword(normalizedMemo)) {
      return false;
    }

    boolean hasPicPayMarker = PICPAY_MARKERS.stream().anyMatch(normalizedMemo::contains);
    if (!hasPicPayMarker) {
      return false;
    }

    String cpfDigits = ownerCpf == null ? "" : ownerCpf.replaceAll("\\D", "");
    if (!cpfDigits.isBlank() && normalizedMemo.replaceAll("\\D", "").contains(cpfDigits)) {
      return true;
    }

    return hasOwnerNameEvidence(normalizedMemo, ownerName);
  }

  private boolean isMercadoPagoOwnTransferMemo(String normalizedMemo, String ownerName, String ownerCpf) {
    if (normalizedMemo == null || normalizedMemo.isBlank()) {
      return false;
    }
    if (!hasTransferKeyword(normalizedMemo)) {
      return false;
    }

    boolean hasMercadoPagoMarker = MERCADO_PAGO_MARKERS.stream().anyMatch(normalizedMemo::contains);
    if (!hasMercadoPagoMarker) {
      return false;
    }

    String cpfDigits = ownerCpf == null ? "" : ownerCpf.replaceAll("\\D", "");
    if (!cpfDigits.isBlank() && normalizedMemo.replaceAll("\\D", "").contains(cpfDigits)) {
      return true;
    }

    return hasOwnerNameEvidence(normalizedMemo, ownerName);
  }

  private boolean hasOwnerNameEvidence(String normalizedText, String ownerName) {
    String normalizedOwnerName = normalizeText(ownerName);
    if (normalizedOwnerName.isBlank()) {
      return false;
    }

    int matchedTokens = 0;
    for (String token : normalizedOwnerName.split(" ")) {
      if (token.length() < 3) {
        continue;
      }
      if (normalizedText.contains(token)) {
        matchedTokens += 1;
      }
    }
    return matchedTokens >= 2;
  }

  private boolean isLegacyInvestmentTransferMemo(String memo) {
    String normalized = normalizeText(memo);
    return isLegacyInvestmentTransferMemoNormalized(normalized);
  }

  private boolean isLegacyInvestmentTransferMemoNormalized(String normalizedMemo) {
    if (normalizedMemo == null || normalizedMemo.isBlank()) {
      return false;
    }
    if (!hasTransferKeyword(normalizedMemo)) {
      return false;
    }
    return LEGACY_INVESTMENT_MARKERS.stream().anyMatch(normalizedMemo::contains);
  }

  private boolean hasTransferKeyword(String normalizedMemo) {
    return normalizedMemo.contains("PIX") ||
      normalizedMemo.contains("TRANSFER") ||
      normalizedMemo.contains("TED") ||
      normalizedMemo.contains("DOC");
  }

  private void ensureLegacyInvestmentTechnicalAccount() {
    ensureTechnicalAccount(
      LEGACY_INVESTMENT_TECHNICAL_LABEL,
      LEGACY_INVESTMENT_TECHNICAL_BANK_ID,
      LEGACY_INVESTMENT_TECHNICAL_ACCOUNT_ID
    );
  }

  private void ensurePicPayTechnicalAccount() {
    ensureTechnicalAccount(
      PICPAY_TECHNICAL_LABEL,
      PICPAY_TECHNICAL_BANK_ID,
      PICPAY_TECHNICAL_ACCOUNT_ID
    );
  }

  private void ensureMercadoPagoTechnicalAccount() {
    ensureTechnicalAccount(
      MERCADO_PAGO_TECHNICAL_LABEL,
      MERCADO_PAGO_TECHNICAL_BANK_ID,
      MERCADO_PAGO_TECHNICAL_ACCOUNT_ID
    );
  }

  private void ensureTechnicalAccount(String label, String bankId, String accountId) {
    boolean exists = bankAccountService.listAll().stream()
      .anyMatch(account -> normalizeText(account.getLabel()).equals(normalizeText(label)));
    if (exists) {
      return;
    }

    bankAccountService.create(new BankAccountRequest(
      label,
      bankId,
      null,
      accountId,
      false,
      true
    ));
  }

  private BankAccount resolveImportedBankAccount(String content) {
    String bankIdRaw = extractTag(content, "BANKID");
    String accountIdRaw = extractTag(content, "ACCTID");
    String fidRaw = extractTag(content, "FID");
    String orgRaw = extractTag(content, "ORG");
    List<BankAccount> accounts = bankAccountService.listAll();

    BankAccount exactActive = bankAccountService.findByOfxIdentifiers(bankIdRaw, accountIdRaw).orElse(null);
    if (exactActive != null) {
      return exactActive;
    }

    String normalizedBankId = normalizeBankId(bankIdRaw);
    if (normalizedBankId.isBlank()) {
      normalizedBankId = normalizeBankId(fidRaw);
    }
    if (normalizedBankId.isBlank()) {
      normalizedBankId = inferBankIdFromOrganization(orgRaw);
    }

    String normalizedAccountId = normalizeAccountId(accountIdRaw);
    String normalizedAccountDigits = normalizeDigits(accountIdRaw);
    String bankIdForMatch = normalizedBankId;
    String accountIdForMatch = normalizedAccountId;
    String accountDigitsForMatch = normalizedAccountDigits;

    if (!bankIdForMatch.isBlank() && !accountIdForMatch.isBlank()) {
      List<BankAccount> exactCandidates = accounts.stream()
        .filter(item -> normalizeBankId(item.getBankId()).equals(bankIdForMatch))
        .filter(item -> normalizeAccountId(item.getAccountId()).equals(accountIdForMatch))
        .toList();
      BankAccount exactUnique = pickUniqueCandidate(exactCandidates);
      if (exactUnique != null) {
        return exactUnique;
      }
    }

    if (!bankIdForMatch.isBlank() && !accountDigitsForMatch.isBlank()) {
      List<BankAccount> sameBankCandidates = accounts.stream()
        .filter(item -> normalizeBankId(item.getBankId()).equals(bankIdForMatch))
        .toList();

      BankAccount byDigits = resolveByAccountDigits(sameBankCandidates, accountDigitsForMatch);
      if (byDigits != null) {
        return byDigits;
      }
    }

    if (!bankIdForMatch.isBlank()) {
      List<BankAccount> sameBankCandidates = accounts.stream()
        .filter(item -> normalizeBankId(item.getBankId()).equals(bankIdForMatch))
        .toList();

      // So vincula por banco quando ha apenas uma conta candidata para evitar associacao errada.
      BankAccount uniqueByBank = pickUniqueCandidate(sameBankCandidates);
      if (uniqueByBank != null) {
        return uniqueByBank;
      }

      // Para extratos com cabecalho parcial (comum em alguns bancos), ainda
      // vinculamos por BANKID para preservar a flag do banco no extrato.
      BankAccount preferredByBank = pickPreferredCandidate(sameBankCandidates);
      if (preferredByBank != null) {
        return preferredByBank;
      }
    }

    String normalizedOrg = normalizeText(orgRaw);
    if (!normalizedOrg.isBlank()) {
      List<BankAccount> byOrgCandidates = accounts.stream()
        .filter(item -> normalizeText(item.getLabel()).contains(normalizedOrg))
        .toList();

      BankAccount uniqueByOrg = pickUniqueCandidate(byOrgCandidates);
      if (uniqueByOrg != null) {
        return uniqueByOrg;
      }
    }

    if (!accountIdForMatch.isBlank()) {
      List<BankAccount> byAccountCandidates = accounts.stream()
        .filter(item -> normalizeAccountId(item.getAccountId()).equals(accountIdForMatch))
        .toList();

      BankAccount uniqueByAccount = pickUniqueCandidate(byAccountCandidates);
      if (uniqueByAccount != null) {
        return uniqueByAccount;
      }
    }

    return null;
  }

  private BankAccount resolveByAccountDigits(List<BankAccount> candidates, String accountDigits) {
    if (accountDigits == null || accountDigits.isBlank()) {
      return null;
    }

    List<BankAccount> exactDigits = candidates.stream()
      .filter(item -> normalizeDigits(item.getAccountId()).equals(accountDigits))
      .toList();
    BankAccount exact = pickUniqueCandidate(exactDigits);
    if (exact != null) {
      return exact;
    }

    if (accountDigits.length() < 6) {
      return null;
    }

    String suffix = accountDigits.substring(accountDigits.length() - 6);
    List<BankAccount> suffixCandidates = candidates.stream()
      .filter(item -> normalizeDigits(item.getAccountId()).endsWith(suffix))
      .toList();
    return pickUniqueCandidate(suffixCandidates);
  }

  private BankAccount pickUniqueCandidate(List<BankAccount> candidates) {
    if (candidates == null || candidates.isEmpty()) {
      return null;
    }

    List<BankAccount> active = candidates.stream().filter(BankAccount::isActive).toList();
    if (active.size() == 1) {
      return active.get(0);
    }
    if (active.size() > 1) {
      return null;
    }

    return candidates.size() == 1 ? candidates.get(0) : null;
  }

  private BankAccount pickPreferredCandidate(List<BankAccount> candidates) {
    if (candidates == null || candidates.isEmpty()) {
      return null;
    }

    List<BankAccount> active = candidates.stream().filter(BankAccount::isActive).toList();
    if (active.size() == 1) {
      return active.get(0);
    }
    if (!active.isEmpty()) {
      List<BankAccount> activePrimary = active.stream().filter(BankAccount::isPrimaryIncome).toList();
      if (activePrimary.size() == 1) {
        return activePrimary.get(0);
      }
      return active.get(0);
    }

    List<BankAccount> primary = candidates.stream().filter(BankAccount::isPrimaryIncome).toList();
    if (primary.size() == 1) {
      return primary.get(0);
    }
    return candidates.get(0);
  }

  private String inferBankIdFromOrganization(String orgRaw) {
    String normalizedOrg = normalizeText(orgRaw);
    if (normalizedOrg.isBlank()) {
      return "";
    }
    if (normalizedOrg.contains("NUBANK") || normalizedOrg.contains("NU PAGAMENTOS")) {
      return "260";
    }
    if (normalizedOrg.contains("INTER")) {
      return "77";
    }
    if (normalizedOrg.contains("ITAU")) {
      return "341";
    }
    if (normalizedOrg.contains("SANTANDER")) {
      return "33";
    }
    if (normalizedOrg.contains("BRADESCO")) {
      return "237";
    }
    if (normalizedOrg.contains("CAIXA")) {
      return "104";
    }
    if (normalizedOrg.contains("BANCO DO BRASIL") || normalizedOrg.equals("BB")) {
      return "1";
    }
    if (normalizedOrg.contains("PAGBANK")) {
      return "290";
    }
    return "";
  }

  private String normalizeDigits(String value) {
    if (value == null) {
      return "";
    }
    return value.replaceAll("\\D", "");
  }

  private String normalizeBankId(String value) {
    String digits = normalizeDigits(value);
    if (digits.isBlank()) {
      return "";
    }
    return digits.replaceFirst("^0+(?!$)", "");
  }

  private String normalizeAccountId(String value) {
    if (value == null) {
      return "";
    }
    return value.replaceAll("[^0-9A-Za-z]", "").toUpperCase(Locale.ROOT);
  }

  private boolean isLoanCreditMemo(String normalizedMemo) {
    if (normalizedMemo == null || normalizedMemo.isBlank()) {
      return false;
    }
    return LOAN_CREDIT_MARKERS.stream().anyMatch(normalizedMemo::contains);
  }

  private boolean isInvestmentPurchaseMemo(String normalizedMemo) {
    if (normalizedMemo == null || normalizedMemo.isBlank()) {
      return false;
    }
    return INVESTMENT_PURCHASE_MARKERS.stream().anyMatch(normalizedMemo::contains);
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

  private Set<String> buildItauCardPaymentPairKeys(List<OfxTransaction> transactions) {
    Set<String> internalKeys = new HashSet<>();
    Set<String> detailedKeys = new HashSet<>();

    for (OfxTransaction tx : transactions) {
      if (tx == null || tx.amount().compareTo(BigDecimal.ZERO) >= 0) {
        continue;
      }
      String normalizedMemo = normalizeText(tx.memo());
      String pairKey = buildItauCardPairKey(tx.postedAt(), tx.amount().abs().doubleValue());

      if (isItauCardInternalMemo(normalizedMemo)) {
        internalKeys.add(pairKey);
      } else if (isItauCardDetailedMemo(normalizedMemo)) {
        detailedKeys.add(pairKey);
      }
    }

    Set<String> pairKeys = new HashSet<>(internalKeys);
    pairKeys.retainAll(detailedKeys);
    return pairKeys;
  }

  private boolean shouldSkipItauCardPairDuplicate(OfxTransaction tx, Set<String> pairKeys) {
    if (tx == null || tx.amount().compareTo(BigDecimal.ZERO) >= 0) {
      return false;
    }
    String normalizedMemo = normalizeText(tx.memo());
    if (!isItauCardInternalMemo(normalizedMemo)) {
      return false;
    }

    String pairKey = buildItauCardPairKey(tx.postedAt(), tx.amount().abs().doubleValue());
    return pairKeys.contains(pairKey);
  }

  private boolean isItauCardInternalMemo(String normalizedMemo) {
    return ITAU_CARD_PAYMENT_INTERNAL_MEMO.equals(normalizedMemo);
  }

  private boolean isItauCardDetailedMemo(String normalizedMemo) {
    return normalizedMemo.startsWith(ITAU_CARD_PAYMENT_DETAILED_PREFIX + " ");
  }

  private String buildItauCardPairKey(LocalDate postedAt, double amount) {
    return postedAt + "|" + String.format(Locale.US, "%.2f", amount);
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

  private static final class GroupAccumulator {
    private final String patternKey;
    private String sampleMemo;
    private int totalCount;
    private int creditCount;
    private int debitCount;
    private int ignoredCount;
    private int likelyInternalCount;
    private int itauPairCandidateCount;
    private double totalCreditAmount;
    private double totalDebitAmount;
    private final Set<Integer> years = new TreeSet<>();
    private final Set<String> yearMonths = new TreeSet<>();

    GroupAccumulator(String patternKey) {
      this.patternKey = patternKey;
    }

    void register(
      String memo,
      double amount,
      boolean ignoredByMarker,
      boolean likelyInternalTransfer,
      boolean itauPairDuplicateCandidate,
      int year,
      String yearMonth
    ) {
      if (this.sampleMemo == null || this.sampleMemo.isBlank()) {
        this.sampleMemo = memo;
      }

      this.totalCount += 1;
      if (amount >= 0) {
        this.creditCount += 1;
        this.totalCreditAmount += amount;
      } else {
        this.debitCount += 1;
        this.totalDebitAmount += Math.abs(amount);
      }

      if (ignoredByMarker) {
        this.ignoredCount += 1;
      }
      if (likelyInternalTransfer) {
        this.likelyInternalCount += 1;
      }
      if (itauPairDuplicateCandidate) {
        this.itauPairCandidateCount += 1;
      }

      this.years.add(year);
      this.yearMonths.add(yearMonth);
    }

    OfxAnalysisGroupResponse toResponse() {
      return new OfxAnalysisGroupResponse(
        this.patternKey,
        this.sampleMemo == null ? "Movimentacao OFX" : this.sampleMemo,
        this.totalCount,
        this.creditCount,
        this.debitCount,
        this.ignoredCount,
        this.likelyInternalCount,
        this.itauPairCandidateCount,
        this.totalCreditAmount,
        this.totalDebitAmount,
        new ArrayList<>(this.years),
        new ArrayList<>(this.yearMonths)
      );
    }
  }
}
