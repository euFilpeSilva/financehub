import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
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
  OfxImportProgressEvent,
  OfxImportResult,
  PlanningGoal,
  SpendingGoal,
  SpendingGoalStatus,
  TrashItem
} from '../models/finance.models';

export interface BillListFilters {
  query?: string;
  category?: string;
  bankAccountId?: string;
  status?: string;
  recurring?: string;
  startDate?: string;
  endDate?: string;
}

export interface IncomeListFilters {
  query?: string;
  category?: string;
  bankAccountId?: string;
  recurring?: string;
  startDate?: string;
  endDate?: string;
  minAmount?: number | null;
  maxAmount?: number | null;
}

export interface PlanningGoalListFilters {
  query?: string;
  status?: string;
  startDate?: string;
  endDate?: string;
}

export interface TrashListFilters {
  query?: string;
  entityType?: string;
  startDate?: string;
  endDate?: string;
}

export interface AuditEventListFilters {
  startDate?: string;
  endDate?: string;
  entityType?: string;
  action?: string;
  transactionBankAccountId?: string;
  statementImportBankAccountId?: string;
  name?: string;
  minValue?: number | null;
  maxValue?: number | null;
}

export interface FinanceGateway {
  listBills(filters?: BillListFilters): Observable<BillRecord[]>;
  createBill(payload: Omit<BillRecord, 'id'>): Observable<BillRecord>;
  updateBill(payload: BillRecord): Observable<BillRecord>;
  deleteBill(id: string): Observable<void>;
  createRecurringBills(month: string): Observable<BillRecord[]>;

  listIncomes(filters?: IncomeListFilters): Observable<IncomeEntry[]>;
  createIncome(payload: Omit<IncomeEntry, 'id'>): Observable<IncomeEntry>;
  updateIncome(payload: IncomeEntry): Observable<IncomeEntry>;
  deleteIncome(id: string): Observable<void>;

  listGoals(filters?: PlanningGoalListFilters): Observable<PlanningGoal[]>;
  createGoal(payload: Omit<PlanningGoal, 'id'>): Observable<PlanningGoal>;
  updateGoal(payload: PlanningGoal): Observable<PlanningGoal>;
  deleteGoal(id: string): Observable<void>;

  listBankAccounts(): Observable<BankAccount[]>;
  createBankAccount(payload: Omit<BankAccount, 'id'>): Observable<BankAccount>;
  updateBankAccount(payload: BankAccount): Observable<BankAccount>;
  deleteBankAccount(id: string): Observable<void>;

  listSpendingGoals(): Observable<SpendingGoal[]>;
  listSpendingGoalStatuses(selectedMonth: string): Observable<SpendingGoalStatus[]>;
  createSpendingGoal(payload: Omit<SpendingGoal, 'id'>): Observable<SpendingGoal>;
  updateSpendingGoal(payload: SpendingGoal): Observable<SpendingGoal>;
  deleteSpendingGoal(id: string): Observable<void>;

  listTrashItems(filters?: TrashListFilters): Observable<TrashItem[]>;
  restoreTrashItem(trashId: string): Observable<void>;
  purgeTrashItem(trashId: string): Observable<void>;

  getRetentionSettings(): Observable<DataRetentionSettings>;
  updateRetentionSettings(payload: DataRetentionSettings): Observable<DataRetentionSettings>;
  getAppPreferences(): Observable<AppPreferences>;
  updateAppPreferences(payload: AppPreferences): Observable<AppPreferences>;

  listAuditEvents(filters?: AuditEventListFilters): Observable<AuditEvent[]>;
  getDashboardSummary(startDate: string, endDate: string): Observable<DashboardSummary>;
  getAccountReconciliation(
    bankAccountId: string,
    startDate: string,
    endDate: string,
    referenceBalance?: number
  ): Observable<AccountReconciliation>;
  detectInternalTransfers(payload: {
    ownerName?: string;
    ownerCpf?: string;
    dateToleranceDays: number;
    autoApply: boolean;
  }): Observable<InternalTransferSuggestion[]>;
  linkInternalTransfer(billId: string, incomeId: string): Observable<void>;
  runRetentionCleanup(): Observable<void>;
  emergencyResetAllData(keepBankAccounts: boolean): Observable<void>;
  importOfxStatementWithProgress(
    file: File,
    ownerName?: string,
    ownerCpf?: string
  ): Observable<OfxImportProgressEvent>;
  importOfxStatement(file: File, ownerName?: string, ownerCpf?: string): Observable<OfxImportResult>;
}

export const FINANCE_GATEWAY = new InjectionToken<FinanceGateway>('FINANCE_GATEWAY');
