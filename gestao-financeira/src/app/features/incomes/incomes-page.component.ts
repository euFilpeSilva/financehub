import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { LucideAngularModule, Pencil, Plus, Trash2, X } from 'lucide-angular';
import { startWith } from 'rxjs/operators';
import { IncomeEntry } from '../../models/finance.models';
import { FinanceFacade } from '../../services/finance.facade';
import { ConfirmDialogService } from '../../shared/confirm-dialog/confirm-dialog.service';
import { CurrencyMaskDirective } from '../../shared/directives/currency-mask.directive';
import { FilterPanelComponent } from '../../shared/filter-panel/filter-panel.component';
import { ToastService } from '../../shared/toast/toast.service';

@Component({
  selector: 'app-incomes-page',
  imports: [CommonModule, ReactiveFormsModule, CurrencyPipe, DatePipe, LucideAngularModule, CurrencyMaskDirective, FilterPanelComponent],
  templateUrl: './incomes-page.component.html'
})
export class IncomesPageComponent {
  protected readonly Pencil = Pencil;
  protected readonly Plus = Plus;
  protected readonly Trash2 = Trash2;
  protected readonly X = X;
  protected readonly categories = computed(() => this.facade.availableIncomeCategories());
  protected readonly bankFilters = computed(() =>
    this.facade.bankAccounts().map((account) => ({ id: account.id, label: account.label }))
  );
  private readonly bankFiltersMap = computed(() =>
    new Map(this.facade.bankAccounts().map((account) => [account.id, account]))
  );
  protected readonly viewMode = signal<'list' | 'grid'>('list');

  private readonly fb = inject(FormBuilder);
  private readonly confirmDialog = inject(ConfirmDialogService);
  private readonly toast = inject(ToastService);

  protected readonly form = this.fb.nonNullable.group({
    source: ['', [Validators.required, Validators.maxLength(120)]],
    category: ['Trabalho', Validators.required],
    amount: [0, [Validators.required, Validators.min(0.01)]],
    receivedAt: [this.today(), Validators.required],
    recurring: [false]
  });
  protected readonly filterForm = this.fb.nonNullable.group({
    query: [''],
    category: ['ALL'],
    bankAccountId: ['ALL'],
    recurring: ['ALL'],
    startDate: [''],
    endDate: [''],
    minAmount: [null as number | null],
    maxAmount: [null as number | null]
  });
  protected readonly sortBy = signal<'receivedAt' | 'source' | 'category' | 'amount' | 'recurring'>('receivedAt');
  protected readonly sortDirection = signal<'asc' | 'desc'>('desc');
  private readonly filterValues = toSignal(
    this.filterForm.valueChanges.pipe(startWith(this.filterForm.getRawValue())),
    { initialValue: this.filterForm.getRawValue() }
  );
  protected editingIncomeId: string | null = null;
  protected readonly filteredIncomes = computed(() => {
    const filters = this.filterValues();
    const query = (filters.query ?? '').trim().toLowerCase();
    const category = filters.category ?? 'ALL';
    const bankAccountId = filters.bankAccountId ?? 'ALL';
    const recurring = filters.recurring ?? 'ALL';
    const startDate = filters.startDate ?? '';
    const endDate = filters.endDate ?? '';
    const minAmount = filters.minAmount !== null ? Number(filters.minAmount) : null;
    const maxAmount = filters.maxAmount !== null ? Number(filters.maxAmount) : null;
    const sortBy = this.sortBy();
    const sortDirection = this.sortDirection();

    const filtered = this.facade.allIncomes().filter((income) => {
      if (query && !income.source.toLowerCase().includes(query)) {
        return false;
      }
      if (category !== 'ALL' && income.category !== category) {
        return false;
      }
      if (!this.matchesBankFilter(bankAccountId, income)) {
        return false;
      }
      if (recurring === 'YES' && !income.recurring) {
        return false;
      }
      if (recurring === 'NO' && income.recurring) {
        return false;
      }
      if (startDate && income.receivedAt < startDate) {
        return false;
      }
      if (endDate && income.receivedAt > endDate) {
        return false;
      }
      if (minAmount !== null && income.amount < minAmount) {
        return false;
      }
      if (maxAmount !== null && income.amount > maxAmount) {
        return false;
      }
      return true;
    });

    const direction = sortDirection === 'asc' ? 1 : -1;
    return filtered.slice().sort((first, second) => {
      const compare = this.compareIncome(first, second, sortBy);
      return compare * direction;
    });
  });

  constructor(protected readonly facade: FinanceFacade) {
    effect(() => {
      const prefs = this.facade.appPreferences();
      if (!this.editingIncomeId) {
        this.form.patchValue({
          category: prefs.defaultIncomeCategory,
          recurring: prefs.defaultIncomeRecurring,
          receivedAt: this.dateForDayInCurrentMonth(prefs.defaultIncomeReceivedDay)
        });
      }
    });
  }

  protected async submit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    const hasEligibleAccount = this.facade.bankAccounts().some((account) => account.active && account.primaryIncome);
    if (!hasEligibleAccount) {
      this.toast.info(
        'Aviso: nenhuma conta ativa esta marcada como elegivel para entrada. Configure em Contas bancarias para melhorar a classificacao das movimentacoes.'
      );
    }

