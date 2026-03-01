package com.financehub.backend.modules.statements.api;

import com.financehub.backend.modules.statements.api.dto.OfxImportResponse;
import com.financehub.backend.modules.statements.application.OfxImportService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/statements")
public class StatementImportController {
  private final OfxImportService service;

  public StatementImportController(OfxImportService service) {
    this.service = service;
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
}

