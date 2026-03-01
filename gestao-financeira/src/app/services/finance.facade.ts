import { computed, Inject, Injectable, signal } from '@angular/core';
import { finalize, forkJoin, Observable, switchMap } from 'rxjs';
import {
  AccountReconciliation,
  AppPreferences,
  AuditEvent,
  BankAccount,
  BillRecord,
  ComparisonSummary,
  DataRetentionSettings,
  DashboardSummary,
  IncomeEntry,
  MonthlyPerformance,
  OfxImportResult,
  PeriodSnapshot,
  PlanningGoal,
  SpendingGoal,
  SpendingGoalStatus,
  TrashItem
} from '../models/finance.models';
import { FINANCE_GATEWAY, FinanceGateway } from './finance.gateway';

export interface OperationNotice {
  id: number;
  type: 'success' | 'error';
  message: string;
}

@Injectable({ providedIn: 'root' })
export class FinanceFacade {
  private static readonly DEFAULT_BILL_CATEGORIES = [
    'Moradia',
    'Alimentacao',
    'Utilidades',
    'Saude',
    'Transporte',
    'Educacao',
    'Lazer',
    'Outros'
  ];
  private static readonly DEFAULT_INCOME_CATEGORIES = ['Trabalho', 'Extra', 'Investimentos', 'Reembolso', 'Outros'];

