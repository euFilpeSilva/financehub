package com.financehub.backend.modules.governance.api;

import com.financehub.backend.modules.governance.api.dto.AppPreferencesRequest;
import com.financehub.backend.modules.governance.api.dto.EmergencyResetRequest;
import com.financehub.backend.modules.governance.api.dto.RetentionSettingsRequest;
import com.financehub.backend.modules.governance.application.GovernanceService;
import com.financehub.backend.modules.governance.application.TrashService;
import com.financehub.backend.modules.governance.domain.AppPreferences;
import com.financehub.backend.modules.governance.domain.AuditEvent;
import com.financehub.backend.modules.governance.domain.RetentionSettings;
import com.financehub.backend.modules.governance.domain.TrashItem;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/governance")
public class GovernanceController {
  private final GovernanceService service;
  private final TrashService trashService;

  public GovernanceController(GovernanceService service, TrashService trashService) {
    this.service = service;
    this.trashService = trashService;
  }

  @GetMapping("/audit-events")
  public List<AuditEvent> listAuditEvents(
    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
    @RequestParam(required = false) String entityType,
    @RequestParam(required = false) String action,
    @RequestParam(required = false) String transactionBankAccountId,
    @RequestParam(required = false) String statementImportBankAccountId,
    @RequestParam(required = false) String name,
    @RequestParam(required = false) Double minValue,
    @RequestParam(required = false) Double maxValue
  ) {
    return service.listAuditEventsFiltered(
      startDate,
      endDate,
      entityType,
      action,
      transactionBankAccountId,
      statementImportBankAccountId,
      name,
      minValue,
      maxValue
    );
  }

  @GetMapping("/trash-items")
  public List<TrashItem> listTrashItems(
    @RequestParam(required = false) String query,
    @RequestParam(required = false) String entityType,
    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
  ) {
    return trashService.listFiltered(query, entityType, startDate, endDate);
  }

  @PostMapping("/trash-items/{trashId}/restore")
  public void restoreTrashItem(@PathVariable String trashId) {
    trashService.restore(trashId);
  }

  @DeleteMapping("/trash-items/{trashId}")
  public void purgeTrashItem(@PathVariable String trashId) {
    trashService.purge(trashId);
  }

  @GetMapping("/retention-settings")
  public RetentionSettings getRetentionSettings() {
    return service.getRetentionSettings();
  }

  @PutMapping("/retention-settings")
  public RetentionSettings updateRetentionSettings(@Valid @RequestBody RetentionSettingsRequest request) {
    return service.updateRetentionSettings(request);
  }

  @GetMapping("/app-preferences")
  public AppPreferences getAppPreferences() {
    return service.getAppPreferences();
  }

  @PutMapping("/app-preferences")
  public AppPreferences updateAppPreferences(@Valid @RequestBody AppPreferencesRequest request) {
    return service.updateAppPreferences(request);
  }

  @PostMapping("/retention-cleanup")
  public void runRetentionCleanup() {
    service.runRetentionCleanup();
  }

  @PostMapping("/emergency-reset")
  public void emergencyResetAllData(@RequestBody(required = false) EmergencyResetRequest request) {
    boolean keepBankAccounts = request != null && request.keepBankAccounts();
    service.emergencyResetAllData(keepBankAccounts);
  }
}
