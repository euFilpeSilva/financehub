package com.financehub.backend.modules.transfers.api;

import com.financehub.backend.modules.transfers.api.dto.InternalTransferLinkRequest;
import com.financehub.backend.modules.transfers.api.dto.InternalTransferDetectionRequest;
import com.financehub.backend.modules.transfers.api.dto.InternalTransferSuggestionResponse;
import com.financehub.backend.modules.transfers.application.InternalTransferService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
