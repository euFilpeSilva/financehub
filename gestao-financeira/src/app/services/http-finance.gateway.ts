import { Injectable } from '@angular/core';
import { HttpClient, HttpEvent, HttpEventType } from '@angular/common/http';
import { Observable } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import {
  resolveDefaultCategory,
  safeBillCategories,
  safeIncomeCategories
} from './finance-preferences.utils';
import {
  AccountReconciliation,
  AppPreferences,
  AuditEvent,
  BankAccount,
  BillRecord,
  DataRetentionSettings,
  DashboardSummary,
  IncomeEntry,
  InternalTransferSuggestion,
  ImportedStatementYearCleanupRequest,
  ImportedStatementYearCleanupResult,
  OfxImportProgressEvent,
  OfxImportResult,
  PlanningGoal,
  SpendingGoal,
  SpendingGoalStatus,
  TrashItem
} from '../models/finance.models';
import {
  AuditEventListFilters,
  BillListFilters,
  FinanceGateway,
  IncomeListFilters,
  PlanningGoalListFilters,
  TrashListFilters
} from './finance.gateway';
import { environment } from '../../environments/environment';

type BillApi = {
  id: string;
  description: string;
  category: string;
  amount: number;
  dueDate: string;
  recurring: boolean;
  paid: boolean;
  internalTransfer?: boolean;
  bankAccountId?: string | null;
};

type IncomeApi = {
  id: string;
  source: string;
  category: string;
  amount: number;
  receivedAt: string;
  recurring: boolean;
  internalTransfer?: boolean;
  bankAccountId?: string | null;
};

type GoalApi = {
  id: string;
  title: string;
  targetAmount: number;
  currentAmount: number;
  targetDate: string;
  notes: string;
  complete: boolean;
};

type BankAccountApi = {
  id: string;
  label: string;
  bankId: string;
  branchId?: string | null;
  accountId: string;
  primaryIncome: boolean;
  active: boolean;
};

type SpendingGoalApi = {
  id: string;
  title: string;
  limitAmount: number;
  category: string;
  schedule: 'monthly' | 'custom';
  startMonth?: string;
  startDate?: string;
  endDate?: string;
  active: boolean;
};

type SpendingGoalStatusApi = {
  goal: SpendingGoalApi;
  spentAmount: number;
  remainingAmount: number;
  usagePercent: number;
  onTrack: boolean;
  periodLabel: string;
};

type TrashApi = {
  id: string;
  entityType: TrashItem['entityType'];
  entityId: string;
  label: string;
  payload: unknown;
  deletedAt: string;
  purgeAt: string;
};

type OfxImportApi = OfxImportResult;
type DashboardSummaryApi = DashboardSummary;
type AccountReconciliationApi = AccountReconciliation;
type InternalTransferSuggestionApi = InternalTransferSuggestion;
type ImportedStatementYearCleanupApi = ImportedStatementYearCleanupResult;

type AuditApi = {
  id: string;
  entityType: string;
  entityId: string;
  action: string;
  message: string;
  amount?: number;
  timestamp: string;
};

@Injectable()
export class HttpFinanceGateway implements FinanceGateway {
  private readonly baseUrl = environment.apiBaseUrl;

  constructor(private readonly http: HttpClient) {}

  listBills(filters?: BillListFilters): Observable<BillRecord[]> {
    const params = this.buildBillParams(filters);
    return this.http.get<BillApi[]>(`${this.baseUrl}/bills`, { params }).pipe(
      map((items) => items.map((item) => ({ ...item, internalTransfer: Boolean(item.internalTransfer) })))
    );
  }

  createBill(payload: Omit<BillRecord, 'id'>): Observable<BillRecord> {
    return this.http.post<BillApi>(`${this.baseUrl}/bills`, {
      ...payload,
      internalTransfer: Boolean(payload.internalTransfer)
    }).pipe(map((item) => ({ ...item, internalTransfer: Boolean(item.internalTransfer) })));
  }

  updateBill(payload: BillRecord): Observable<BillRecord> {
    return this.http.put<BillApi>(`${this.baseUrl}/bills/${payload.id}`, {
      description: payload.description,
      category: payload.category,
      amount: payload.amount,
      dueDate: payload.dueDate,
      recurring: payload.recurring,
      paid: payload.paid,
      internalTransfer: Boolean(payload.internalTransfer),
      bankAccountId: payload.bankAccountId ?? null
    }).pipe(map((item) => ({ ...item, internalTransfer: Boolean(item.internalTransfer) })));
  }

