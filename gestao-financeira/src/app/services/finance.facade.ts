import { computed, Inject, inject, Injectable, signal } from '@angular/core';
import { finalize, forkJoin, Observable, switchMap } from 'rxjs';
import {
  AccountReconciliation,
  AppPreferences,
  BankAccount,
  BillRecord,
  ComparisonSummary,
  DataRetentionSettings,
  IncomeEntry,
  OfxImportBatchProgress,
  OfxImportProgressEvent,
  OfxImportResult,
  PlanningGoal,
  SpendingGoal,
  SpendingGoalStatus
} from '../models/finance.models';
import { tap } from 'rxjs/operators';
import { FINANCE_GATEWAY, FinanceGateway } from './finance.gateway';
import {
  getCurrentMonth,
  resolveDashboardSummaryPeriod
} from './finance-period.utils';
import { BillsFacade } from './bills.facade';
import { IncomesFacade } from './incomes.facade';
import { DashboardAnalyticsFacade } from './dashboard-analytics.facade';
import { GovernanceFacade } from './governance.facade';
import { PlanningFacade } from './planning.facade';
import { BankAccountsFacade } from './bank-accounts.facade';

export interface OperationNotice {
  id: number;
  type: 'success' | 'error';
  message: string;
}

@Injectable({ providedIn: 'root' })
export class FinanceFacade {
  private static readonly DEFAULT_OFX_IMPORT_BATCH_PROGRESS: OfxImportBatchProgress = {
    visible: false,
    running: false,
    status: 'idle',
    currentFileName: '',
    currentFileProgress: 0,
    currentFilePhase: 'uploading',
    processedFiles: 0,
    totalFiles: 0,
    successCount: 0,
    failureCount: 0,
    overallProgress: 0
  };

  private readonly billsFacade = inject(BillsFacade);
  private readonly incomesFacade = inject(IncomesFacade);
  private readonly dashboardAnalyticsFacade = inject(DashboardAnalyticsFacade);
  private readonly governanceFacade = inject(GovernanceFacade);
  private readonly planningFacade = inject(PlanningFacade);
  private readonly bankAccountsFacade = inject(BankAccountsFacade);
  private readonly accountReconciliationSource = signal<AccountReconciliation | null>(null);
  private readonly lastOfxImportSource = signal<OfxImportResult | null>(null);
  private readonly ofxImportBatchProgressSource = signal<OfxImportBatchProgress>(
    FinanceFacade.DEFAULT_OFX_IMPORT_BATCH_PROGRESS
  );
  private readonly operationNoticeSource = signal<OperationNotice | null>(null);
  private noticeId = 0;

  readonly selectedMonth = signal(getCurrentMonth());
  readonly rangeStart = signal('');
  readonly rangeEnd = signal('');
  readonly loading = signal(false);
  readonly lastError = signal<string | null>(null);

  readonly filteredBills = this.billsFacade.filteredBills;
  readonly allBills = this.billsFacade.allBills;

  readonly filteredIncomes = this.incomesFacade.filteredIncomes;
  readonly allIncomes = this.incomesFacade.allIncomes;

  readonly allGoals = this.planningFacade.allGoals;

  readonly bankAccounts = this.bankAccountsFacade.bankAccounts;

  readonly spendingGoals = this.planningFacade.spendingGoals;

  readonly trashItems = this.governanceFacade.trashItems;
  readonly auditEvents = this.governanceFacade.auditEvents;
  readonly retentionSettings = this.governanceFacade.retentionSettings;
  readonly appPreferences = this.governanceFacade.appPreferences;
  readonly lastOfxImport = computed(() => this.lastOfxImportSource());
  readonly operationNotice = computed(() => this.operationNoticeSource());
  readonly accountReconciliation = computed(() => this.accountReconciliationSource());
  readonly ofxImportBatchProgress = computed(() => this.ofxImportBatchProgressSource());

  readonly availableExpenseCategories = this.billsFacade.availableExpenseCategories;

  readonly availableIncomeCategories = this.incomesFacade.availableIncomeCategories;

  readonly incomeTotal = this.dashboardAnalyticsFacade.incomeTotal;
  readonly expenseTotal = this.dashboardAnalyticsFacade.expenseTotal;
  readonly paidBillsTotal = this.dashboardAnalyticsFacade.paidBillsTotal;
  readonly pendingBillsTotal = this.dashboardAnalyticsFacade.pendingBillsTotal;
  readonly balance = this.dashboardAnalyticsFacade.balance;

