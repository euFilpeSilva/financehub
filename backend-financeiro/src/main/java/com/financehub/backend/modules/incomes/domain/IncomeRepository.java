package com.financehub.backend.modules.incomes.domain;

import java.util.List;
import java.util.Optional;

public interface IncomeRepository {
  List<Income> findAll();
  Optional<Income> findById(String id);
  Income save(Income income);
  void deleteById(String id);
}
