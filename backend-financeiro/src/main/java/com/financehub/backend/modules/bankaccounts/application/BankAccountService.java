package com.financehub.backend.modules.bankaccounts.application;

import com.financehub.backend.modules.bankaccounts.api.dto.BankAccountRequest;
import com.financehub.backend.modules.bankaccounts.domain.BankAccount;
import com.financehub.backend.modules.bankaccounts.domain.BankAccountRepository;
import com.financehub.backend.shared.api.NotFoundException;
import com.financehub.backend.shared.application.port.AuditPort;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class BankAccountService {
  private final BankAccountRepository repository;
  private final AuditPort auditPort;

  public BankAccountService(BankAccountRepository repository, AuditPort auditPort) {
    this.repository = repository;
    this.auditPort = auditPort;
  }

  public List<BankAccount> listAll() {
    return repository.findAll().stream()
      .sorted(Comparator.comparing(BankAccount::getLabel, String.CASE_INSENSITIVE_ORDER))
      .toList();
  }

  public BankAccount create(BankAccountRequest request) {
    BankAccount bankAccount = new BankAccount(
      UUID.randomUUID().toString(),
      normalizeLabel(request.label()),
      normalizeDigits(request.bankId()),
      normalizeBranch(request.branchId()),
      normalizeAccountId(request.accountId()),
      request.primaryIncome(),
      request.active()
    );

    repository.save(bankAccount);
    auditPort.record("bank-account", bankAccount.getId(), "create", bankAccount.getLabel() + " criada", null);
    return bankAccount;
  }

  public BankAccount update(String id, BankAccountRequest request) {
    BankAccount bankAccount = repository.findById(id)
      .orElseThrow(() -> new NotFoundException("Conta bancaria nao encontrada: " + id));

    bankAccount.setLabel(normalizeLabel(request.label()));
    bankAccount.setBankId(normalizeDigits(request.bankId()));
    bankAccount.setBranchId(normalizeBranch(request.branchId()));
    bankAccount.setAccountId(normalizeAccountId(request.accountId()));
    bankAccount.setPrimaryIncome(request.primaryIncome());
    bankAccount.setActive(request.active());

    repository.save(bankAccount);
    auditPort.record("bank-account", bankAccount.getId(), "update", bankAccount.getLabel() + " atualizada", null);
    return bankAccount;
  }

  public void delete(String id) {
    BankAccount bankAccount = repository.findById(id)
      .orElseThrow(() -> new NotFoundException("Conta bancaria nao encontrada: " + id));

    repository.deleteById(id);
    auditPort.record("bank-account", bankAccount.getId(), "delete", bankAccount.getLabel() + " removida", null);
  }

  public Optional<BankAccount> findByOfxIdentifiers(String bankId, String accountId) {
    if (bankId == null || accountId == null) {
      return Optional.empty();
    }

    String normalizedBankId = normalizeDigits(bankId);
    String normalizedAccountId = normalizeAccountId(accountId);
    if (normalizedBankId.isBlank() || normalizedAccountId.isBlank()) {
      return Optional.empty();
    }

    return repository.findAll().stream()
      .filter(BankAccount::isActive)
      .filter(item -> normalizeDigits(item.getBankId()).equals(normalizedBankId))
      .filter(item -> normalizeAccountId(item.getAccountId()).equals(normalizedAccountId))
      .findFirst();
  }

  public BankAccount getById(String id) {
    return repository.findById(id)
      .orElseThrow(() -> new NotFoundException("Conta bancaria nao encontrada: " + id));
  }

  public boolean hasActiveEligibleIncomeAccount() {
    return repository.findAll().stream()
      .anyMatch(account -> account.isActive() && account.isPrimaryIncome());
  }

  private String normalizeLabel(String value) {
    return value == null ? "" : value.trim();
  }

  private String normalizeDigits(String value) {
    if (value == null) {
      return "";
    }
    return value.replaceAll("\\D", "");
  }

  private String normalizeBranch(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim().replaceAll("\\s+", " ");
    return normalized.isBlank() ? null : normalized;
  }

  private String normalizeAccountId(String value) {
    if (value == null) {
      return "";
    }
    return value.replaceAll("[^0-9A-Za-z]", "").toUpperCase();
  }
}