  readonly goalsProgress = this.planningFacade.goalsProgress;

  readonly monthlyPerformance = this.dashboardAnalyticsFacade.monthlyPerformance;
  readonly bestSavingsMonth = this.dashboardAnalyticsFacade.bestSavingsMonth;
  readonly highestExpenseMonth = this.dashboardAnalyticsFacade.highestExpenseMonth;

  readonly spendingGoalStatuses = this.planningFacade.spendingGoalStatuses;

  readonly upcomingBills = computed(() =>
    this.filteredBills()
      .filter((item) => !item.paid)
      .slice(0, 5)
  );

  constructor(
    @Inject(FINANCE_GATEWAY) private readonly gateway: FinanceGateway
  ) {
    this.syncDomainContexts();
    this.loadAll();
  }

  loadAll(): void {
    this.execute(
      this.gateway.runRetentionCleanup().pipe(
        switchMap(() =>
          forkJoin({
            bills: this.gateway.listBills(),
            incomes: this.gateway.listIncomes(),
            goals: this.gateway.listGoals(),
            bankAccounts: this.gateway.listBankAccounts(),
            spendingGoals: this.gateway.listSpendingGoals(),
            spendingGoalStatuses: this.gateway.listSpendingGoalStatuses(this.selectedMonth()),
            trashItems: this.gateway.listTrashItems(),
            auditEvents: this.gateway.listAuditEvents(),
            retentionSettings: this.gateway.getRetentionSettings(),
            appPreferences: this.gateway.getAppPreferences()
          })
        )
      ),
      ({ bills, incomes, goals, bankAccounts, spendingGoals, spendingGoalStatuses, trashItems, auditEvents, retentionSettings, appPreferences }) => {
        this.billsFacade.setBills(bills);
        this.incomesFacade.setIncomes(incomes);
        this.planningFacade.setGoals(goals);
        this.bankAccountsFacade.setBankAccounts(bankAccounts);
        this.planningFacade.setSpendingGoals(spendingGoals);
        this.planningFacade.setSpendingGoalStatuses(spendingGoalStatuses);
        this.governanceFacade.setGovernanceData(trashItems, auditEvents, retentionSettings, appPreferences);
        this.syncDomainContexts();
        this.refreshDashboardSummary();
      },
      'Falha ao carregar dados financeiros.'
    );
  }

  setMonth(month: string): void {
    this.selectedMonth.set(month);
    this.syncDomainContexts();
    this.refreshSpendingGoalStatuses();
    this.refreshDashboardSummary();
  }

  setDateRange(start: string, end: string): void {
    this.rangeStart.set(start);
    this.rangeEnd.set(end);
    this.syncDomainContexts();
    this.refreshDashboardSummary();
  }

  clearDateRange(): void {
    this.rangeStart.set('');
    this.rangeEnd.set('');
    this.syncDomainContexts();
    this.refreshDashboardSummary();
  }

  addBill(payload: Omit<BillRecord, 'id'>): void {
    this.execute(
      this.gateway.createBill(payload),
      (created) => {
        this.billsFacade.addBill(created);
        this.refreshAuditEvents();
        this.refreshSpendingGoalStatuses();
        this.refreshDashboardSummary();
      },
      'Falha ao adicionar conta.',
      'Conta adicionada com sucesso.'
    );
  }

  updateBill(payload: BillRecord): void {
    this.execute(
      this.gateway.updateBill(payload),
      (updated) => {
        this.billsFacade.updateBill(updated);
        this.refreshAuditEvents();
        this.refreshSpendingGoalStatuses();
        this.refreshDashboardSummary();
      },
      'Falha ao atualizar conta.',
      'Conta atualizada com sucesso.'
    );
  }

  deleteBill(id: string): void {
    this.execute(
      this.gateway.deleteBill(id),
      () => {
        this.billsFacade.removeBill(id);
        this.refreshGovernanceData();
        this.refreshSpendingGoalStatuses();
        this.refreshDashboardSummary();
      },
      'Falha ao excluir conta.',
      'Conta excluida com sucesso.'
    );
  }

