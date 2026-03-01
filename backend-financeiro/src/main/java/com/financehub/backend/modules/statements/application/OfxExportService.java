package com.financehub.backend.modules.statements.application;

import com.financehub.backend.modules.bills.application.BillService;
import com.financehub.backend.modules.incomes.application.IncomeService;
import com.financehub.backend.shared.application.port.AuditPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OfxExportService {
  private static final ZoneId BRAZIL_ZONE = ZoneId.of("America/Sao_Paulo");
  private static final DateTimeFormatter OFX_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

  private final BillService billService;
  private final IncomeService incomeService;
  private final AuditPort auditPort;

  public OfxExportService(BillService billService, IncomeService incomeService, AuditPort auditPort) {
    this.billService = billService;
    this.incomeService = incomeService;
    this.auditPort = auditPort;
  }

  public String exportOfx(
    LocalDate startDate,
    LocalDate endDate,
    String bankOrg,
    String fid,
    String bankId,
    String branchId,
    String accountId,
    String currency
  ) {
    if (startDate.isAfter(endDate)) {
      throw new IllegalArgumentException("Data inicial nao pode ser maior que data final.");
    }

    List<OfxTransaction> transactions = new ArrayList<>();

    billService.listAll().stream()
      .filter(item -> !item.getDueDate().isBefore(startDate) && !item.getDueDate().isAfter(endDate))
      .forEach(item -> transactions.add(new OfxTransaction(
        item.getDueDate(),
        BigDecimal.valueOf(item.getAmount()).abs().negate(),
        "bill-" + item.getId(),
        sanitizeMemo(item.getDescription()),
        "DEBIT"
      )));

    incomeService.listAll().stream()
      .filter(item -> !item.getReceivedAt().isBefore(startDate) && !item.getReceivedAt().isAfter(endDate))
      .forEach(item -> transactions.add(new OfxTransaction(
        item.getReceivedAt(),
        BigDecimal.valueOf(item.getAmount()).abs(),
        "income-" + item.getId(),
        sanitizeMemo(item.getSource()),
        "CREDIT"
      )));

    transactions.sort(Comparator.comparing(OfxTransaction::postedAt).thenComparing(OfxTransaction::fitId));

    BigDecimal ledgerBalance = transactions.stream()
      .map(OfxTransaction::amount)
      .reduce(BigDecimal.ZERO, BigDecimal::add);

    String ofx = buildOfxDocument(
      startDate,
      endDate,
      bankOrg,
      fid,
      bankId,
      branchId,
      accountId,
      currency,
      transactions,
      ledgerBalance
    );

    auditPort.record(
      "statement",
      "ofx-export",
      "export",
      "Exportacao OFX gerada para periodo " + startDate + " a " + endDate,
      ledgerBalance.doubleValue()
    );

    return ofx;
  }

  private String buildOfxDocument(
    LocalDate startDate,
    LocalDate endDate,
    String bankOrg,
    String fid,
    String bankId,
    String branchId,
    String accountId,
    String currency,
    List<OfxTransaction> transactions,
    BigDecimal ledgerBalance
  ) {
    StringBuilder builder = new StringBuilder(4096);

    builder.append("OFXHEADER:100\n")
      .append("DATA:OFXSGML\n")
      .append("VERSION:102\n")
      .append("SECURITY:NONE\n")
      .append("ENCODING:UTF-8\n")
      .append("CHARSET:NONE\n")
      .append("COMPRESSION:NONE\n")
      .append("OLDFILEUID:NONE\n")
      .append("NEWFILEUID:NONE\n")
      .append("<OFX>\n")
      .append("<SIGNONMSGSRSV1>\n")
      .append("<SONRS>\n")
      .append("<STATUS>\n")
      .append("<CODE>0</CODE>\n")
      .append("<SEVERITY>INFO</SEVERITY>\n")
      .append("</STATUS>\n")
      .append("<DTSERVER>").append(formatInstant(Instant.now())).append("</DTSERVER>\n")
      .append("<LANGUAGE>POR</LANGUAGE>\n")
      .append("<FI>\n")
      .append("<ORG>").append(sanitizeMemo(bankOrg)).append("</ORG>\n")
      .append("<FID>").append(sanitizeMemo(fid)).append("</FID>\n")
      .append("</FI>\n")
      .append("</SONRS>\n")
      .append("</SIGNONMSGSRSV1>\n")
      .append("<BANKMSGSRSV1>\n")
      .append("<STMTTRNRS>\n")
      .append("<TRNUID>1</TRNUID>\n")
      .append("<STATUS>\n")
      .append("<CODE>0</CODE>\n")
      .append("<SEVERITY>INFO</SEVERITY>\n")
      .append("</STATUS>\n")
      .append("<STMTRS>\n")
      .append("<CURDEF>").append(sanitizeMemo(currency)).append("</CURDEF>\n")
      .append("<BANKACCTFROM>\n")
      .append("<BANKID>").append(sanitizeMemo(bankId)).append("</BANKID>\n")
      .append("<BRANCHID>").append(sanitizeMemo(branchId)).append("</BRANCHID>\n")
      .append("<ACCTID>").append(sanitizeMemo(accountId)).append("</ACCTID>\n")
      .append("<ACCTTYPE>CHECKING</ACCTTYPE>\n")
      .append("</BANKACCTFROM>\n")
      .append("<BANKTRANLIST>\n")
      .append("<DTSTART>").append(formatLocalDate(startDate)).append("[-3:BRT]</DTSTART>\n")
      .append("<DTEND>").append(formatLocalDate(endDate)).append("[-3:BRT]</DTEND>\n");

    for (OfxTransaction transaction : transactions) {
      builder.append("<STMTTRN>\n")
        .append("<TRNTYPE>").append(transaction.trnType()).append("</TRNTYPE>\n")
        .append("<DTPOSTED>").append(formatLocalDate(transaction.postedAt())).append("[-3:BRT]</DTPOSTED>\n")
        .append("<TRNAMT>").append(formatAmount(transaction.amount())).append("</TRNAMT>\n")
        .append("<FITID>").append(transaction.fitId()).append("</FITID>\n")
        .append("<MEMO>").append(transaction.memo()).append("</MEMO>\n")
        .append("</STMTTRN>\n");
    }

    builder.append("</BANKTRANLIST>\n")
      .append("<LEDGERBAL>\n")
      .append("<BALAMT>").append(formatAmount(ledgerBalance)).append("</BALAMT>\n")
      .append("<DTASOF>").append(formatLocalDate(endDate)).append("[-3:BRT]</DTASOF>\n")
      .append("</LEDGERBAL>\n")
      .append("</STMTRS>\n")
      .append("</STMTTRNRS>\n")
      .append("</BANKMSGSRSV1>\n")
      .append("</OFX>\n");

    return builder.toString();
  }

  private String sanitizeMemo(String value) {
    if (value == null || value.isBlank()) {
      return "NA";
    }
    String normalized = value
      .replace('\n', ' ')
      .replace('\r', ' ')
      .replace('<', ' ')
      .replace('>', ' ')
      .replace('&', ' ')
      .trim()
      .replaceAll("\\s{2,}", " ");
    if (normalized.length() > 255) {
      return normalized.substring(0, 255);
    }
    return normalized;
  }

  private String formatAmount(BigDecimal value) {
    return value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
  }

  private String formatLocalDate(LocalDate date) {
    return date.format(DateTimeFormatter.BASIC_ISO_DATE) + "000000";
  }

  private String formatInstant(Instant instant) {
    ZonedDateTime zonedDateTime = instant.atZone(BRAZIL_ZONE);
    return OFX_DATE_TIME.format(zonedDateTime) + "[-3:BRT]";
  }

  private record OfxTransaction(
    LocalDate postedAt,
    BigDecimal amount,
    String fitId,
    String memo,
    String trnType
  ) {
  }
}