  deleteBill(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/bills/${id}`);
  }

  createRecurringBills(month: string): Observable<BillRecord[]> {
    return this.http.post<BillApi[]>(`${this.baseUrl}/bills/recurring`, null, {
      params: { month }
    }).pipe(
      map((items) => items.map((item) => ({ ...item, internalTransfer: Boolean(item.internalTransfer) })))
    );
  }

  listIncomes(filters?: IncomeListFilters): Observable<IncomeEntry[]> {
    const params = this.buildIncomeParams(filters);
    return this.http.get<IncomeApi[]>(`${this.baseUrl}/incomes`, { params }).pipe(
      map((items) => items.map((item) => ({ ...item, internalTransfer: Boolean(item.internalTransfer) })))
    );
  }

  private buildBillParams(filters?: BillListFilters): Record<string, string> {
    if (!filters) {
      return {};
    }

    const params: Record<string, string> = {};
    if (filters.query && filters.query.trim().length > 0) {
      params['query'] = filters.query.trim();
    }
    if (filters.category && filters.category !== 'ALL') {
      params['category'] = filters.category;
    }
    if (filters.bankAccountId && filters.bankAccountId !== 'ALL') {
      params['bankAccountId'] = filters.bankAccountId;
    }
    if (filters.status && filters.status !== 'ALL') {
      params['status'] = filters.status;
    }
    if (filters.recurring && filters.recurring !== 'ALL') {
      params['recurring'] = filters.recurring;
    }
    if (filters.startDate && filters.startDate.trim().length > 0) {
      params['startDate'] = filters.startDate;
    }
    if (filters.endDate && filters.endDate.trim().length > 0) {
      params['endDate'] = filters.endDate;
    }
    return params;
  }

  private buildIncomeParams(filters?: IncomeListFilters): Record<string, string> {
    if (!filters) {
      return {};
    }

    const params: Record<string, string> = {};
    if (filters.query && filters.query.trim().length > 0) {
      params['query'] = filters.query.trim();
    }
    if (filters.category && filters.category !== 'ALL') {
      params['category'] = filters.category;
    }
    if (filters.bankAccountId && filters.bankAccountId !== 'ALL') {
      params['bankAccountId'] = filters.bankAccountId;
    }
    if (filters.recurring && filters.recurring !== 'ALL') {
      params['recurring'] = filters.recurring;
    }
    if (filters.startDate && filters.startDate.trim().length > 0) {
      params['startDate'] = filters.startDate;
    }
    if (filters.endDate && filters.endDate.trim().length > 0) {
      params['endDate'] = filters.endDate;
    }
    if (typeof filters.minAmount === 'number' && !Number.isNaN(filters.minAmount)) {
      params['minAmount'] = String(filters.minAmount);
    }
    if (typeof filters.maxAmount === 'number' && !Number.isNaN(filters.maxAmount)) {
      params['maxAmount'] = String(filters.maxAmount);
    }
    return params;
  }

  createIncome(payload: Omit<IncomeEntry, 'id'>): Observable<IncomeEntry> {
    return this.http.post<IncomeApi>(`${this.baseUrl}/incomes`, {
      ...payload,
      internalTransfer: Boolean(payload.internalTransfer)
    }).pipe(map((item) => ({ ...item, internalTransfer: Boolean(item.internalTransfer) })));
  }

  updateIncome(payload: IncomeEntry): Observable<IncomeEntry> {
    return this.http.put<IncomeApi>(`${this.baseUrl}/incomes/${payload.id}`, {
      source: payload.source,
      category: payload.category,
      amount: payload.amount,
      receivedAt: payload.receivedAt,
      recurring: payload.recurring,
      internalTransfer: Boolean(payload.internalTransfer),
      bankAccountId: payload.bankAccountId ?? null
    }).pipe(map((item) => ({ ...item, internalTransfer: Boolean(item.internalTransfer) })));
  }

  deleteIncome(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/incomes/${id}`);
  }

  listGoals(filters?: PlanningGoalListFilters): Observable<PlanningGoal[]> {
    const params = this.buildPlanningParams(filters);
    return this.http.get<GoalApi[]>(`${this.baseUrl}/planning-goals`, { params });
  }

  createGoal(payload: Omit<PlanningGoal, 'id'>): Observable<PlanningGoal> {
    return this.http.post<GoalApi>(`${this.baseUrl}/planning-goals`, payload);
  }

  updateGoal(payload: PlanningGoal): Observable<PlanningGoal> {
    return this.http.put<GoalApi>(`${this.baseUrl}/planning-goals/${payload.id}`, {
      title: payload.title,
      targetAmount: payload.targetAmount,
      currentAmount: payload.currentAmount,
      targetDate: payload.targetDate,
      notes: payload.notes,
      complete: payload.complete
    });
  }

  deleteGoal(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/planning-goals/${id}`);
  }

  listBankAccounts(): Observable<BankAccount[]> {
    return this.http.get<BankAccountApi[]>(`${this.baseUrl}/bank-accounts`);
  }

  createBankAccount(payload: Omit<BankAccount, 'id'>): Observable<BankAccount> {
    return this.http.post<BankAccountApi>(`${this.baseUrl}/bank-accounts`, payload);
  }

  updateBankAccount(payload: BankAccount): Observable<BankAccount> {
    return this.http.put<BankAccountApi>(`${this.baseUrl}/bank-accounts/${payload.id}`, {
      label: payload.label,
      bankId: payload.bankId,
      branchId: payload.branchId,
      accountId: payload.accountId,
      primaryIncome: payload.primaryIncome,
      active: payload.active
    });
  }

  deleteBankAccount(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/bank-accounts/${id}`);
  }

  listSpendingGoals(): Observable<SpendingGoal[]> {
    return this.http.get<SpendingGoalApi[]>(`${this.baseUrl}/spending-goals`);
  }

  listSpendingGoalStatuses(selectedMonth: string): Observable<SpendingGoalStatus[]> {
    return this.http.get<SpendingGoalStatusApi[]>(`${this.baseUrl}/spending-goals/statuses`, {
      params: { selectedMonth }
    });
  }

  createSpendingGoal(payload: Omit<SpendingGoal, 'id'>): Observable<SpendingGoal> {
    return this.http.post<SpendingGoalApi>(`${this.baseUrl}/spending-goals`, payload);
  }

  updateSpendingGoal(payload: SpendingGoal): Observable<SpendingGoal> {
    return this.http.put<SpendingGoalApi>(`${this.baseUrl}/spending-goals/${payload.id}`, {
      title: payload.title,
      limitAmount: payload.limitAmount,
      category: payload.category,
      schedule: payload.schedule,
      startMonth: payload.startMonth,
      startDate: payload.startDate,
      endDate: payload.endDate,
      active: payload.active
    });
  }

  deleteSpendingGoal(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/spending-goals/${id}`);
  }

  listTrashItems(filters?: TrashListFilters): Observable<TrashItem[]> {
    const params = this.buildTrashParams(filters);
    return this.http.get<TrashApi[]>(`${this.baseUrl}/governance/trash-items`, { params });
  }

  restoreTrashItem(trashId: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/governance/trash-items/${trashId}/restore`, {});
  }

  purgeTrashItem(trashId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/governance/trash-items/${trashId}`);
  }

  getRetentionSettings(): Observable<DataRetentionSettings> {
    return this.http.get<DataRetentionSettings>(`${this.baseUrl}/governance/retention-settings`);
  }

  updateRetentionSettings(payload: DataRetentionSettings): Observable<DataRetentionSettings> {
    return this.http.put<DataRetentionSettings>(`${this.baseUrl}/governance/retention-settings`, payload);
  }

  getAppPreferences(): Observable<AppPreferences> {
    return this.http
      .get<Partial<AppPreferences>>(`${this.baseUrl}/governance/app-preferences`)
      .pipe(map((payload) => this.normalizeAppPreferences(payload)));
  }

  updateAppPreferences(payload: AppPreferences): Observable<AppPreferences> {
    return this.http
      .put<Partial<AppPreferences>>(`${this.baseUrl}/governance/app-preferences`, payload)
      .pipe(map((response) => this.normalizeAppPreferences(response)));
  }

  listAuditEvents(filters?: AuditEventListFilters): Observable<AuditEvent[]> {
    const params = this.buildAuditParams(filters);
    return this.http.get<AuditApi[]>(`${this.baseUrl}/governance/audit-events`, { params }).pipe(
      map((items) => items.map((item) => ({
        id: item.id,
        entityType: item.entityType as AuditEvent['entityType'],
        entityId: item.entityId,
        action: item.action as AuditEvent['action'],
        message: item.message,
        amount: item.amount,
        timestamp: item.timestamp
      })))
    );
  }

  private buildPlanningParams(filters?: PlanningGoalListFilters): Record<string, string> {
    if (!filters) {
      return {};
    }

    const params: Record<string, string> = {};
    if (filters.query && filters.query.trim().length > 0) {
      params['query'] = filters.query.trim();
    }
    if (filters.status && filters.status !== 'ALL') {
      params['status'] = filters.status;
    }
    if (filters.startDate && filters.startDate.trim().length > 0) {
      params['startDate'] = filters.startDate;
    }
    if (filters.endDate && filters.endDate.trim().length > 0) {
      params['endDate'] = filters.endDate;
    }
    return params;
  }

  private buildTrashParams(filters?: TrashListFilters): Record<string, string> {
    if (!filters) {
      return {};
    }

    const params: Record<string, string> = {};
    if (filters.query && filters.query.trim().length > 0) {
      params['query'] = filters.query.trim();
    }
    if (filters.entityType && filters.entityType !== 'ALL') {
      params['entityType'] = filters.entityType;
    }
    if (filters.startDate && filters.startDate.trim().length > 0) {
      params['startDate'] = filters.startDate;
    }
    if (filters.endDate && filters.endDate.trim().length > 0) {
      params['endDate'] = filters.endDate;
    }
    return params;
  }

  private buildAuditParams(filters?: AuditEventListFilters): Record<string, string> {
    if (!filters) {
      return {};
    }

    const params: Record<string, string> = {};
    if (filters.startDate && filters.startDate.trim().length > 0) {
      params['startDate'] = filters.startDate;
    }
    if (filters.endDate && filters.endDate.trim().length > 0) {
      params['endDate'] = filters.endDate;
    }
    if (filters.entityType && filters.entityType !== 'ALL') {
      params['entityType'] = filters.entityType;
    }
    if (filters.action && filters.action !== 'ALL') {
      params['action'] = filters.action;
    }
    if (filters.transactionBankAccountId && filters.transactionBankAccountId !== 'ALL') {
      params['transactionBankAccountId'] = filters.transactionBankAccountId;
    }
    if (filters.statementImportBankAccountId && filters.statementImportBankAccountId !== 'ALL') {
      params['statementImportBankAccountId'] = filters.statementImportBankAccountId;
    }
    if (filters.name && filters.name.trim().length > 0) {
      params['name'] = filters.name.trim();
    }
    if (typeof filters.minValue === 'number' && !Number.isNaN(filters.minValue)) {
      params['minValue'] = String(filters.minValue);
    }
    if (typeof filters.maxValue === 'number' && !Number.isNaN(filters.maxValue)) {
      params['maxValue'] = String(filters.maxValue);
    }
    return params;
  }

  getDashboardSummary(startDate: string, endDate: string): Observable<DashboardSummary> {
    return this.http.get<DashboardSummaryApi>(`${this.baseUrl}/analytics/dashboard-summary`, {
      params: {
        startDate,
        endDate
      }
    });
  }

  getAccountReconciliation(
    bankAccountId: string,
    startDate: string,
    endDate: string,
    referenceBalance?: number
  ): Observable<AccountReconciliation> {
    const params: Record<string, string> = {
      bankAccountId,
      startDate,
      endDate
    };

    if (typeof referenceBalance === 'number' && !Number.isNaN(referenceBalance)) {
      params['referenceBalance'] = String(referenceBalance);
    }

    return this.http.get<AccountReconciliationApi>(`${this.baseUrl}/analytics/account-reconciliation`, {
      params
    });
  }

  detectInternalTransfers(payload: {
    ownerName?: string;
    ownerCpf?: string;
    dateToleranceDays: number;
    autoApply: boolean;
  }): Observable<InternalTransferSuggestion[]> {
    return this.http.post<InternalTransferSuggestionApi[]>(`${this.baseUrl}/transfers/internal/detect`, {
      ownerName: payload.ownerName?.trim() || null,
      ownerCpf: payload.ownerCpf?.trim() || null,
      dateToleranceDays: payload.dateToleranceDays,
      autoApply: payload.autoApply
    }).pipe(
      map((items) => items.map((item) => ({
        ...item,
        confidence: item.confidence ?? 'BAIXA',
        reasons: Array.isArray(item.reasons) ? item.reasons : []
      })))
    );
  }

  linkInternalTransfer(billId: string, incomeId: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/transfers/internal/link`, { billId, incomeId });
  }

  runRetentionCleanup(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/governance/retention-cleanup`, {});
  }

  emergencyResetAllData(keepBankAccounts: boolean): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/governance/emergency-reset`, {
      keepBankAccounts: Boolean(keepBankAccounts)
    });
  }

  importOfxStatement(file: File, ownerName?: string, ownerCpf?: string): Observable<OfxImportResult> {
    return this.importOfxStatementWithProgress(file, ownerName, ownerCpf).pipe(
      filter((event): event is Extract<OfxImportProgressEvent, { kind: 'completed' }> => event.kind === 'completed'),
      map((event) => event.result)
    );
  }

  importOfxStatementWithProgress(
    file: File,
    ownerName?: string,
    ownerCpf?: string
  ): Observable<OfxImportProgressEvent> {
    const formData = new FormData();
    formData.append('file', file, file.name);
    if (ownerName && ownerName.trim().length > 0) {
      formData.append('ownerName', ownerName.trim());
    }
    if (ownerCpf && ownerCpf.trim().length > 0) {
      formData.append('ownerCpf', ownerCpf.trim());
    }
    formData.append('applyInternalTransferDetection', 'true');

    return this.http.post<OfxImportApi>(
      `${this.baseUrl}/statements/import/ofx`,
      formData,
      {
        observe: 'events',
        reportProgress: true
      }
    ).pipe(
      map((event) => this.mapOfxImportEvent(event as HttpEvent<OfxImportApi>, file.name)),
      filter((event): event is OfxImportProgressEvent => event !== null)
    );
  }

  cleanupImportedStatementYear(
    payload: ImportedStatementYearCleanupRequest
  ): Observable<ImportedStatementYearCleanupResult> {
    return this.http.post<ImportedStatementYearCleanupApi>(
      `${this.baseUrl}/statements/cleanup/imported-year`,
      {
        year: payload.year,
        bankAccountId: payload.bankAccountId ?? null,
        dryRun: Boolean(payload.dryRun),
        permanentDelete: Boolean(payload.permanentDelete)
      }
    );
  }

  private mapOfxImportEvent(event: HttpEvent<OfxImportApi>, fileName: string): OfxImportProgressEvent | null {
    if (event.type === HttpEventType.Sent) {
      return {
        kind: 'progress',
        fileName,
        progress: 0,
        phase: 'uploading'
      };
    }

    if (event.type === HttpEventType.UploadProgress) {
      const uploadPercent = event.total
        ? Math.round((event.loaded / event.total) * 90)
        : 65;
      return {
        kind: 'progress',
        fileName,
        progress: Math.min(95, Math.max(1, uploadPercent)),
        phase: 'uploading'
      };
    }

    if (event.type === HttpEventType.ResponseHeader) {
      return {
        kind: 'progress',
        fileName,
        progress: 95,
        phase: 'processing'
      };
    }

    if (event.type === HttpEventType.Response && event.body) {
      return {
        kind: 'completed',
        fileName,
        progress: 100,
        phase: 'completed',
        result: event.body
      };
    }

    return null;
  }

  private normalizeAppPreferences(payload: Partial<AppPreferences>): AppPreferences {
    const billCategories = safeBillCategories(payload);
    const incomeCategories = safeIncomeCategories(payload);
    return {
      defaultBillCategory: resolveDefaultCategory(payload.defaultBillCategory, billCategories),
      defaultBillRecurring: Boolean(payload.defaultBillRecurring),
      defaultBillDueDay: this.clampNumber(payload.defaultBillDueDay, 1, 31, 5),
      defaultIncomeCategory: resolveDefaultCategory(payload.defaultIncomeCategory, incomeCategories),
      defaultIncomeRecurring: Boolean(payload.defaultIncomeRecurring),
      defaultIncomeReceivedDay: this.clampNumber(payload.defaultIncomeReceivedDay, 1, 31, 1),
      defaultDashboardMode: payload.defaultDashboardMode === 'range' ? 'range' : 'month',
      defaultDashboardMonthComparisonOffset: this.clampNumber(payload.defaultDashboardMonthComparisonOffset, 1, 12, 1),
      billCategories,
      incomeCategories
    };
  }

  private clampNumber(value: number | undefined, min: number, max: number, fallback: number): number {
    if (typeof value !== 'number' || Number.isNaN(value)) {
      return fallback;
    }
    return Math.max(min, Math.min(max, Number(value)));
  }
}