  toggleBillPaid(bill: BillRecord): void {
    this.updateBill({ ...bill, paid: !bill.paid });
  }

  addIncome(payload: Omit<IncomeEntry, 'id'>): void {
    this.execute(
      this.gateway.createIncome(payload),
      (created) => {
        this.incomesFacade.addIncome(created);
        this.refreshAuditEvents();
        this.refreshDashboardSummary();
      },
      'Falha ao adicionar entrada.',
      'Entrada adicionada com sucesso.'
    );
  }

  updateIncome(payload: IncomeEntry): void {
    this.execute(
      this.gateway.updateIncome(payload),
      (updated) => {
        this.incomesFacade.updateIncome(updated);
        this.refreshAuditEvents();
        this.refreshDashboardSummary();
      },
      'Falha ao atualizar entrada.',
      'Entrada atualizada com sucesso.'
    );
  }

  deleteIncome(id: string): void {
    this.execute(
      this.gateway.deleteIncome(id),
      () => {
        this.incomesFacade.removeIncome(id);
        this.refreshGovernanceData();
        this.refreshDashboardSummary();
      },
      'Falha ao excluir entrada.',
      'Entrada excluida com sucesso.'
    );
  }

  addGoal(payload: Omit<PlanningGoal, 'id'>): void {
    this.execute(
      this.gateway.createGoal(payload),
      (created) => {
        this.planningFacade.addGoal(created);
        this.refreshAuditEvents();
      },
      'Falha ao criar meta.',
      'Meta criada com sucesso.'
    );
  }

  addBankAccount(payload: Omit<BankAccount, 'id'>): void {
    this.execute(
      this.gateway.createBankAccount(payload),
      (created) => {
        this.bankAccountsFacade.addBankAccount(created);
        this.refreshAuditEvents();
      },
      'Falha ao criar conta bancaria.',
      'Conta bancaria criada com sucesso.'
    );
  }

  updateBankAccount(payload: BankAccount): void {
    this.execute(
      this.gateway.updateBankAccount(payload),
      (updated) => {
        this.bankAccountsFacade.updateBankAccount(updated);
        this.refreshAuditEvents();
      },
      'Falha ao atualizar conta bancaria.',
      'Conta bancaria atualizada com sucesso.'
    );
  }

  deleteBankAccount(id: string): void {
    this.execute(
      this.gateway.deleteBankAccount(id),
      () => {
        this.bankAccountsFacade.removeBankAccount(id);
        this.refreshAuditEvents();
      },
      'Falha ao remover conta bancaria.',
      'Conta bancaria removida com sucesso.'
    );
  }

  updateGoal(payload: PlanningGoal): void {
    this.execute(
      this.gateway.updateGoal(payload),
      (updated) => {
        this.planningFacade.updateGoal(updated);
        this.refreshAuditEvents();
      },
      'Falha ao atualizar meta.',
      'Meta atualizada com sucesso.'
    );
  }

  deleteGoal(id: string): void {
    this.execute(
      this.gateway.deleteGoal(id),
      () => {
        this.planningFacade.removeGoal(id);
        this.refreshGovernanceData();
      },
      'Falha ao excluir meta.',
      'Meta excluida com sucesso.'
    );
  }

  addSpendingGoal(payload: Omit<SpendingGoal, 'id'>): void {
    this.execute(
      this.gateway.createSpendingGoal(payload),
      (created) => {
        this.planningFacade.addSpendingGoal(created);
        this.refreshAuditEvents();
        this.refreshSpendingGoalStatuses();
      },
      'Falha ao criar meta de gasto.',
      'Meta de gasto criada com sucesso.'
    );
  }

  updateSpendingGoal(payload: SpendingGoal): void {
    this.execute(
      this.gateway.updateSpendingGoal(payload),
      (updated) => {
        this.planningFacade.updateSpendingGoal(updated);
        this.refreshAuditEvents();
        this.refreshSpendingGoalStatuses();
      },
      'Falha ao atualizar meta de gasto.',
      'Meta de gasto atualizada com sucesso.'
    );
  }

  deleteSpendingGoal(id: string): void {
    this.execute(
      this.gateway.deleteSpendingGoal(id),
      () => {
        this.planningFacade.removeSpendingGoal(id);
        this.refreshGovernanceData();
        this.refreshSpendingGoalStatuses();
      },
      'Falha ao excluir meta de gasto.',
      'Meta de gasto excluida com sucesso.'
    );
  }