  private readonly bills = signal<BillRecord[]>([]);
  private readonly incomes = signal<IncomeEntry[]>([]);
  private readonly goals = signal<PlanningGoal[]>([]);
  private readonly bankAccountsSource = signal<BankAccount[]>([]);
  private readonly spendingGoalsSource = signal<SpendingGoal[]>([]);
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
    billCategories: ['Moradia', 'Alimentacao', 'Utilidades', 'Saude', 'Transporte', 'Educacao', 'Lazer', 'Outros'],
    incomeCategories: ['Trabalho', 'Extra', 'Investimentos', 'Reembolso', 'Outros']
  });
  private readonly dashboardSummarySource = signal<DashboardSummary>({
    incomeTotal: 0,
    expenseTotal: 0,
    balance: 0,
    paidBillsTotal: 0,
    pendingBillsTotal: 0
  });
  private readonly accountReconciliationSource = signal<AccountReconciliation | null>(null);
  private readonly lastOfxImportSource = signal<OfxImportResult | null>(null);
  private readonly operationNoticeSource = signal<OperationNotice | null>(null);
  private noticeId = 0;

  readonly selectedMonth = signal(this.getCurrentMonth());
  readonly rangeStart = signal('');
  readonly rangeEnd = signal('');
  readonly loading = signal(false);
  readonly lastError = signal<string | null>(null);

  readonly filteredBills = computed(() =>
    this.bills()
      .filter((bill) => this.matchesMonthAndRange(bill.dueDate))
      .sort((a, b) => a.dueDate.localeCompare(b.dueDate))
  );
  readonly allBills = computed(() =>
    this.bills().slice().sort((a, b) => a.dueDate.localeCompare(b.dueDate))
  );

  readonly filteredIncomes = computed(() =>
    this.incomes()
      .filter((income) => this.matchesMonthAndRange(income.receivedAt))
      .sort((a, b) => a.receivedAt.localeCompare(b.receivedAt))
  );
  readonly allIncomes = computed(() =>
    this.incomes().slice().sort((a, b) => a.receivedAt.localeCompare(b.receivedAt))
  );

  readonly allGoals = computed(() => this.goals().sort((a, b) => a.targetDate.localeCompare(b.targetDate)));

  readonly bankAccounts = computed(() =>
    this.bankAccountsSource().slice().sort((a, b) => a.label.localeCompare(b.label))
  );

  readonly spendingGoals = computed(() =>
    this.spendingGoalsSource().sort((a, b) => a.title.localeCompare(b.title))
  );

  readonly trashItems = computed(() =>
    this.trashItemsSource().sort((a, b) => b.deletedAt.localeCompare(a.deletedAt))
  );

  readonly auditEvents = computed(() =>
    this.auditEventsSource().sort((a, b) => b.timestamp.localeCompare(a.timestamp))
  );

  readonly retentionSettings = computed(() => this.retentionSettingsSource());
  readonly appPreferences = computed(() => this.appPreferencesSource());
  readonly lastOfxImport = computed(() => this.lastOfxImportSource());
  readonly operationNotice = computed(() => this.operationNoticeSource());
  readonly accountReconciliation = computed(() => this.accountReconciliationSource());

  readonly availableExpenseCategories = computed(() =>
    Array.from(
      new Set([
        ...this.safeBillCategories(this.appPreferences()),
        ...this.bills().map((item) => item.category)
      ])
    ).sort((a, b) => a.localeCompare(b))
  );

  readonly availableIncomeCategories = computed(() =>
    Array.from(
      new Set([
        ...this.safeIncomeCategories(this.appPreferences()),
        ...this.incomes().map((item) => item.category)
      ])
    ).sort((a, b) => a.localeCompare(b))
  );

  readonly incomeTotal = computed(() =>
    this.dashboardSummarySource().incomeTotal
  );

  readonly expenseTotal = computed(() =>
    this.dashboardSummarySource().expenseTotal
  );

  readonly paidBillsTotal = computed(() =>
    this.dashboardSummarySource().paidBillsTotal
  );

  readonly pendingBillsTotal = computed(() =>
    this.dashboardSummarySource().pendingBillsTotal
  );

  readonly balance = computed(() => this.dashboardSummarySource().balance);

  readonly goalsProgress = computed(() => {
    const list = this.allGoals();
    if (!list.length) {
      return 0;
    }
    const progressSum = list.reduce((sum, goal) => {
      if (!goal.targetAmount) {
        return sum;
      }
      return sum + Math.min(100, (goal.currentAmount / goal.targetAmount) * 100);
    }, 0);
    return Math.round(progressSum / list.length);
  });

  readonly monthlyPerformance = computed<MonthlyPerformance[]>(() => {
    const months = new Set<string>();
    for (const bill of this.bills()) {
      months.add(bill.dueDate.slice(0, 7));
    }
    for (const income of this.incomes()) {
      months.add(income.receivedAt.slice(0, 7));
    }

    return Array.from(months)
      .sort((a, b) => a.localeCompare(b))
      .map((month) => {
        const income = this.incomes()
          .filter((item) => !item.internalTransfer)
          .filter((item) => item.receivedAt.startsWith(month))
          .reduce((sum, item) => sum + item.amount, 0);
        const expenses = this.bills()
          .filter((item) => !item.internalTransfer)
          .filter((item) => item.dueDate.startsWith(month))
          .reduce((sum, item) => sum + item.amount, 0);
        return {
          month,
          income,
          expenses,
          savings: income - expenses
        };
      });
  });

  readonly bestSavingsMonth = computed<MonthlyPerformance | null>(() => {
    const series = this.monthlyPerformance();
    if (!series.length) {
      return null;
    }
    return series.reduce((best, current) => (current.savings > best.savings ? current : best));
  });

  readonly highestExpenseMonth = computed<MonthlyPerformance | null>(() => {
    const series = this.monthlyPerformance();
    if (!series.length) {
      return null;
    }
    return series.reduce((worst, current) => (current.expenses > worst.expenses ? current : worst));
  });

  readonly spendingGoalStatuses = computed<SpendingGoalStatus[]>(() =>
    this.spendingGoals().map((goal) => {
      const period = this.resolveGoalPeriod(goal);
      const spentAmount = this.calculateExpenseInPeriod(period.start, period.end, goal.category);
      const remainingAmount = goal.limitAmount - spentAmount;
      const usagePercent = goal.limitAmount ? (spentAmount / goal.limitAmount) * 100 : 0;
      return {
        goal,
        spentAmount,
        remainingAmount,
        usagePercent,
        onTrack: spentAmount <= goal.limitAmount,
        periodLabel: period.label
      };
    })
  );

  readonly upcomingBills = computed(() =>
    this.filteredBills()
      .filter((item) => !item.paid)
      .slice(0, 5)
  );

  constructor(@Inject(FINANCE_GATEWAY) private readonly gateway: FinanceGateway) {
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
            trashItems: this.gateway.listTrashItems(),
            auditEvents: this.gateway.listAuditEvents(),
            retentionSettings: this.gateway.getRetentionSettings(),
            appPreferences: this.gateway.getAppPreferences()
          })
        )
      ),
      ({ bills, incomes, goals, bankAccounts, spendingGoals, trashItems, auditEvents, retentionSettings, appPreferences }) => {
        this.bills.set(bills);
        this.incomes.set(incomes);
        this.goals.set(goals);
        this.bankAccountsSource.set(bankAccounts);
        this.spendingGoalsSource.set(spendingGoals);
        this.trashItemsSource.set(trashItems);
        this.auditEventsSource.set(auditEvents);
        this.retentionSettingsSource.set(retentionSettings);
        this.appPreferencesSource.set(this.normalizePreferences(appPreferences));
        this.refreshDashboardSummary();
      },
      'Falha ao carregar dados financeiros.'
    );
  }

  setMonth(month: string): void {
    this.selectedMonth.set(month);
    this.refreshDashboardSummary();
  }

  setDateRange(start: string, end: string): void {
    this.rangeStart.set(start);
    this.rangeEnd.set(end);
    this.refreshDashboardSummary();
  }

  clearDateRange(): void {
    this.rangeStart.set('');
    this.rangeEnd.set('');
    this.refreshDashboardSummary();
  }

  addBill(payload: Omit<BillRecord, 'id'>): void {
    this.execute(
      this.gateway.createBill(payload),
      (created) => {
        this.bills.update((list) => [...list, created]);
        this.refreshAuditEvents();
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
        this.bills.update((list) => list.map((item) => (item.id === updated.id ? updated : item)));
        this.refreshAuditEvents();
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
        this.bills.update((list) => list.filter((item) => item.id !== id));
        this.refreshGovernanceData();
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
        this.incomes.update((list) => [...list, created]);
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
        this.incomes.update((list) => list.map((item) => (item.id === updated.id ? updated : item)));
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
        this.incomes.update((list) => list.filter((item) => item.id !== id));
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
        this.goals.update((list) => [...list, created]);
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
        this.bankAccountsSource.update((list) => [...list, created]);
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
        this.bankAccountsSource.update((list) => list.map((item) => (item.id === updated.id ? updated : item)));
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
        this.bankAccountsSource.update((list) => list.filter((item) => item.id !== id));
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
        this.goals.update((list) => list.map((item) => (item.id === updated.id ? updated : item)));
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
        this.goals.update((list) => list.filter((item) => item.id !== id));
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
        this.spendingGoalsSource.update((list) => [...list, created]);
        this.refreshAuditEvents();
      },
      'Falha ao criar meta de gasto.',
      'Meta de gasto criada com sucesso.'
    );
  }

  updateSpendingGoal(payload: SpendingGoal): void {
    this.execute(
      this.gateway.updateSpendingGoal(payload),
      (updated) => {
        this.spendingGoalsSource.update((list) => list.map((item) => (item.id === updated.id ? updated : item)));
        this.refreshAuditEvents();
      },
      'Falha ao atualizar meta de gasto.',
      'Meta de gasto atualizada com sucesso.'
    );
  }

  deleteSpendingGoal(id: string): void {
    this.execute(
      this.gateway.deleteSpendingGoal(id),
      () => {
        this.spendingGoalsSource.update((list) => list.filter((item) => item.id !== id));
        this.refreshGovernanceData();
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
        this.retentionSettingsSource.set(updated);
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
        this.appPreferencesSource.set(this.normalizePreferences(updated));
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

  emergencyResetAllData(): void {
    this.execute(
      this.gateway.emergencyResetAllData(),
      () => this.loadAll(),
      'Falha ao zerar os dados da base.',
      'Base de dados zerada com sucesso.'
    );
  }

  updateGoalProgress(goal: PlanningGoal, currentAmount: number): void {
    const rounded = Math.max(0, Number(currentAmount.toFixed(2)));
    const complete = rounded >= goal.targetAmount;
    this.updateGoal({ ...goal, currentAmount: rounded, complete });
  }

  toggleGoalStatus(goal: PlanningGoal): void {
    this.updateGoal({ ...goal, complete: !goal.complete });
  }

  createRecurringBillsForCurrentMonth(): void {
    const currentMonth = this.selectedMonth();
    const recurringTemplates = this.bills().filter((item) => item.recurring);

    const createRequests = recurringTemplates
      .filter((template) => {
        const alreadyExists = this.bills().some(
          (item) =>
            item.description.toLowerCase() === template.description.toLowerCase() &&
            item.category === template.category &&
            item.dueDate.startsWith(currentMonth)
        );
        return !alreadyExists;
      })
      .map((template) => {
        const templateDay = Number(template.dueDate.slice(8, 10));
        return this.gateway.createBill({
          description: template.description,
          category: template.category,
          amount: template.amount,
          dueDate: this.createSafeDate(currentMonth, templateDay),
          recurring: true,
          paid: false,
          internalTransfer: false
        });
      });

    if (!createRequests.length) {
      return;
    }

    this.execute(
      forkJoin(createRequests),
      (createdBills) => {
        this.bills.update((list) => [...list, ...createdBills]);
        this.refreshAuditEvents();
        this.refreshDashboardSummary();
      },
      'Falha ao gerar contas recorrentes.',
      'Contas recorrentes geradas com sucesso.'
    );
  }

  clearOperationNotice(): void {
    this.operationNoticeSource.set(null);
  }

  compareMonths(firstMonth: string, secondMonth: string): ComparisonSummary {
    const first = this.getMonthSnapshot(firstMonth);
    const second = this.getMonthSnapshot(secondMonth);
    return this.buildComparison(firstMonth, secondMonth, first, second);
  }

  compareRanges(
    firstStart: string,
    firstEnd: string,
    secondStart: string,
    secondEnd: string
  ): ComparisonSummary {
    const first = this.getRangeSnapshot(firstStart, firstEnd);
    const second = this.getRangeSnapshot(secondStart, secondEnd);
    return this.buildComparison(`${firstStart} a ${firstEnd}`, `${secondStart} a ${secondEnd}`, first, second);
  }

  getMonthSnapshot(month: string): PeriodSnapshot {
    const income = this.incomes()
      .filter((item) => !item.internalTransfer)
      .filter((item) => item.receivedAt.startsWith(month))
      .reduce((sum, item) => sum + item.amount, 0);
    const expenses = this.bills()
      .filter((item) => !item.internalTransfer)
      .filter((item) => item.dueDate.startsWith(month))
      .reduce((sum, item) => sum + item.amount, 0);
    return { income, expenses, savings: income - expenses };
  }

  getRangeSnapshot(start: string, end: string): PeriodSnapshot {
    const safeStart = start || '0000-01-01';
    const safeEnd = end || '9999-12-31';
    const income = this.incomes()
      .filter((item) => !item.internalTransfer)
      .filter((item) => item.receivedAt >= safeStart && item.receivedAt <= safeEnd)
      .reduce((sum, item) => sum + item.amount, 0);
    const expenses = this.bills()
      .filter((item) => !item.internalTransfer)
      .filter((item) => item.dueDate >= safeStart && item.dueDate <= safeEnd)
      .reduce((sum, item) => sum + item.amount, 0);
    return { income, expenses, savings: income - expenses };
  }

  private calculateExpenseInPeriod(start: string, end: string, category: string): number {
    return this.bills()
      .filter((item) => !item.internalTransfer)
      .filter((item) => item.dueDate >= start && item.dueDate <= end)
      .filter((item) => category === 'ALL' || item.category === category)
      .reduce((sum, item) => sum + item.amount, 0);
  }

  private resolveGoalPeriod(goal: SpendingGoal): { start: string; end: string; label: string } {
    if (goal.schedule === 'custom') {
      const start = goal.startDate || this.selectedMonth() + '-01';
      const end = goal.endDate || this.lastDayOfMonth(this.selectedMonth());
      return { start, end, label: `${start} a ${end}` };
    }

    const selectedMonth = this.selectedMonth();
    const startMonth = goal.startMonth || selectedMonth;
    const month = selectedMonth >= startMonth ? selectedMonth : startMonth;
    return {
      start: `${month}-01`,
      end: this.lastDayOfMonth(month),
      label: `Mensal (${month})`
    };
  }

  private buildComparison(
    firstLabel: string,
    secondLabel: string,
    first: PeriodSnapshot,
    second: PeriodSnapshot
  ): ComparisonSummary {
    return {
      firstLabel,
      secondLabel,
      first,
      second,
      incomeDelta: second.income - first.income,
      expenseDelta: second.expenses - first.expenses,
      savingsDelta: second.savings - first.savings
    };
  }

  private matchesMonthAndRange(date: string): boolean {
    const monthMatch = date.startsWith(this.selectedMonth());
    const start = this.rangeStart();
    const end = this.rangeEnd();

    if (!start && !end) {
      return monthMatch;
    }

    if (start && date < start) {
      return false;
    }

    if (end && date > end) {
      return false;
    }

    return true;
  }

  private createSafeDate(month: string, day: number): string {
    const [year, monthNumber] = month.split('-').map((value) => Number(value));
    const maxDay = new Date(year, monthNumber, 0).getDate();
    const safeDay = Math.max(1, Math.min(day, maxDay));
    return `${month}-${safeDay.toString().padStart(2, '0')}`;
  }

  private lastDayOfMonth(month: string): string {
    const [year, monthNumber] = month.split('-').map((value) => Number(value));
    const last = new Date(year, monthNumber, 0).getDate();
    return `${month}-${last.toString().padStart(2, '0')}`;
  }

  private getCurrentMonth(): string {
    const today = new Date();
    const year = today.getFullYear();
    const month = (today.getMonth() + 1).toString().padStart(2, '0');
    return `${year}-${month}`;
  }

  private refreshAuditEvents(): void {
    this.gateway.listAuditEvents().subscribe({
      next: (auditEvents) => this.auditEventsSource.set(auditEvents)
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
        this.trashItemsSource.set(trashItems);
        this.auditEventsSource.set(auditEvents);
        this.retentionSettingsSource.set(retentionSettings);
        this.appPreferencesSource.set(this.normalizePreferences(appPreferences));
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
      trashItems: this.gateway.listTrashItems(),
      auditEvents: this.gateway.listAuditEvents(),
      retentionSettings: this.gateway.getRetentionSettings(),
      appPreferences: this.gateway.getAppPreferences()
    }).subscribe({
      next: ({ bills, incomes, goals, bankAccounts, spendingGoals, trashItems, auditEvents, retentionSettings, appPreferences }) => {
        this.bills.set(bills);
        this.incomes.set(incomes);
        this.goals.set(goals);
        this.bankAccountsSource.set(bankAccounts);
        this.spendingGoalsSource.set(spendingGoals);
        this.trashItemsSource.set(trashItems);
        this.auditEventsSource.set(auditEvents);
        this.retentionSettingsSource.set(retentionSettings);
        this.appPreferencesSource.set(this.normalizePreferences(appPreferences));
        this.refreshDashboardSummary();
      }
    });
  }

  private refreshDashboardSummary(): void {
    const { startDate, endDate } = this.resolveDashboardSummaryPeriod();
    this.gateway.getDashboardSummary(startDate, endDate).subscribe({
      next: (summary) => this.dashboardSummarySource.set(summary)
    });
  }

  private resolveDashboardSummaryPeriod(): { startDate: string; endDate: string } {
    const start = this.rangeStart();
    const end = this.rangeEnd();
    if (start || end) {
      return {
        startDate: start || '0001-01-01',
        endDate: end || '9999-12-31'
      };
    }
    const month = this.selectedMonth();
    return {
      startDate: `${month}-01`,
      endDate: this.lastDayOfMonth(month)
    };
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

  private normalizePreferences(payload: AppPreferences): AppPreferences {
    const billCategories = this.safeBillCategories(payload);
    const incomeCategories = this.safeIncomeCategories(payload);
    return {
      ...payload,
      defaultBillCategory: this.resolveDefaultCategory(payload.defaultBillCategory, billCategories),
      defaultIncomeCategory: this.resolveDefaultCategory(payload.defaultIncomeCategory, incomeCategories),
      billCategories,
      incomeCategories
    };
  }

  private safeBillCategories(payload: Partial<AppPreferences>): string[] {
    return this.sanitizeCategories(payload.billCategories, FinanceFacade.DEFAULT_BILL_CATEGORIES);
  }

  private safeIncomeCategories(payload: Partial<AppPreferences>): string[] {
    return this.sanitizeCategories(payload.incomeCategories, FinanceFacade.DEFAULT_INCOME_CATEGORIES);
  }

  private sanitizeCategories(values: string[] | undefined, fallback: string[]): string[] {
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

  private resolveDefaultCategory(value: string, categories: string[]): string {
    const normalized = value?.trim()?.toLowerCase();
    const found = categories.find((item) => item.toLowerCase() === normalized);
    return found ?? categories[0];
  }
}
