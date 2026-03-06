package com.financehub.backend.modules.governance.application;

import com.financehub.backend.modules.bills.infrastructure.BillSpringDataRepository;
import com.financehub.backend.modules.bankaccounts.infrastructure.BankAccountJpaEntity;
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
import java.time.LocalDate;
import java.time.ZoneOffset;
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
  public List<AuditEvent> listAuditEventsFiltered(
    LocalDate startDate,
    LocalDate endDate,
    String entityType,
    String action,
    String transactionBankAccountId,
    String statementImportBankAccountId,
    String name,
    Double minValue,
    Double maxValue
  ) {
    String normalizedEntityType = normalizeOption(entityType);
    String normalizedAction = normalizeOption(action);
    String normalizedTransactionBankAccountId = normalizeOption(transactionBankAccountId);
    String normalizedStatementImportBankAccountId = normalizeOption(statementImportBankAccountId);
    String normalizedName = normalizeText(name);

    List<BankAccountJpaEntity> bankAccounts = bankAccountRepository.findAll();

    return listAuditEvents().stream()
      .filter(event -> matchesAuditDateRange(event, startDate, endDate))
      .filter(event -> matchesAuditEntityType(event, normalizedEntityType))
      .filter(event -> matchesAuditAction(event, normalizedAction))
      .filter(event -> matchesAuditName(event, normalizedName))
      .filter(event -> matchesAuditAmount(event, minValue, maxValue))
      .filter(event -> matchesAuditTransactionBank(event, normalizedTransactionBankAccountId))
      .filter(event -> matchesAuditStatementImportBank(event, normalizedStatementImportBankAccountId, bankAccounts))
      .toList();
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
  public void emergencyResetAllData(boolean keepBankAccounts) {
    trashItemRepository.deleteAllInBatch();
    spendingGoalRepository.deleteAllInBatch();
    planningGoalRepository.deleteAllInBatch();
    incomeRepository.deleteAllInBatch();
    billRepository.deleteAllInBatch();
    if (!keepBankAccounts) {
      bankAccountRepository.deleteAllInBatch();
    }
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

  private boolean matchesAuditDateRange(AuditEvent event, LocalDate startDate, LocalDate endDate) {
    LocalDate eventDate = event.timestamp().atZone(ZoneOffset.UTC).toLocalDate();
    if (startDate != null && eventDate.isBefore(startDate)) {
      return false;
    }
    if (endDate != null && eventDate.isAfter(endDate)) {
      return false;
    }
    return true;
  }

  private boolean matchesAuditEntityType(AuditEvent event, String normalizedEntityType) {
    if (normalizedEntityType.isBlank() || "ALL".equals(normalizedEntityType)) {
      return true;
    }
    return normalizeOption(event.entityType()).equals(normalizedEntityType);
  }

  private boolean matchesAuditAction(AuditEvent event, String normalizedAction) {
    if (normalizedAction.isBlank() || "ALL".equals(normalizedAction)) {
      return true;
    }
    return normalizeOption(event.action()).equals(normalizedAction);
  }

  private boolean matchesAuditName(AuditEvent event, String normalizedName) {
    if (normalizedName.isBlank()) {
      return true;
    }
    return normalizeText(event.message()).contains(normalizedName);
  }

  private boolean matchesAuditAmount(AuditEvent event, Double minValue, Double maxValue) {
    if (minValue == null && maxValue == null) {
      return true;
    }
    if (event.amount() == null) {
      return false;
    }
    if (minValue != null && event.amount() < minValue) {
      return false;
    }
    if (maxValue != null && event.amount() > maxValue) {
      return false;
    }
    return true;
  }

  private boolean matchesAuditTransactionBank(AuditEvent event, String normalizedTransactionBankAccountId) {
    if (normalizedTransactionBankAccountId.isBlank() || "ALL".equals(normalizedTransactionBankAccountId)) {
      return true;
    }
    String eventBankId = resolveTransactionBankAccountId(event);
    return eventBankId != null && normalizeOption(eventBankId).equals(normalizedTransactionBankAccountId);
  }

  private boolean matchesAuditStatementImportBank(
    AuditEvent event,
    String normalizedStatementImportBankAccountId,
    List<BankAccountJpaEntity> bankAccounts
  ) {
    if (normalizedStatementImportBankAccountId.isBlank() || "ALL".equals(normalizedStatementImportBankAccountId)) {
      return true;
    }
    if (!"STATEMENT".equals(normalizeOption(event.entityType())) || !"IMPORT".equals(normalizeOption(event.action()))) {
      return false;
    }
    String eventBankId = resolveStatementImportBankAccountId(event, bankAccounts);
    return eventBankId != null && normalizeOption(eventBankId).equals(normalizedStatementImportBankAccountId);
  }

  private String resolveTransactionBankAccountId(AuditEvent event) {
    String normalizedEntityType = normalizeOption(event.entityType());
    if ("BILL".equals(normalizedEntityType)) {
      return billRepository.findById(event.entityId()).map(entity -> entity.getBankAccountId()).orElse(null);
    }
    if ("INCOME".equals(normalizedEntityType)) {
      return incomeRepository.findById(event.entityId()).map(entity -> entity.getBankAccountId()).orElse(null);
    }
    return null;
  }

  private String resolveStatementImportBankAccountId(AuditEvent event, List<BankAccountJpaEntity> bankAccounts) {
    for (BankAccountJpaEntity account : bankAccounts) {
      if (account.getId().equals(event.entityId())) {
        return account.getId();
      }
    }

    String digits = (event.entityId() + " " + event.message()).replaceAll("\\D", "");
    for (BankAccountJpaEntity account : bankAccounts) {
      String accountDigits = account.getAccountId() == null ? "" : account.getAccountId().replaceAll("\\D", "");
      if (accountDigits.length() >= 4 && digits.contains(accountDigits)) {
        return account.getId();
      }
    }

    String normalizedEventText = normalizeText(event.entityId() + " " + event.message());
    for (BankAccountJpaEntity account : bankAccounts) {
      String normalizedLabel = normalizeText(account.getLabel());
      if (!normalizedLabel.isBlank() && normalizedEventText.contains(normalizedLabel)) {
        return account.getId();
      }
    }

    return null;
  }

  private String normalizeOption(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    return value.trim().toUpperCase(Locale.ROOT);
  }

  private String normalizeText(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
      .replaceAll("\\p{M}", "")
      .toUpperCase(Locale.ROOT)
      .replaceAll("[^A-Z0-9 ]", " ")
      .replaceAll("\\s{2,}", " ")
      .trim();
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