  toggleSpendingGoal(goal: SpendingGoal): void {
    this.updateSpendingGoal({ ...goal, active: !goal.active });
  }

  restoreTrashItem(trashId: string): void {
    this.execute(
      this.gateway.restoreTrashItem(trashId),
      () => this.refreshEntityAndGovernanceData(),
      'Falha ao restaurar item.',
      'Item restaurado com sucesso.'
    );
  }

  purgeTrashItem(trashId: string): void {
    this.execute(
      this.gateway.purgeTrashItem(trashId),
      () => this.refreshGovernanceData(),
      'Falha ao excluir item permanentemente.',
      'Item excluido permanentemente.'
    );
  }

  updateRetentionSettings(payload: DataRetentionSettings): void {
    this.execute(
      this.gateway.updateRetentionSettings(payload),
      (updated) => {
        this.governanceFacade.setRetentionSettings(updated);
        this.refreshGovernanceData();
      },
      'Falha ao salvar configuracoes de retencao.',
      'Configuracoes de retencao salvas com sucesso.'
    );
  }

  updateAppPreferences(payload: AppPreferences): void {
    this.execute(
      this.gateway.updateAppPreferences(payload),
      (updated) => {
        this.governanceFacade.setAppPreferences(updated);
        this.syncDomainContexts();
        this.refreshAuditEvents();
      },
      'Falha ao salvar padroes da aplicacao.',
      'Padroes da aplicacao salvos com sucesso.'
    );
  }

  importOfxStatement(file: File, ownerName?: string, ownerCpf?: string): void {
    this.execute(
      this.gateway.importOfxStatement(file, ownerName, ownerCpf),
      (result) => {
        this.lastOfxImportSource.set(result);
        this.loadAll();
      },
      'Falha ao importar OFX.',
      'OFX importado com sucesso.'
    );
  }

  importOfxStatementWithProgress(
    file: File,
    ownerName?: string,
    ownerCpf?: string
  ): Observable<OfxImportProgressEvent> {
    this.loading.set(true);
    this.lastError.set(null);

    return this.gateway.importOfxStatementWithProgress(file, ownerName, ownerCpf).pipe(
      tap({
        next: (event) => {
          if (event.kind !== 'completed') {
            return;
          }
          this.lastOfxImportSource.set(event.result);
          this.loadAll();
        },
        error: () => {
          this.lastError.set('Falha ao importar OFX.');
        }
      }),
      finalize(() => {
        this.loading.set(false);
      })
    );
  }

  async importOfxBatch(
    files: File[],
    ownerName?: string,
    ownerCpf?: string
  ): Promise<{ total: number; successCount: number; failureCount: number }> {
    if (!files.length) {
      return { total: 0, successCount: 0, failureCount: 0 };
    }

    const current = this.ofxImportBatchProgressSource();
    if (current.running) {
      return {
        total: current.totalFiles,
        successCount: current.successCount,
        failureCount: current.failureCount
      };
    }

    this.ofxImportBatchProgressSource.set({
      visible: true,
      running: true,
      status: 'running',
      currentFileName: '',
      currentFileProgress: 0,
      currentFilePhase: 'uploading',
      processedFiles: 0,
      totalFiles: files.length,
      successCount: 0,
      failureCount: 0,
      overallProgress: 0
    });

    let successCount = 0;
    let failureCount = 0;

    for (const file of files) {
      this.ofxImportBatchProgressSource.update((state) => ({
        ...state,
        currentFileName: file.name,
        currentFileProgress: 0,
        currentFilePhase: 'uploading'
      }));

      try {
        await this.runSingleOfxImportWithProgress(file, ownerName, ownerCpf);
        successCount += 1;
      } catch {
        failureCount += 1;
      } finally {
        const processedFiles = successCount + failureCount;
        this.ofxImportBatchProgressSource.update((state) => ({
          ...state,
          processedFiles,
          successCount,
          failureCount,
          overallProgress: state.totalFiles > 0
            ? Math.round((processedFiles / state.totalFiles) * 100)
            : 100
        }));
      }
    }

    if (successCount > 0) {
      this.loadAll();
    }

    const status = failureCount === 0
      ? 'success'
      : successCount === 0
        ? 'error'
        : 'partial';

    this.ofxImportBatchProgressSource.update((state) => ({
      ...state,
      running: false,
      status,
      currentFilePhase: 'completed',
      currentFileProgress: 100,
      overallProgress: 100
    }));

    return { total: files.length, successCount, failureCount };
  }

