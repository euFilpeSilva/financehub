import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  CalendarClock,
  CalendarRange,
  CircleHelp,
  LineChart,
  LucideAngularModule,
  Pencil,
  Plus,
  Settings2,
  Trash2,
  X
} from 'lucide-angular';
import { SpendingGoal } from '../../models/finance.models';
import { FinanceFacade } from '../../services/finance.facade';
import { ConfirmDialogService } from '../../shared/confirm-dialog/confirm-dialog.service';
import { CurrencyMaskDirective } from '../../shared/directives/currency-mask.directive';
import { SummaryCardsComponent } from '../../shared/summary-cards/summary-cards.component';
import { ToastService } from '../../shared/toast/toast.service';

@Component({
  selector: 'app-dashboard-page',
  imports: [CommonModule, CurrencyPipe, DatePipe, ReactiveFormsModule, LucideAngularModule, SummaryCardsComponent, CurrencyMaskDirective],
  templateUrl: './dashboard-page.component.html'
})
export class DashboardPageComponent {
  protected readonly CalendarClock = CalendarClock;
  protected readonly CalendarRange = CalendarRange;
  protected readonly CircleHelp = CircleHelp;
  protected readonly LineChart = LineChart;
  protected readonly Pencil = Pencil;
  protected readonly Plus = Plus;
  protected readonly Settings2 = Settings2;
  protected readonly Trash2 = Trash2;
  protected readonly X = X;

  protected readonly comparisonMode = signal<'month' | 'range'>('month');

  private readonly fb = inject(FormBuilder);
  private readonly confirmDialog = inject(ConfirmDialogService);
  private readonly toast = inject(ToastService);

  protected readonly comparisonForm = this.fb.nonNullable.group({
    monthA: [this.previousMonth(), Validators.required],
    monthB: [this.currentMonth(), Validators.required],
    rangeAStart: [this.currentMonth() + '-01', Validators.required],
    rangeAEnd: [this.currentMonth() + '-15', Validators.required],
    rangeBStart: [this.currentMonth() + '-16', Validators.required],
    rangeBEnd: [this.lastDayOfCurrentMonth(), Validators.required]
  });

  protected readonly spendingGoalForm = this.fb.nonNullable.group({
    title: ['', [Validators.required, Validators.maxLength(120)]],
    limitAmount: [0, [Validators.required, Validators.min(0.01)]],
    category: ['ALL', Validators.required],
    schedule: ['monthly' as 'monthly' | 'custom', Validators.required],
    startMonth: [this.currentMonth()],
    startDate: [this.currentMonth() + '-01'],
    endDate: [this.lastDayOfCurrentMonth()],
    active: [true]
  });
  protected readonly reconciliationForm = this.fb.nonNullable.group({
    bankAccountId: [''],
    startDate: [this.todayIsoDate(), Validators.required],
    endDate: [this.todayIsoDate(), Validators.required],
    referenceBalance: [0]
  });
  protected readonly reconciliationTopCauses = computed(() => {
    const reconciliation = this.facade.accountReconciliation();
    if (!reconciliation) {
      return [] as string[];
    }

    const causes: string[] = [];
    const difference = reconciliation.difference;
    const absDifference = difference === null || difference === undefined ? null : Math.abs(difference);

    if (reconciliation.referenceBalance === null || reconciliation.referenceBalance === undefined) {
      causes.push('Saldo de referencia nao informado: sem ele, nao ha como medir a diferenca real com o banco.');
    }

    if ((reconciliation.legacyIncomeMatches + reconciliation.legacyExpenseMatches) > 0) {
      causes.push(
        'Ha lançamentos legados identificados por texto (sem vinculo direto de conta), o que pode gerar classificacoes menos precisas.'
      );
    }

    if (absDifference !== null && absDifference <= 1) {
      causes.push('Diferenca muito pequena: pode ser arredondamento, IOF/taxas ou horario de corte do extrato.');
    }

    if (difference !== null && difference !== undefined && difference > 1) {
      causes.push('Saldo calculado ficou maior que o saldo de referencia: pode haver saidas faltando ou entradas em duplicidade no periodo.');
    }

    if (difference !== null && difference !== undefined && difference < -1) {
      causes.push('Saldo calculado ficou menor que o saldo de referencia: pode haver entradas faltando ou saidas extras classificadas para esta conta.');
    }

    if (reconciliation.incomeCount === 0 || reconciliation.expenseCount === 0) {
      causes.push('Recorte de datas pode estar curto ou desalinhado com o extrato usado como referencia.');
    }

    if (causes.length < 5) {
      causes.push('Movimentos pos-corte (apos a data final) nao entram no calculo e podem explicar parte da diferenca.');
    }

    if (causes.length < 5) {
      causes.push('Transferencias internas marcadas de forma diferente entre app e extrato podem distorcer o saldo conciliado.');
    }

    return causes.slice(0, 5);
  });
  protected editingSpendingGoalId: string | null = null;
  private hasAppliedDashboardDefaults = false;
  private hasInitializedReconciliationDefaults = false;

