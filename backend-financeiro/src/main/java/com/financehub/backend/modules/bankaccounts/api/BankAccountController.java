package com.financehub.backend.modules.bankaccounts.api;

import com.financehub.backend.modules.bankaccounts.api.dto.BankAccountRequest;
import com.financehub.backend.modules.bankaccounts.api.dto.BankAccountResponse;
import com.financehub.backend.modules.bankaccounts.application.BankAccountService;
import com.financehub.backend.modules.bankaccounts.domain.BankAccount;
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
@RequestMapping("/api/v1/bank-accounts")
public class BankAccountController {
  private final BankAccountService service;

  public BankAccountController(BankAccountService service) {
    this.service = service;
  }

  @GetMapping
  public List<BankAccountResponse> list() {
    return service.listAll().stream().map(this::toResponse).toList();
  }

  @PostMapping
  public BankAccountResponse create(@Valid @RequestBody BankAccountRequest request) {
    return toResponse(service.create(request));
  }

  @PutMapping("/{id}")
  public BankAccountResponse update(@PathVariable String id, @Valid @RequestBody BankAccountRequest request) {
    return toResponse(service.update(id, request));
  }

  @DeleteMapping("/{id}")
  public void delete(@PathVariable String id) {
    service.delete(id);
  }

  private BankAccountResponse toResponse(BankAccount bankAccount) {
    return new BankAccountResponse(
      bankAccount.getId(),
      bankAccount.getLabel(),
      bankAccount.getBankId(),
      bankAccount.getBranchId(),
      bankAccount.getAccountId(),
      bankAccount.isPrimaryIncome(),
      bankAccount.isActive()
    );
  }
}
