import { computed, Injectable, signal } from '@angular/core';
import {
  BillRecord,
  ComparisonSummary,
  DashboardSummary,
  IncomeEntry,
  MonthlyPerformance,
  PeriodSnapshot
} from '../models/finance.models';

@Injectable({ providedIn: 'root' })
export class DashboardAnalyticsFacade {
  private readonly billsSource = signal<BillRecord[]>([]);
  private readonly incomesSource = signal<IncomeEntry[]>([]);
  private readonly dashboardSummarySource = signal<DashboardSummary>({
    incomeTotal: 0,
    expenseTotal: 0,
    balance: 0,
    paidBillsTotal: 0,
    pendingBillsTotal: 0
  });

  readonly incomeTotal = computed(() => this.dashboardSummarySource().incomeTotal);
  readonly expenseTotal = computed(() => this.dashboardSummarySource().expenseTotal);
  readonly paidBillsTotal = computed(() => this.dashboardSummarySource().paidBillsTotal);
  readonly pendingBillsTotal = computed(() => this.dashboardSummarySource().pendingBillsTotal);
  readonly balance = computed(() => this.dashboardSummarySource().balance);

  readonly monthlyPerformance = computed<MonthlyPerformance[]>(() => {
    const months = new Set<string>();
    for (const bill of this.billsSource()) {
      months.add(bill.dueDate.slice(0, 7));
    }
    for (const income of this.incomesSource()) {
      months.add(income.receivedAt.slice(0, 7));
    }

    return Array.from(months)
      .sort((a, b) => a.localeCompare(b))
      .map((month) => {
        const income = this.incomesSource()
          .filter((item) => !item.internalTransfer)
          .filter((item) => item.receivedAt.startsWith(month))
          .reduce((sum, item) => sum + item.amount, 0);
        const expenses = this.billsSource()
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

  setData(bills: BillRecord[], incomes: IncomeEntry[]): void {
    this.billsSource.set(bills);
    this.incomesSource.set(incomes);
  }

  setDashboardSummary(summary: DashboardSummary): void {
    this.dashboardSummarySource.set(summary);
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
    const income = this.incomesSource()
      .filter((item) => !item.internalTransfer)
      .filter((item) => item.receivedAt.startsWith(month))
      .reduce((sum, item) => sum + item.amount, 0);
    const expenses = this.billsSource()
      .filter((item) => !item.internalTransfer)
      .filter((item) => item.dueDate.startsWith(month))
      .reduce((sum, item) => sum + item.amount, 0);
    return { income, expenses, savings: income - expenses };
  }

  getRangeSnapshot(start: string, end: string): PeriodSnapshot {
    const safeStart = start || '0000-01-01';
    const safeEnd = end || '9999-12-31';
    const income = this.incomesSource()
      .filter((item) => !item.internalTransfer)
      .filter((item) => item.receivedAt >= safeStart && item.receivedAt <= safeEnd)
      .reduce((sum, item) => sum + item.amount, 0);
    const expenses = this.billsSource()
      .filter((item) => !item.internalTransfer)
      .filter((item) => item.dueDate >= safeStart && item.dueDate <= safeEnd)
      .reduce((sum, item) => sum + item.amount, 0);
    return { income, expenses, savings: income - expenses };
  }

  calculateExpenseInPeriod(start: string, end: string, category: string): number {
    return this.billsSource()
      .filter((item) => !item.internalTransfer)
      .filter((item) => item.dueDate >= start && item.dueDate <= end)
      .filter((item) => category === 'ALL' || item.category === category)
      .reduce((sum, item) => sum + item.amount, 0);
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
}
