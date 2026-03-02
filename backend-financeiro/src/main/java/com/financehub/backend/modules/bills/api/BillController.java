package com.financehub.backend.modules.bills.api;

import com.financehub.backend.modules.bills.api.dto.BillRequest;
import com.financehub.backend.modules.bills.api.dto.BillResponse;
import com.financehub.backend.modules.bills.application.BillService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bills")
public class BillController {
  private final BillService service;

  public BillController(BillService service) {
    this.service = service;
  }

  @GetMapping
  public List<BillResponse> list() {
    return service.listAll().stream().map(this::toResponse).toList();
  }

  @PostMapping
  public BillResponse create(@Valid @RequestBody BillRequest request) {
    return toResponse(service.create(request));
  }

  @PutMapping("/{id}")
  public BillResponse update(@PathVariable String id, @Valid @RequestBody BillRequest request) {
    return toResponse(service.update(id, request));
  }

  @DeleteMapping("/{id}")
  public void delete(@PathVariable String id) {
    service.delete(id);
  }

  @PostMapping("/recurring")
  public List<BillResponse> createRecurring(@RequestParam String month) {
    return service.createRecurringForMonth(month).stream().map(this::toResponse).toList();
  }

  private BillResponse toResponse(com.financehub.backend.modules.bills.domain.Bill bill) {
    return new BillResponse(
      bill.getId(),
      bill.getDescription(),
      bill.getCategory(),
      bill.getAmount(),
      bill.getDueDate(),
      bill.isRecurring(),
      bill.isPaid(),
      bill.isInternalTransfer(),
      bill.getBankAccountId()
    );
  }
}