    if (this.editingIncomeId) {
      const confirmed = await this.confirmDialog.confirm({
        title: 'Confirmar edicao',
        message: 'Deseja salvar as alteracoes desta entrada?',
        confirmLabel: 'Salvar'
      });
      if (!confirmed) {
        return;
      }
      const current = this.facade.allIncomes().find((item) => item.id === this.editingIncomeId);
      if (!current) {
        return;
      }
      this.facade.updateIncome({
        ...current,
        source: value.source.trim(),
        category: value.category,
        amount: this.toAmount(value.amount),
        receivedAt: value.receivedAt,
        recurring: value.recurring
      });
    } else {
      const confirmed = await this.confirmDialog.confirm({
        title: 'Confirmar inclusao',
        message: 'Deseja adicionar esta nova entrada?',
        confirmLabel: 'Adicionar'
      });
      if (!confirmed) {
        return;
      }
      this.facade.addIncome({
        source: value.source.trim(),
        category: value.category,
        amount: this.toAmount(value.amount),
        receivedAt: value.receivedAt,
        recurring: value.recurring,
        internalTransfer: false
      });
    }

    this.resetForm();
  }

  protected startEdit(income: IncomeEntry): void {
    this.editingIncomeId = income.id;
    this.form.patchValue({
      source: income.source,
      category: income.category,
      amount: income.amount,
      receivedAt: income.receivedAt,
      recurring: income.recurring
    });
  }

  protected cancelEdit(): void {
    this.resetForm();
  }

  protected async removeIncome(income: IncomeEntry): Promise<void> {
    const confirmed = await this.confirmDialog.confirm({
      title: 'Excluir entrada',
      message: `Deseja excluir "${income.source}"?`,
      confirmLabel: 'Excluir',
      tone: 'danger'
    });
    if (!confirmed) {
      return;
    }
    if (this.editingIncomeId === income.id) {
      this.resetForm();
    }
    this.facade.deleteIncome(income.id);
  }

  private today(): string {
    return new Date().toISOString().slice(0, 10);
  }

  private toAmount(value: number): number {
    return Math.max(0, Number(value.toFixed(2)));
  }

  private resetForm(): void {
    const prefs = this.facade.appPreferences();
    this.editingIncomeId = null;
    this.form.patchValue({
      source: '',
      category: prefs.defaultIncomeCategory,
      amount: 0,
      receivedAt: this.dateForDayInCurrentMonth(prefs.defaultIncomeReceivedDay),
      recurring: prefs.defaultIncomeRecurring
    });
  }

  private dateForDayInCurrentMonth(day: number): string {
    const today = new Date();
    const year = today.getFullYear();
    const month = today.getMonth() + 1;
    const maxDay = new Date(year, month, 0).getDate();
    const safeDay = Math.max(1, Math.min(day, maxDay));
    return `${year}-${month.toString().padStart(2, '0')}-${safeDay.toString().padStart(2, '0')}`;
  }

  protected clearFilters(): void {
    this.filterForm.patchValue({
      query: '',
      category: 'ALL',
      bankAccountId: 'ALL',
      recurring: 'ALL',
      startDate: '',
      endDate: '',
      minAmount: null,
      maxAmount: null
    });
    this.sortBy.set('receivedAt');
    this.sortDirection.set('desc');
  }

  protected setSort(column: 'receivedAt' | 'source' | 'category' | 'amount' | 'recurring'): void {
    if (this.sortBy() === column) {
      this.sortDirection.set(this.sortDirection() === 'asc' ? 'desc' : 'asc');
      return;
    }
    this.sortBy.set(column);
    this.sortDirection.set('asc');
  }

  protected sortLabel(column: 'receivedAt' | 'source' | 'category' | 'amount' | 'recurring'): string {
    if (this.sortBy() !== column) {
      return '';
    }
    return this.sortDirection() === 'asc' ? '▲' : '▼';
  }

  private compareIncome(first: IncomeEntry, second: IncomeEntry, sortBy: string): number {
    if (sortBy === 'source') {
      return first.source.localeCompare(second.source);
    }
    if (sortBy === 'category') {
      return first.category.localeCompare(second.category);
    }
    if (sortBy === 'amount') {
      return first.amount - second.amount;
    }
    if (sortBy === 'recurring') {
      return Number(first.recurring) - Number(second.recurring);
    }
    return first.receivedAt.localeCompare(second.receivedAt);
  }

  private matchesBankFilter(selectedBankId: string, income: IncomeEntry): boolean {
    if (selectedBankId === 'ALL') {
      return true;
    }

    if ((income.bankAccountId ?? null) === selectedBankId) {
      return true;
    }

    if (income.bankAccountId) {
      return false;
    }

    const bank = this.bankFiltersMap().get(selectedBankId);
    if (!bank) {
      return false;
    }

    const normalizedSource = this.normalizeText(income.source);
    const tokens = this.resolveBankTokens(bank.label, bank.bankId);
    return tokens.some((token) => normalizedSource.includes(token));
  }

  private resolveBankTokens(label: string, bankId: string): string[] {
    const normalizedLabel = this.normalizeText(label);
    const tokens = new Set<string>();

    for (const part of normalizedLabel.split(' ')) {
      if (part.length >= 3) {
        tokens.add(part);
      }
    }

    if (bankId) {
      tokens.add(bankId);
    }

    if (normalizedLabel.includes('NUBANK') || bankId === '260') {
      tokens.add('NUBANK');
      tokens.add('NU PAGAMENTOS');
    }

    return Array.from(tokens);
  }

  private normalizeText(value: string): string {
    return value
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toUpperCase();
  }
}

