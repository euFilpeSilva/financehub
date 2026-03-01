package com.financehub.backend.modules.bankaccounts.domain;

import java.util.List;
import java.util.Optional;

public interface BankAccountRepository {
  List<BankAccount> findAll();
  Optional<BankAccount> findById(String id);
  BankAccount save(BankAccount bankAccount);
  void deleteById(String id);
}