  dismissOfxImportWidget(): void {
    this.ofxImportBatchProgressSource.update((state) => {
      if (state.running) {
        return state;
      }
      return {
        ...FinanceFacade.DEFAULT_OFX_IMPORT_BATCH_PROGRESS,
        visible: false
      };
    });
  }

  private runSingleOfxImportWithProgress(
    file: File,
    ownerName?: string,
    ownerCpf?: string
  ): Promise<void> {
    return new Promise((resolve, reject) => {
      const subscription = this.gateway.importOfxStatementWithProgress(file, ownerName, ownerCpf).subscribe({
        next: (event) => {
          if (event.kind === 'completed') {
            this.lastOfxImportSource.set(event.result);
            this.ofxImportBatchProgressSource.update((state) => ({
              ...state,
              currentFileName: event.fileName,
              currentFileProgress: 100,
              currentFilePhase: 'completed',
              overallProgress: this.calculateOverallProgress(state.processedFiles, state.totalFiles, 1)
            }));
            subscription.unsubscribe();
            resolve();
            return;
          }

          const currentFraction = Math.min(Math.max(event.progress, 0), 99) / 100;
          this.ofxImportBatchProgressSource.update((state) => ({
            ...state,
            currentFileName: event.fileName,
            currentFileProgress: event.progress,
            currentFilePhase: event.phase,
            overallProgress: this.calculateOverallProgress(state.processedFiles, state.totalFiles, currentFraction)
          }));
        },
        error: (error) => {
          subscription.unsubscribe();
          reject(error);
        }
      });
    });
  }

  private calculateOverallProgress(processedFiles: number, totalFiles: number, currentFraction: number): number {
    if (totalFiles <= 0) {
      return 0;
    }
    return Math.min(100, Math.round(((processedFiles + currentFraction) / totalFiles) * 100));
  }

  reconcileAccount(
    bankAccountId: string,
    startDate: string,
    endDate: string,
    referenceBalance?: number
  ): void {
    this.execute(
      this.gateway.getAccountReconciliation(bankAccountId, startDate, endDate, referenceBalance),
      (result) => this.accountReconciliationSource.set(result),
      'Falha ao calcular conciliacao por conta.'
    );
  }

  emergencyResetAllData(keepBankAccounts: boolean): void {
    this.execute(
      this.gateway.emergencyResetAllData(keepBankAccounts),
      () => this.loadAll(),
      'Falha ao zerar os dados da base.',
      'Base de dados zerada com sucesso.'
    );
  }

  updateGoalProgress(goal: PlanningGoal, currentAmount: number): void {
    const rounded = Math.max(0, Number(currentAmount.toFixed(2)));
    this.updateGoal({ ...goal, currentAmount: rounded });
  }

  toggleGoalStatus(goal: PlanningGoal): void {
    const currentAmount = goal.complete
      ? Math.max(0, Number((goal.targetAmount - 0.01).toFixed(2)))
      : goal.targetAmount;
    this.updateGoal({ ...goal, currentAmount });
  }

  createRecurringBillsForCurrentMonth(): void {
    const currentMonth = this.selectedMonth();
    this.execute(
      this.gateway.createRecurringBills(currentMonth),
      (createdBills) => {
        if (!createdBills.length) {
          return;
        }
        this.billsFacade.addBills(createdBills);
        this.refreshAuditEvents();
        this.refreshSpendingGoalStatuses();
        this.refreshDashboardSummary();
        this.emitNotice('success', 'Contas recorrentes geradas com sucesso.');
      },
      'Falha ao gerar contas recorrentes.'
    );
  }

  clearOperationNotice(): void {
    this.operationNoticeSource.set(null);
  }

  compareMonths(firstMonth: string, secondMonth: string): ComparisonSummary {
    return this.dashboardAnalyticsFacade.compareMonths(firstMonth, secondMonth);
  }

