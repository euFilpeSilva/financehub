import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import {
  AccountReconciliation,
  AppPreferences,
  AuditEvent,
  BankAccount,
  BillRecord,
  DataRetentionSettings,
  DashboardSummary,
  IncomeEntry,
  OfxImportResult,
  PlanningGoal,
  SpendingGoal,
  TrashItem
} from '../models/finance.models';
import { FinanceGateway } from './finance.gateway';
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

type AuditApi = {
  id: string;
  entityType: string;
  entityId: string;
  action: string;
  message: string;
  amount?: number;
  timestamp: string;
};

const DEFAULT_BILL_CATEGORIES = ['Moradia', 'Alimentacao', 'Utilidades', 'Saude', 'Transporte', 'Educacao', 'Lazer', 'Outros'];
const DEFAULT_INCOME_CATEGORIES = ['Trabalho', 'Extra', 'Investimentos', 'Reembolso', 'Outros'];

@Injectable()
export class HttpFinanceGateway implements FinanceGateway {
  private readonly baseUrl = environment.apiBaseUrl;

  constructor(private readonly http: HttpClient) {}

  listBills(): Observable<BillRecord[]> {
    return this.http.get<BillApi[]>(`${this.baseUrl}/bills`).pipe(
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

  listIncomes(): Observable<IncomeEntry[]> {
    return this.http.get<IncomeApi[]>(`${this.baseUrl}/incomes`).pipe(
      map((items) => items.map((item) => ({ ...item, internalTransfer: Boolean(item.internalTransfer) })))
    );
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

  listGoals(): Observable<PlanningGoal[]> {
    return this.http.get<GoalApi[]>(`${this.baseUrl}/planning-goals`);
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

  listTrashItems(): Observable<TrashItem[]> {
    return this.http.get<TrashApi[]>(`${this.baseUrl}/governance/trash-items`);
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

  listAuditEvents(): Observable<AuditEvent[]> {
    return this.http.get<AuditApi[]>(`${this.baseUrl}/governance/audit-events`).pipe(
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

  runRetentionCleanup(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/governance/retention-cleanup`, {});
  }

  emergencyResetAllData(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/governance/emergency-reset`, {});
  }

  importOfxStatement(file: File, ownerName?: string, ownerCpf?: string): Observable<OfxImportResult> {
    const formData = new FormData();
    formData.append('file', file, file.name);
    if (ownerName && ownerName.trim().length > 0) {
      formData.append('ownerName', ownerName.trim());
    }
    if (ownerCpf && ownerCpf.trim().length > 0) {
      formData.append('ownerCpf', ownerCpf.trim());
    }
    formData.append('applyInternalTransferDetection', 'true');
    return this.http.post<OfxImportApi>(`${this.baseUrl}/statements/import/ofx`, formData);
  }

  private normalizeAppPreferences(payload: Partial<AppPreferences>): AppPreferences {
    const billCategories = this.normalizeCategories(payload.billCategories, DEFAULT_BILL_CATEGORIES);
    const incomeCategories = this.normalizeCategories(payload.incomeCategories, DEFAULT_INCOME_CATEGORIES);
    return {
      defaultBillCategory: this.resolveDefaultCategory(payload.defaultBillCategory, billCategories),
      defaultBillRecurring: Boolean(payload.defaultBillRecurring),
      defaultBillDueDay: this.clampNumber(payload.defaultBillDueDay, 1, 31, 5),
      defaultIncomeCategory: this.resolveDefaultCategory(payload.defaultIncomeCategory, incomeCategories),
      defaultIncomeRecurring: Boolean(payload.defaultIncomeRecurring),
      defaultIncomeReceivedDay: this.clampNumber(payload.defaultIncomeReceivedDay, 1, 31, 1),
      defaultDashboardMode: payload.defaultDashboardMode === 'range' ? 'range' : 'month',
      defaultDashboardMonthComparisonOffset: this.clampNumber(payload.defaultDashboardMonthComparisonOffset, 1, 12, 1),
      billCategories,
      incomeCategories
    };
  }

  private normalizeCategories(values: string[] | undefined, fallback: string[]): string[] {
    if (!Array.isArray(values) || !values.length) {
      return [...fallback];
    }
    const unique = new Map<string, string>();
    for (const raw of values) {
      const value = raw?.trim();
      if (!value || value.length > 120) {
        continue;
      }
      const key = value.toLowerCase();
      if (!unique.has(key)) {
        unique.set(key, value);
      }
    }
    return unique.size ? Array.from(unique.values()) : [...fallback];
  }

  private resolveDefaultCategory(value: string | undefined, categories: string[]): string {
    const key = value?.trim()?.toLowerCase();
    const match = categories.find((item) => item.toLowerCase() === key);
    return match ?? categories[0];
  }

  private clampNumber(value: number | undefined, min: number, max: number, fallback: number): number {
    if (typeof value !== 'number' || Number.isNaN(value)) {
      return fallback;
    }
    return Math.max(min, Math.min(max, Number(value)));
  }
}
