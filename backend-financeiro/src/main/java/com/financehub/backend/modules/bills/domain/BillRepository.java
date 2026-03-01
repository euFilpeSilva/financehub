package com.financehub.backend.modules.bills.domain;

import java.util.List;
import java.util.Optional;

public interface BillRepository {
  List<Bill> findAll();
  Optional<Bill> findById(String id);
  Bill save(Bill bill);
  void deleteById(String id);
}
