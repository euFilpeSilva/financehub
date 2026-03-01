package com.financehub.backend.modules.statements.api;

import com.financehub.backend.modules.statements.application.OfxExportService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/statements")
public class StatementExportController {
  private final OfxExportService ofxExportService;

  public StatementExportController(OfxExportService ofxExportService) {
    this.ofxExportService = ofxExportService;
  }

  @GetMapping("/export/ofx")
  public ResponseEntity<byte[]> exportOfx(
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
    @RequestParam(defaultValue = "Finance Hub") String bankOrg,
    @RequestParam(defaultValue = "999") String fid,
    @RequestParam(defaultValue = "9999") String bankId,
    @RequestParam(defaultValue = "0001") String branchId,
    @RequestParam(defaultValue = "000000-0") String accountId,
    @RequestParam(defaultValue = "BRL") String currency
  ) {
    String ofx = ofxExportService.exportOfx(startDate, endDate, bankOrg, fid, bankId, branchId, accountId, currency);
    String fileName = "extrato_" + startDate.format(DateTimeFormatter.BASIC_ISO_DATE)
      + "_" + endDate.format(DateTimeFormatter.BASIC_ISO_DATE) + ".ofx";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/x-ofx; charset=UTF-8"));
    headers.setContentDisposition(ContentDisposition.attachment().filename(fileName).build());

    return ResponseEntity.ok()
      .headers(headers)
      .body(ofx.getBytes(StandardCharsets.UTF_8));
  }
}