  compareRanges(
    firstStart: string,
    firstEnd: string,
    secondStart: string,
    secondEnd: string
  ): ComparisonSummary {
    return this.dashboardAnalyticsFacade.compareRanges(firstStart, firstEnd, secondStart, secondEnd);
  }

  private refreshAuditEvents(): void {
    this.gateway.listAuditEvents().subscribe({
      next: (auditEvents) => this.governanceFacade.setAuditEvents(auditEvents)
    });
  }

  private refreshGovernanceData(): void {
    forkJoin({
      trashItems: this.gateway.listTrashItems(),
      auditEvents: this.gateway.listAuditEvents(),
      retentionSettings: this.gateway.getRetentionSettings(),
      appPreferences: this.gateway.getAppPreferences()
    }).subscribe({
      next: ({ trashItems, auditEvents, retentionSettings, appPreferences }) => {
        this.governanceFacade.setGovernanceData(trashItems, auditEvents, retentionSettings, appPreferences);
        this.syncDomainContexts();
      }
    });
  }

  private refreshEntityAndGovernanceData(): void {
    forkJoin({
      bills: this.gateway.listBills(),
      incomes: this.gateway.listIncomes(),
      goals: this.gateway.listGoals(),
      bankAccounts: this.gateway.listBankAccounts(),
      spendingGoals: this.gateway.listSpendingGoals(),
      spendingGoalStatuses: this.gateway.listSpendingGoalStatuses(this.selectedMonth()),
      trashItems: this.gateway.listTrashItems(),
      auditEvents: this.gateway.listAuditEvents(),
      retentionSettings: this.gateway.getRetentionSettings(),
      appPreferences: this.gateway.getAppPreferences()
    }).subscribe({
      next: ({ bills, incomes, goals, bankAccounts, spendingGoals, spendingGoalStatuses, trashItems, auditEvents, retentionSettings, appPreferences }) => {
        this.billsFacade.setBills(bills);
        this.incomesFacade.setIncomes(incomes);
        this.planningFacade.setGoals(goals);
        this.bankAccountsFacade.setBankAccounts(bankAccounts);
        this.planningFacade.setSpendingGoals(spendingGoals);
        this.planningFacade.setSpendingGoalStatuses(spendingGoalStatuses);
        this.governanceFacade.setGovernanceData(trashItems, auditEvents, retentionSettings, appPreferences);
        this.syncDomainContexts();
        this.refreshDashboardSummary();
      }
    });
  }

  private syncDomainContexts(): void {
    this.billsFacade.setPeriod(this.selectedMonth(), this.rangeStart(), this.rangeEnd());
    this.billsFacade.setAppPreferences(this.governanceFacade.appPreferences());
    this.incomesFacade.setPeriod(this.selectedMonth(), this.rangeStart(), this.rangeEnd());
    this.incomesFacade.setAppPreferences(this.governanceFacade.appPreferences());
    this.dashboardAnalyticsFacade.setData(this.billsFacade.snapshot(), this.incomesFacade.snapshot());
  }

  private refreshSpendingGoalStatuses(): void {
    this.gateway.listSpendingGoalStatuses(this.selectedMonth()).subscribe({
      next: (statuses) => this.planningFacade.setSpendingGoalStatuses(statuses)
    });
  }

  private refreshDashboardSummary(): void {
    const { startDate, endDate } = resolveDashboardSummaryPeriod(this.selectedMonth(), this.rangeStart(), this.rangeEnd());
    this.gateway.getDashboardSummary(startDate, endDate).subscribe({
      next: (summary) => this.dashboardAnalyticsFacade.setDashboardSummary(summary)
    });
  }

  private execute<T>(
    operation: Observable<T>,
    onSuccess: (value: T) => void,
    errorMessage = 'Nao foi possivel salvar a alteracao.',
    successMessage?: string
  ): void {
    this.loading.set(true);
    this.lastError.set(null);
    operation.pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (value) => {
        onSuccess(value);
        if (successMessage) {
          this.emitNotice('success', successMessage);
        }
      },
      error: () => {
        this.lastError.set(errorMessage);
        this.emitNotice('error', errorMessage);
      }
    });
  }

  private emitNotice(type: OperationNotice['type'], message: string): void {
    this.noticeId += 1;
    this.operationNoticeSource.set({ id: this.noticeId, type, message });
  }

}
