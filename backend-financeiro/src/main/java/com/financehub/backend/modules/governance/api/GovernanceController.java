package com.financehub.backend.modules.governance.api;

import com.financehub.backend.modules.governance.api.dto.AppPreferencesRequest;
import com.financehub.backend.modules.governance.api.dto.RetentionSettingsRequest;
import com.financehub.backend.modules.governance.application.GovernanceService;
import com.financehub.backend.modules.governance.application.TrashService;
import com.financehub.backend.modules.governance.domain.AppPreferences;
import com.financehub.backend.modules.governance.domain.AuditEvent;
import com.financehub.backend.modules.governance.domain.RetentionSettings;
import com.financehub.backend.modules.governance.domain.TrashItem;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
  public List<AuditEvent> listAuditEvents() {
    return service.listAuditEvents();
  }

  @GetMapping("/trash-items")
  public List<TrashItem> listTrashItems() {
    return trashService.listAll();
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
  public void emergencyResetAllData() {
    service.emergencyResetAllData();
  }
}
