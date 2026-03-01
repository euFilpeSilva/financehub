package com.financehub.backend.modules.governance.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.financehub.backend.modules.bills.domain.Bill;
import com.financehub.backend.modules.bills.domain.BillRepository;
import com.financehub.backend.modules.governance.domain.TrashItem;
import com.financehub.backend.modules.governance.infrastructure.AuditEventJpaEntity;
import com.financehub.backend.modules.governance.infrastructure.AuditEventSpringDataRepository;
import com.financehub.backend.modules.governance.infrastructure.RetentionSettingsJpaEntity;
import com.financehub.backend.modules.governance.infrastructure.RetentionSettingsSpringDataRepository;
import com.financehub.backend.modules.governance.infrastructure.TrashItemJpaEntity;
import com.financehub.backend.modules.governance.infrastructure.TrashItemSpringDataRepository;
import com.financehub.backend.modules.incomes.domain.Income;
import com.financehub.backend.modules.incomes.domain.IncomeRepository;
import com.financehub.backend.modules.planning.domain.PlanningGoal;
import com.financehub.backend.modules.planning.domain.PlanningGoalRepository;
import com.financehub.backend.modules.spending.domain.SpendingGoal;
import com.financehub.backend.modules.spending.domain.SpendingGoalRepository;
import com.financehub.backend.shared.api.NotFoundException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrashService {
  private static final long SINGLETON_ID = 1L;

  private final TrashItemSpringDataRepository trashRepository;
  private final RetentionSettingsSpringDataRepository retentionSettingsRepository;
  private final BillRepository billRepository;
  private final IncomeRepository incomeRepository;
  private final PlanningGoalRepository planningGoalRepository;
  private final SpendingGoalRepository spendingGoalRepository;
  private final AuditEventSpringDataRepository auditEventRepository;
  private final ObjectMapper objectMapper;

  public TrashService(
    TrashItemSpringDataRepository trashRepository,
    RetentionSettingsSpringDataRepository retentionSettingsRepository,
    BillRepository billRepository,
    IncomeRepository incomeRepository,
    PlanningGoalRepository planningGoalRepository,
    SpendingGoalRepository spendingGoalRepository,
    AuditEventSpringDataRepository auditEventRepository,
    ObjectMapper objectMapper
  ) {
    this.trashRepository = trashRepository;
    this.retentionSettingsRepository = retentionSettingsRepository;
    this.billRepository = billRepository;
    this.incomeRepository = incomeRepository;
    this.planningGoalRepository = planningGoalRepository;
    this.spendingGoalRepository = spendingGoalRepository;
    this.auditEventRepository = auditEventRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  public List<TrashItem> listAll() {
    return trashRepository.findAllByOrderByDeletedAtDesc().stream().map(this::toDomain).toList();
  }

  @Transactional
  public void moveToTrash(String entityType, String entityId, String label, Object payload) {
    String payloadJson;
    try {
      payloadJson = objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("Falha ao serializar item para lixeira.", ex);
    }

    int retentionDays = retentionSettingsRepository.findById(SINGLETON_ID)
      .map(RetentionSettingsJpaEntity::getTrashRetentionDays)
      .orElse(30);

    Instant deletedAt = Instant.now();
    Instant purgeAt = deletedAt.plus(retentionDays, ChronoUnit.DAYS);

    trashRepository.save(new TrashItemJpaEntity(
      UUID.randomUUID().toString(),
      entityType,
      entityId,
      label,
      payloadJson,
      deletedAt,
      purgeAt
    ));
  }

  @Transactional
  public void restore(String trashId) {
    TrashItemJpaEntity item = trashRepository.findById(trashId)
      .orElseThrow(() -> new NotFoundException("Item da lixeira nao encontrado: " + trashId));

    try {
      switch (item.getEntityType()) {
        case "bill" -> billRepository.save(objectMapper.readValue(item.getPayload(), Bill.class));
        case "income" -> incomeRepository.save(objectMapper.readValue(item.getPayload(), Income.class));
        case "planning-goal" -> planningGoalRepository.save(objectMapper.readValue(item.getPayload(), PlanningGoal.class));
        case "spending-goal" -> spendingGoalRepository.save(objectMapper.readValue(item.getPayload(), SpendingGoal.class));
        default -> throw new IllegalArgumentException("Tipo de entidade nao suportado para restauracao: " + item.getEntityType());
      }
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("Falha ao desserializar item da lixeira.", ex);
    }

    trashRepository.deleteById(trashId);
    recordAudit(item.getEntityType(), item.getEntityId(), "restore", item.getLabel() + " restaurado da lixeira");
  }

  @Transactional
  public void purge(String trashId) {
    TrashItemJpaEntity item = trashRepository.findById(trashId)
      .orElseThrow(() -> new NotFoundException("Item da lixeira nao encontrado: " + trashId));
    trashRepository.deleteById(trashId);
    recordAudit(item.getEntityType(), item.getEntityId(), "purge", item.getLabel() + " excluido permanentemente");
  }

  @Transactional
  public int purgeExpired() {
    List<TrashItemJpaEntity> expired = trashRepository.findByPurgeAtBefore(Instant.now());
    for (TrashItemJpaEntity item : expired) {
      trashRepository.deleteById(item.getId());
      recordAudit(item.getEntityType(), item.getEntityId(), "purge", item.getLabel() + " excluido por retencao");
    }
    return expired.size();
  }

  private void recordAudit(String entityType, String entityId, String action, String message) {
    auditEventRepository.save(new AuditEventJpaEntity(
      UUID.randomUUID().toString(),
      entityType,
      entityId,
      action,
      message,
      null,
      Instant.now()
    ));
  }

  private TrashItem toDomain(TrashItemJpaEntity entity) {
    return new TrashItem(
      entity.getId(),
      entity.getEntityType(),
      entity.getEntityId(),
      entity.getLabel(),
      entity.getPayload(),
      entity.getDeletedAt(),
      entity.getPurgeAt()
    );
  }
}
