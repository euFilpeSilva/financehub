package com.financehub.backend.modules.governance.api.dto;

public record EmergencyResetRequest(
  boolean keepBankAccounts
) {
}
