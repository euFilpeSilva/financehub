package com.financehub.backend.modules.governance.application;

import com.financehub.backend.modules.bills.infrastructure.BillSpringDataRepository;
import com.financehub.backend.modules.bankaccounts.infrastructure.BankAccountSpringDataRepository;
import com.financehub.backend.modules.governance.api.dto.AppPreferencesRequest;
import com.financehub.backend.modules.governance.api.dto.RetentionSettingsRequest;
import com.financehub.backend.modules.governance.domain.AppPreferences;
import com.financehub.backend.modules.governance.domain.AuditEvent;
import com.financehub.backend.modules.governance.domain.RetentionSettings;
import com.financehub.backend.modules.governance.infrastructure.AppPreferencesJpaEntity;
import com.financehub.backend.modules.governance.infrastructure.AppPreferencesSpringDataRepository;
import com.financehub.backend.modules.governance.infrastructure.AuditEventJpaEntity;
import com.financehub.backend.modules.governance.infrastructure.AuditEventSpringDataRepository;
import com.financehub.backend.modules.governance.infrastructure.RetentionSettingsJpaEntity;
import com.financehub.backend.modules.governance.infrastructure.RetentionSettingsSpringDataRepository;
import com.financehub.backend.modules.governance.infrastructure.TrashItemSpringDataRepository;
import com.financehub.backend.modules.incomes.infrastructure.IncomeSpringDataRepository;
import com.financehub.backend.modules.planning.infrastructure.PlanningGoalSpringDataRepository;
import com.financehub.backend.modules.spending.infrastructure.SpendingGoalSpringDataRepository;
import com.financehub.backend.shared.application.port.AuditPort;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GovernanceService implements AuditPort {
  private static final long SINGLETON_ID = 1L;
  private static final List<String> DEFAULT_BILL_CATEGORIES = List.of(
    "Moradia",
    "Alimentacao",
    "Utilidades",
    "Saude",
    "Transporte",
    "Educacao",
    "Lazer",
    "Outros"
  );
  private static final List<String> DEFAULT_INCOME_CATEGORIES = List.of(
    "Trabalho",
    "Extra",
    "Investimentos",
    "Reembolso",
    "Outros"
  );

  private final AuditEventSpringDataRepository auditEventRepository;
  private final RetentionSettingsSpringDataRepository retentionSettingsRepository;
  private final AppPreferencesSpringDataRepository appPreferencesRepository;
  private final TrashItemSpringDataRepository trashItemRepository;
  private final BillSpringDataRepository billRepository;
  private final BankAccountSpringDataRepository bankAccountRepository;
  private final IncomeSpringDataRepository incomeRepository;
  private final PlanningGoalSpringDataRepository planningGoalRepository;
  private final SpendingGoalSpringDataRepository spendingGoalRepository;
  private final TrashService trashService;

  public GovernanceService(
    AuditEventSpringDataRepository auditEventRepository,
    RetentionSettingsSpringDataRepository retentionSettingsRepository,
    AppPreferencesSpringDataRepository appPreferencesRepository,
    TrashItemSpringDataRepository trashItemRepository,
    BillSpringDataRepository billRepository,
    BankAccountSpringDataRepository bankAccountRepository,
    IncomeSpringDataRepository incomeRepository,
    PlanningGoalSpringDataRepository planningGoalRepository,
    SpendingGoalSpringDataRepository spendingGoalRepository,
    TrashService trashService
  ) {
    this.auditEventRepository = auditEventRepository;
    this.retentionSettingsRepository = retentionSettingsRepository;
    this.appPreferencesRepository = appPreferencesRepository;
    this.trashItemRepository = trashItemRepository;
    this.billRepository = billRepository;
    this.bankAccountRepository = bankAccountRepository;
    this.incomeRepository = incomeRepository;
    this.planningGoalRepository = planningGoalRepository;
    this.spendingGoalRepository = spendingGoalRepository;
    this.trashService = trashService;
  }

  @Transactional(readOnly = true)
  public List<AuditEvent> listAuditEvents() {
    return auditEventRepository.findAllByOrderByTimestampDesc().stream().map(this::toDomain).toList();
  }

  @Transactional(readOnly = true)
  public RetentionSettings getRetentionSettings() {
    RetentionSettingsJpaEntity entity = retentionSettingsRepository.findById(SINGLETON_ID)
      .orElseGet(() -> retentionSettingsRepository.save(new RetentionSettingsJpaEntity(SINGLETON_ID, 30, 180)));
    return new RetentionSettings(entity.getTrashRetentionDays(), entity.getAuditRetentionDays());
  }

  @Transactional
  public RetentionSettings updateRetentionSettings(RetentionSettingsRequest request) {
    RetentionSettings previous = getRetentionSettings();
    RetentionSettings current = new RetentionSettings(request.trashRetentionDays(), request.auditRetentionDays());

    retentionSettingsRepository.save(new RetentionSettingsJpaEntity(
      SINGLETON_ID,
      current.trashRetentionDays(),
      current.auditRetentionDays()
    ));

    String message = buildRetentionAuditMessage(previous, current);
    if (message != null) {
      record("settings", "settings", "update", message, null);
    }
    return current;
  }

  @Transactional(readOnly = true)
  public AppPreferences getAppPreferences() {
    AppPreferencesJpaEntity entity = appPreferencesRepository.findById(SINGLETON_ID).orElseGet(() ->
      appPreferencesRepository.save(new AppPreferencesJpaEntity(
        SINGLETON_ID,
        "Moradia",
        false,
        5,
        "Trabalho",
        false,
        1,
        "month",
        1,
        toCategoryStorage(DEFAULT_BILL_CATEGORIES),
        toCategoryStorage(DEFAULT_INCOME_CATEGORIES)
      ))
    );
    return toDomain(entity);
  }

  @Transactional
  public AppPreferences updateAppPreferences(AppPreferencesRequest request) {
    AppPreferences previous = getAppPreferences();

    List<String> billCategories = normalizeCategories(request.billCategories(), DEFAULT_BILL_CATEGORIES);
    List<String> incomeCategories = normalizeCategories(request.incomeCategories(), DEFAULT_INCOME_CATEGORIES);
    String defaultBillCategory = resolveDefaultCategory(request.defaultBillCategory(), billCategories);
    String defaultIncomeCategory = resolveDefaultCategory(request.defaultIncomeCategory(), incomeCategories);

    AppPreferencesJpaEntity saved = appPreferencesRepository.save(new AppPreferencesJpaEntity(
      SINGLETON_ID,
      defaultBillCategory,
      request.defaultBillRecurring(),
      request.defaultBillDueDay(),
      defaultIncomeCategory,
      request.defaultIncomeRecurring(),
      request.defaultIncomeReceivedDay(),
      request.defaultDashboardMode(),
      request.defaultDashboardMonthComparisonOffset(),
      toCategoryStorage(billCategories),
      toCategoryStorage(incomeCategories)
    ));

    AppPreferences updated = toDomain(saved);
    String message = buildPreferencesAuditMessage(previous, updated);
    if (message != null) {
      record("preferences", "preferences", "update", message, null);
    }
    return updated;
  }

  @Transactional
  public void runRetentionCleanup() {
    RetentionSettings settings = getRetentionSettings();
    trashService.purgeExpired();
    Instant threshold = Instant.now().minus(settings.auditRetentionDays(), ChronoUnit.DAYS);
    auditEventRepository.deleteByTimestampBefore(threshold);
  }

  @Transactional
  public void emergencyResetAllData() {
    trashItemRepository.deleteAllInBatch();
    spendingGoalRepository.deleteAllInBatch();
    planningGoalRepository.deleteAllInBatch();
    incomeRepository.deleteAllInBatch();
    billRepository.deleteAllInBatch();
    bankAccountRepository.deleteAllInBatch();
    appPreferencesRepository.deleteAllInBatch();
    retentionSettingsRepository.deleteAllInBatch();
    auditEventRepository.deleteAllInBatch();
  }

  @Override
  @Transactional
  public void record(String entityType, String entityId, String action, String message, Double amount) {
    auditEventRepository.save(new AuditEventJpaEntity(
      UUID.randomUUID().toString(),
      entityType,
      entityId,
      action,
      message,
      amount == null ? null : BigDecimal.valueOf(amount),
      Instant.now()
    ));
  }

  private AuditEvent toDomain(AuditEventJpaEntity entity) {
    return new AuditEvent(
      entity.getId(),
      entity.getEntityType(),
      entity.getEntityId(),
      entity.getAction(),
      entity.getMessage(),
      entity.getAmount() == null ? null : entity.getAmount().doubleValue(),
      entity.getTimestamp()
    );
  }

  private AppPreferences toDomain(AppPreferencesJpaEntity entity) {
    List<String> billCategories = parseCategoryStorage(entity.getBillCategories(), DEFAULT_BILL_CATEGORIES);
    List<String> incomeCategories = parseCategoryStorage(entity.getIncomeCategories(), DEFAULT_INCOME_CATEGORIES);

    return new AppPreferences(
      resolveDefaultCategory(entity.getDefaultBillCategory(), billCategories),
      entity.isDefaultBillRecurring(),
      entity.getDefaultBillDueDay(),
      resolveDefaultCategory(entity.getDefaultIncomeCategory(), incomeCategories),
      entity.isDefaultIncomeRecurring(),
      entity.getDefaultIncomeReceivedDay(),
      entity.getDefaultDashboardMode(),
      entity.getDefaultDashboardMonthComparisonOffset(),
      billCategories,
      incomeCategories
    );
  }

  private List<String> parseCategoryStorage(String value, List<String> fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }

    List<String> parsed = normalizeCategories(List.of(value.split("\\|")), fallback);
    return parsed.isEmpty() ? fallback : parsed;
  }

  private String toCategoryStorage(List<String> categories) {
    return String.join("|", categories);
  }

  private List<String> normalizeCategories(List<String> categories, List<String> fallback) {
    if (categories == null || categories.isEmpty()) {
      return fallback;
    }

    Map<String, String> unique = new LinkedHashMap<>();
    for (String raw : categories) {
      if (raw == null) {
        continue;
      }
      String trimmed = raw.trim();
      if (trimmed.isEmpty() || trimmed.length() > 120) {
        continue;
      }
      String key = trimmed.toLowerCase(Locale.ROOT);
      unique.putIfAbsent(key, trimmed);
    }

    if (unique.isEmpty()) {
      return fallback;
    }
    return new ArrayList<>(unique.values());
  }

  private String resolveDefaultCategory(String requestedDefault, List<String> categories) {
    if (requestedDefault == null) {
      return categories.get(0);
    }

    String normalizedRequested = requestedDefault.trim();
    for (String category : categories) {
      if (category.equalsIgnoreCase(normalizedRequested)) {
        return category;
      }
    }
    return categories.get(0);
  }

  private String buildRetentionAuditMessage(RetentionSettings previous, RetentionSettings updated) {
    List<String> changes = new ArrayList<>();
    if (previous.trashRetentionDays() != updated.trashRetentionDays()) {
      changes.add("lixeira " + previous.trashRetentionDays() + "d -> " + updated.trashRetentionDays() + "d");
    }
    if (previous.auditRetentionDays() != updated.auditRetentionDays()) {
      changes.add("auditoria " + previous.auditRetentionDays() + "d -> " + updated.auditRetentionDays() + "d");
    }
    if (changes.isEmpty()) {
      return null;
    }
    return "Configuracoes de retencao alteradas: " + String.join("; ", changes);
  }

  private String buildPreferencesAuditMessage(AppPreferences previous, AppPreferences updated) {
    List<String> changes = new ArrayList<>();
    addChange(changes, "categoria padrao de saida", previous.defaultBillCategory(), updated.defaultBillCategory());
    addChange(changes, "saida recorrente padrao", previous.defaultBillRecurring(), updated.defaultBillRecurring());
    addChange(changes, "dia padrao de vencimento", previous.defaultBillDueDay(), updated.defaultBillDueDay());
    addChange(changes, "categoria padrao de entrada", previous.defaultIncomeCategory(), updated.defaultIncomeCategory());
    addChange(changes, "entrada recorrente padrao", previous.defaultIncomeRecurring(), updated.defaultIncomeRecurring());
    addChange(changes, "dia padrao de recebimento", previous.defaultIncomeReceivedDay(), updated.defaultIncomeReceivedDay());
    addChange(changes, "modo padrao do dashboard", previous.defaultDashboardMode(), updated.defaultDashboardMode());
    addChange(
      changes,
      "distancia de comparacao no dashboard",
      previous.defaultDashboardMonthComparisonOffset(),
      updated.defaultDashboardMonthComparisonOffset()
    );
    if (!previous.billCategories().equals(updated.billCategories())) {
      changes.add("categorias de saida: " + previous.billCategories() + " -> " + updated.billCategories());
    }
    if (!previous.incomeCategories().equals(updated.incomeCategories())) {
      changes.add("categorias de entrada: " + previous.incomeCategories() + " -> " + updated.incomeCategories());
    }

    if (changes.isEmpty()) {
      return null;
    }
    return "Preferencias da aplicacao alteradas: " + String.join("; ", changes);
  }

  private void addChange(List<String> changes, String label, Object previous, Object updated) {
    if (!Objects.equals(previous, updated)) {
      changes.add(label + ": " + previous + " -> " + updated);
    }
  }
}
