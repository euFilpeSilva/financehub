package com.financehub.backend.modules.incomes.api;

import com.financehub.backend.modules.incomes.api.dto.IncomeRequest;
import com.financehub.backend.modules.incomes.api.dto.IncomeResponse;
import com.financehub.backend.modules.incomes.application.IncomeService;
import com.financehub.backend.modules.incomes.domain.Income;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/incomes")
public class IncomeController {
  private final IncomeService service;

  public IncomeController(IncomeService service) {
    this.service = service;
  }

  @GetMapping
  public List<IncomeResponse> list() {
    return service.listAll().stream().map(this::toResponse).toList();
  }

  @PostMapping
  public IncomeResponse create(@Valid @RequestBody IncomeRequest request) {
    return toResponse(service.create(request));
  }

  @PutMapping("/{id}")
  public IncomeResponse update(@PathVariable String id, @Valid @RequestBody IncomeRequest request) {
    return toResponse(service.update(id, request));
  }

  @DeleteMapping("/{id}")
  public void delete(@PathVariable String id) {
    service.delete(id);
  }

  private IncomeResponse toResponse(Income income) {
    return new IncomeResponse(
      income.getId(),
      income.getSource(),
      income.getCategory(),
      income.getAmount(),
      income.getReceivedAt(),
      income.isRecurring(),
      income.isInternalTransfer(),
      income.getBankAccountId()
    );
  }
}
