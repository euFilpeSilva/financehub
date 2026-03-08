package com.financehub.backend.modules.statements.api;

import com.financehub.backend.modules.statements.api.dto.OfxImportResponse;
import com.financehub.backend.modules.statements.api.dto.OfxAnalysisResponse;
import com.financehub.backend.modules.statements.api.dto.ImportedStatementYearCleanupRequest;
import com.financehub.backend.modules.statements.api.dto.ImportedStatementYearCleanupResponse;
import com.financehub.backend.modules.statements.application.ImportedStatementCleanupService;
import com.financehub.backend.modules.statements.application.OfxImportService;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/v1/statements")
public class StatementImportController {
  private final OfxImportService service;
  private final ImportedStatementCleanupService importedStatementCleanupService;

  public StatementImportController(
    OfxImportService service,
    ImportedStatementCleanupService importedStatementCleanupService
  ) {
    this.service = service;
    this.importedStatementCleanupService = importedStatementCleanupService;
  }

  @PostMapping("/import/ofx")
  public OfxImportResponse importOfx(
    @RequestParam("file") MultipartFile file,
    @RequestParam(required = false) String ownerName,
    @RequestParam(required = false) String ownerCpf,
    @RequestParam(defaultValue = "true") boolean applyInternalTransferDetection
  ) {
    return service.importOfx(file, ownerName, ownerCpf, applyInternalTransferDetection);
  }

  @PostMapping("/analyze/ofx")
  public OfxAnalysisResponse analyzeOfx(
    @RequestParam("files") List<MultipartFile> files,
    @RequestParam(required = false) String ownerName,
    @RequestParam(required = false) String ownerCpf
  ) {
    return service.analyzeOfx(files, ownerName, ownerCpf);
  }

  @PostMapping("/cleanup/imported-year")
  public ImportedStatementYearCleanupResponse cleanupImportedYear(
    @RequestBody ImportedStatementYearCleanupRequest request
  ) {
    return importedStatementCleanupService.cleanupByYear(request);
  }
}