  constructor(protected readonly facade: FinanceFacade) {
    effect(() => {
      const prefs = this.facade.appPreferences();
      if (!this.hasAppliedDashboardDefaults) {
        this.comparisonMode.set(prefs.defaultDashboardMode);
        this.comparisonForm.patchValue({
          monthA: this.monthWithOffset(-prefs.defaultDashboardMonthComparisonOffset),
          monthB: this.currentMonth()
        });
        this.hasAppliedDashboardDefaults = true;
      }
    });

    effect(() => {
      if (this.hasInitializedReconciliationDefaults) {
        return;
      }

      const accounts = this.facade.bankAccounts();
      const bills = this.facade.allBills();
      const incomes = this.facade.allIncomes();

      if (!accounts.length && !bills.length && !incomes.length) {
        return;
      }

      const accountId = this.resolveDefaultBankAccountId(accounts);
      const startDate = this.resolveEarliestMovementDate(bills, incomes) ?? this.todayIsoDate();

      this.reconciliationForm.patchValue({
        bankAccountId: accountId,
        startDate,
        endDate: this.todayIsoDate()
      });
      this.hasInitializedReconciliationDefaults = true;
    });
  }

  protected get comparisonResult() {
    const values = this.comparisonForm.getRawValue();
    if (this.comparisonMode() === 'month') {
      return this.facade.compareMonths(values.monthA, values.monthB);
    }
    return this.facade.compareRanges(values.rangeAStart, values.rangeAEnd, values.rangeBStart, values.rangeBEnd);
  }

  protected setMode(mode: 'month' | 'range'): void {
    this.comparisonMode.set(mode);
  }

  protected async submitSpendingGoal(): Promise<void> {
    if (this.spendingGoalForm.invalid) {
      this.spendingGoalForm.markAllAsTouched();
      this.toast.error('Preencha os campos obrigatorios da meta de gasto (nome e limite maior que zero).');
      return;
    }

    const value = this.spendingGoalForm.getRawValue();
    const schedule = value.schedule;

    if (this.editingSpendingGoalId) {
      const confirmed = await this.confirmDialog.confirm({
        title: 'Confirmar edicao',
        message: 'Deseja salvar as alteracoes desta meta de gasto?',
        confirmLabel: 'Salvar'
      });
      if (!confirmed) {
        return;
      }
      const current = this.facade.spendingGoals().find((item) => item.id === this.editingSpendingGoalId);
      if (!current) {
        return;
      }
      this.facade.updateSpendingGoal({
        ...current,
        title: value.title.trim(),
        limitAmount: this.toAmount(value.limitAmount),
        category: value.category,
        schedule,
        startMonth: schedule === 'monthly' ? value.startMonth : undefined,
        startDate: schedule === 'custom' ? value.startDate : undefined,
        endDate: schedule === 'custom' ? value.endDate : undefined,
        active: value.active
      });
    } else {
      const confirmed = await this.confirmDialog.confirm({
        title: 'Confirmar inclusao',
        message: 'Deseja criar esta nova meta de gasto?',
        confirmLabel: 'Criar'
      });
      if (!confirmed) {
        return;
      }
      this.facade.addSpendingGoal({
        title: value.title.trim(),
        limitAmount: this.toAmount(value.limitAmount),
        category: value.category,
        schedule,
        startMonth: schedule === 'monthly' ? value.startMonth : undefined,
        startDate: schedule === 'custom' ? value.startDate : undefined,
        endDate: schedule === 'custom' ? value.endDate : undefined,
        active: value.active
      });
    }

    this.resetSpendingGoalForm();
  }

