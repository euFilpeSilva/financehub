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
  OfxImportResult,
  PlanningGoal,
  SpendingGoal,
  TrashItem
} from '../models/finance.models';

export interface FinanceGateway {
  listBills(): Observable<BillRecord[]>;
  createBill(payload: Omit<BillRecord, 'id'>): Observable<BillRecord>;
  updateBill(payload: BillRecord): Observable<BillRecord>;
  deleteBill(id: string): Observable<void>;

  listIncomes(): Observable<IncomeEntry[]>;
  createIncome(payload: Omit<IncomeEntry, 'id'>): Observable<IncomeEntry>;
  updateIncome(payload: IncomeEntry): Observable<IncomeEntry>;
  deleteIncome(id: string): Observable<void>;

  listGoals(): Observable<PlanningGoal[]>;
  createGoal(payload: Omit<PlanningGoal, 'id'>): Observable<PlanningGoal>;
  updateGoal(payload: PlanningGoal): Observable<PlanningGoal>;
  deleteGoal(id: string): Observable<void>;

  listBankAccounts(): Observable<BankAccount[]>;
  createBankAccount(payload: Omit<BankAccount, 'id'>): Observable<BankAccount>;
  updateBankAccount(payload: BankAccount): Observable<BankAccount>;
  deleteBankAccount(id: string): Observable<void>;

  listSpendingGoals(): Observable<SpendingGoal[]>;
  createSpendingGoal(payload: Omit<SpendingGoal, 'id'>): Observable<SpendingGoal>;
  updateSpendingGoal(payload: SpendingGoal): Observable<SpendingGoal>;
  deleteSpendingGoal(id: string): Observable<void>;

  listTrashItems(): Observable<TrashItem[]>;
  restoreTrashItem(trashId: string): Observable<void>;
  purgeTrashItem(trashId: string): Observable<void>;

  getRetentionSettings(): Observable<DataRetentionSettings>;
  updateRetentionSettings(payload: DataRetentionSettings): Observable<DataRetentionSettings>;
  getAppPreferences(): Observable<AppPreferences>;
  updateAppPreferences(payload: AppPreferences): Observable<AppPreferences>;

  listAuditEvents(): Observable<AuditEvent[]>;
  getDashboardSummary(startDate: string, endDate: string): Observable<DashboardSummary>;
  getAccountReconciliation(
    bankAccountId: string,
    startDate: string,
    endDate: string,
    referenceBalance?: number
  ): Observable<AccountReconciliation>;
  runRetentionCleanup(): Observable<void>;
  emergencyResetAllData(): Observable<void>;
  importOfxStatement(file: File, ownerName?: string, ownerCpf?: string): Observable<OfxImportResult>;
}

export const FINANCE_GATEWAY = new InjectionToken<FinanceGateway>('FINANCE_GATEWAY');
