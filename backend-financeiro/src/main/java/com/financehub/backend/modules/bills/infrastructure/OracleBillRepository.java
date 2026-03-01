package com.financehub.backend.modules.bills.infrastructure;

import com.financehub.backend.modules.bills.domain.Bill;
import com.financehub.backend.modules.bills.domain.BillRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

@Repository
@Primary
public class OracleBillRepository implements BillRepository {
  private final BillSpringDataRepository jpaRepository;

  public OracleBillRepository(BillSpringDataRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public List<Bill> findAll() {
    return jpaRepository.findAll().stream().map(this::toDomain).toList();
  }

  @Override
  public Optional<Bill> findById(String id) {
    return jpaRepository.findById(id).map(this::toDomain);
  }

  @Override
  public Bill save(Bill bill) {
    BillJpaEntity saved = jpaRepository.save(toEntity(bill));
    return toDomain(saved);
  }

  @Override
  public void deleteById(String id) {
    jpaRepository.deleteById(id);
  }

  private Bill toDomain(BillJpaEntity entity) {
    return new Bill(
      entity.getId(),
      entity.getDescription(),
      entity.getCategory(),
      entity.getAmount().doubleValue(),
      entity.getDueDate(),
      entity.isRecurring(),
      entity.isPaid(),
      entity.isInternalTransfer(),
      entity.getBankAccountId()
    );
  }

  private BillJpaEntity toEntity(Bill bill) {
    return new BillJpaEntity(
      bill.getId(),
      bill.getDescription(),
      bill.getCategory(),
      BigDecimal.valueOf(bill.getAmount()),
      bill.getDueDate(),
      bill.isRecurring(),
      bill.isPaid(),
      bill.isInternalTransfer(),
      bill.getBankAccountId()
    );
  }
}