  protected toggleSpendingGoal(goalId: string): void {
    const goal = this.facade.spendingGoals().find((item) => item.id === goalId);
    if (!goal) {
      return;
    }
    this.facade.toggleSpendingGoal(goal);
  }

  protected startEditSpendingGoal(goal: SpendingGoal): void {
    this.editingSpendingGoalId = goal.id;
    this.spendingGoalForm.patchValue({
      title: goal.title,
      limitAmount: goal.limitAmount,
      category: goal.category,
      schedule: goal.schedule,
      startMonth: goal.startMonth ?? this.currentMonth(),
      startDate: goal.startDate ?? this.currentMonth() + '-01',
      endDate: goal.endDate ?? this.lastDayOfCurrentMonth(),
      active: goal.active
    });
  }

  protected cancelEditSpendingGoal(): void {
    this.resetSpendingGoalForm();
  }

  protected async removeSpendingGoal(goal: SpendingGoal): Promise<void> {
    const confirmed = await this.confirmDialog.confirm({
      title: 'Excluir meta de gasto',
      message: `Deseja excluir "${goal.title}"?`,
      confirmLabel: 'Excluir',
      tone: 'danger'
    });
    if (!confirmed) {
      return;
    }
    if (this.editingSpendingGoalId === goal.id) {
      this.resetSpendingGoalForm();
    }
    this.facade.deleteSpendingGoal(goal.id);
  }

  protected runReconciliation(): void {
    const value = this.reconciliationForm.getRawValue();
    if (!value.bankAccountId) {
      this.toast.error('Selecione uma conta para conciliar.');
      return;
    }
    const referenceBalance = Number(value.referenceBalance);
    this.facade.reconcileAccount(
      value.bankAccountId,
      value.startDate,
      value.endDate,
      Number.isFinite(referenceBalance) ? referenceBalance : undefined
    );
  }

  private toAmount(value: number): number {
    return Math.max(0, Number(value.toFixed(2)));
  }

  private currentMonth(): string {
    return new Date().toISOString().slice(0, 7);
  }

  private previousMonth(): string {
    return this.monthWithOffset(-1);
  }

  private lastDayOfCurrentMonth(): string {
    const now = new Date();
    const year = now.getFullYear();
    const month = now.getMonth() + 1;
    const last = new Date(year, month, 0).getDate();
    return `${this.currentMonth()}-${last.toString().padStart(2, '0')}`;
  }

  private resetSpendingGoalForm(): void {
    this.editingSpendingGoalId = null;
    this.spendingGoalForm.patchValue({
      title: '',
      limitAmount: 0,
      category: 'ALL',
      schedule: 'monthly',
      startMonth: this.currentMonth(),
      startDate: this.currentMonth() + '-01',
      endDate: this.lastDayOfCurrentMonth(),
      active: true
    });
  }

  private resolveDefaultBankAccountId(
    accounts: Array<{ id: string; active: boolean }>
  ): string {
    const active = accounts.find((account) => account.active);
    if (active) {
      return active.id;
    }
    return accounts[0]?.id ?? '';
  }

  private resolveEarliestMovementDate(
    bills: Array<{ dueDate: string }>,
    incomes: Array<{ receivedAt: string }>
  ): string | null {
    const dates = [
      ...bills.map((item) => item.dueDate),
      ...incomes.map((item) => item.receivedAt)
    ]
      .filter((date) => /^\d{4}-\d{2}-\d{2}$/.test(date))
      .sort((a, b) => a.localeCompare(b));

    return dates.length ? dates[0] : null;
  }

  private todayIsoDate(): string {
    return new Date().toISOString().slice(0, 10);
  }

  private monthWithOffset(offset: number): string {
    const now = new Date();
    now.setMonth(now.getMonth() + offset);
    return now.toISOString().slice(0, 7);
  }
}
