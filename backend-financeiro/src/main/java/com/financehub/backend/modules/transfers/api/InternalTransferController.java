package com.financehub.backend.modules.transfers.api;

import com.financehub.backend.modules.transfers.api.dto.InternalTransferLinkRequest;
import com.financehub.backend.modules.transfers.api.dto.InternalTransferDetectionRequest;
import com.financehub.backend.modules.transfers.api.dto.InternalTransferReclassificationRequest;
import com.financehub.backend.modules.transfers.api.dto.InternalTransferReclassificationResponse;
import com.financehub.backend.modules.transfers.api.dto.ImportedDuplicateCleanupResponse;
import com.financehub.backend.modules.transfers.api.dto.InternalTransferSuggestionResponse;
import com.financehub.backend.modules.transfers.application.InternalTransferService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
public class InternalTransferController {
  private final InternalTransferService service;

  public InternalTransferController(InternalTransferService service) {
    this.service = service;
  }

  @PostMapping("/internal/link")
  public ResponseEntity<Void> linkInternalTransfer(@Valid @RequestBody InternalTransferLinkRequest request) {
    service.linkInternalTransfer(request.billId(), request.incomeId());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/internal/detect")
  public List<InternalTransferSuggestionResponse> detectInternalTransfers(
    @Valid @RequestBody InternalTransferDetectionRequest request
  ) {
    int toleranceDays = request.dateToleranceDays() == 0 ? 1 : request.dateToleranceDays();
    return service.detectInternalTransfers(
      request.ownerName(),
      request.ownerCpf(),
      toleranceDays,
      request.autoApply()
    );
  }

  @PostMapping("/internal/reclassify-legacy")
  public InternalTransferReclassificationResponse reclassifyLegacyTransfers(
    @RequestBody(required = false) InternalTransferReclassificationRequest request
  ) {
    String ownerName = request == null ? null : request.ownerName();
    String ownerCpf = request == null ? null : request.ownerCpf();
    boolean includePicpay = request == null || request.includePicpay() == null || request.includePicpay();
    boolean includeLegacyBroker = request == null || request.includeLegacyBroker() == null || request.includeLegacyBroker();
    boolean includeInvestmentPurchases = request == null || request.includeInvestmentPurchases() == null || request.includeInvestmentPurchases();
    return service.reclassifyLegacyTransfers(ownerName, ownerCpf, includePicpay, includeLegacyBroker, includeInvestmentPurchases);
  }

  @PostMapping("/internal/cleanup-imported-duplicates")
  public ImportedDuplicateCleanupResponse cleanupImportedDuplicates(
    @RequestParam(defaultValue = "true") boolean dryRun
  ) {
    return service.cleanupImportedExpenseDuplicates(dryRun);
  }
}
