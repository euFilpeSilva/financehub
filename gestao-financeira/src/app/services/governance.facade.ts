import { computed, Injectable, signal } from '@angular/core';
import {
  AppPreferences,
  AuditEvent,
  DataRetentionSettings,
  TrashItem
} from '../models/finance.models';
import {
  DEFAULT_BILL_CATEGORIES,
  DEFAULT_INCOME_CATEGORIES,
  normalizePreferences
} from './finance-preferences.utils';

@Injectable({ providedIn: 'root' })
export class GovernanceFacade {
  private readonly trashItemsSource = signal<TrashItem[]>([]);
  private readonly auditEventsSource = signal<AuditEvent[]>([]);
  private readonly retentionSettingsSource = signal<DataRetentionSettings>({
    trashRetentionDays: 30,
    auditRetentionDays: 180
  });
  private readonly appPreferencesSource = signal<AppPreferences>({
    defaultBillCategory: 'Moradia',
    defaultBillRecurring: false,
    defaultBillDueDay: 5,
    defaultIncomeCategory: 'Trabalho',
    defaultIncomeRecurring: false,
    defaultIncomeReceivedDay: 1,
    defaultDashboardMode: 'month',
    defaultDashboardMonthComparisonOffset: 1,
    billCategories: DEFAULT_BILL_CATEGORIES,
    incomeCategories: DEFAULT_INCOME_CATEGORIES
  });

  readonly trashItems = computed(() =>
    this.trashItemsSource().slice().sort((a, b) => b.deletedAt.localeCompare(a.deletedAt))
  );

  readonly auditEvents = computed(() =>
    this.auditEventsSource().slice().sort((a, b) => b.timestamp.localeCompare(a.timestamp))
  );

  readonly retentionSettings = computed(() => this.retentionSettingsSource());
  readonly appPreferences = computed(() => this.appPreferencesSource());

  setGovernanceData(
    trashItems: TrashItem[],
    auditEvents: AuditEvent[],
    retentionSettings: DataRetentionSettings,
    appPreferences: AppPreferences
  ): void {
    this.trashItemsSource.set(trashItems);
    this.auditEventsSource.set(auditEvents);
    this.retentionSettingsSource.set(retentionSettings);
    this.appPreferencesSource.set(normalizePreferences(appPreferences));
  }

  setAuditEvents(items: AuditEvent[]): void {
    this.auditEventsSource.set(items);
  }

  setRetentionSettings(settings: DataRetentionSettings): void {
    this.retentionSettingsSource.set(settings);
  }

  setAppPreferences(preferences: AppPreferences): void {
    this.appPreferencesSource.set(normalizePreferences(preferences));
  }
}
