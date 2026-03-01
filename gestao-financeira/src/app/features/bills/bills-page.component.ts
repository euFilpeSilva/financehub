import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CircleCheckBig, CircleDashed, LucideAngularModule, Pencil, Plus, Repeat, Trash2, X } from 'lucide-angular';
import { startWith } from 'rxjs/operators';
import { BillRecord } from '../../models/finance.models';
import { FinanceFacade } from '../../services/finance.facade';
import { ConfirmDialogService } from '../../shared/confirm-dialog/confirm-dialog.service';
import { CurrencyMaskDirective } from '../../shared/directives/currency-mask.directive';
import { FilterPanelComponent } from '../../shared/filter-panel/filter-panel.component';
import { ToastService } from '../../shared/toast/toast.service';

@Component({
  selector: 'app-bills-page',
  imports: [CommonModule, ReactiveFormsModule, CurrencyPipe, DatePipe, LucideAngularModule, CurrencyMaskDirective, FilterPanelComponent],
  templateUrl: './bills-page.component.html'
})
export class BillsPageComponent {
  protected readonly CircleCheckBig = CircleCheckBig;
  protected readonly CircleDashed = CircleDashed;
  protected readonly Pencil = Pencil;
  protected readonly Plus = Plus;
  protected readonly Repeat = Repeat;
  protected readonly Trash2 = Trash2;
  protected readonly X = X;

  protected readonly categories = computed(() => this.facade.availableExpenseCategories());
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
    description: ['', [Validators.required, Validators.maxLength(120)]],
    category: ['Moradia', Validators.required],
    amount: [0, [Validators.required, Validators.min(0.01)]],
    dueDate: [this.today(), Validators.required],
    recurring: [false]
  });
  protected readonly filterForm = this.fb.nonNullable.group({
    query: [''],
    category: ['ALL'],
    bankAccountId: ['ALL'],
    status: ['ALL'],
    recurring: ['ALL'],
    startDate: [''],
    endDate: ['']
  });
  private readonly filterValues = toSignal(
    this.filterForm.valueChanges.pipe(startWith(this.filterForm.getRawValue())),
    { initialValue: this.filterForm.getRawValue() }
  );
  protected editingBillId: string | null = null;
  protected readonly filteredBills = computed(() => {
    const filters = this.filterValues();
    const query = (filters.query ?? '').trim().toLowerCase();
    const category = filters.category ?? 'ALL';
    const bankAccountId = filters.bankAccountId ?? 'ALL';
    const status = filters.status ?? 'ALL';
    const recurring = filters.recurring ?? 'ALL';
    const startDate = filters.startDate ?? '';
    const endDate = filters.endDate ?? '';
    const sortBy = this.sortBy();
    const sortDirection = this.sortDirection();

    const filtered = this.facade.allBills().filter((bill) => {
      if (query && !bill.description.toLowerCase().includes(query)) {
        return false;
      }
      if (category !== 'ALL' && bill.category !== category) {
        return false;
      }
      if (!this.matchesBankFilter(bankAccountId, bill)) {
        return false;
      }
      if (status === 'PAID' && !bill.paid) {
        return false;
      }
      if (status === 'PENDING' && bill.paid) {
        return false;
      }
      if (recurring === 'YES' && !bill.recurring) {
        return false;
      }
      if (recurring === 'NO' && bill.recurring) {
        return false;
      }
      if (startDate && bill.dueDate < startDate) {
        return false;
      }
      if (endDate && bill.dueDate > endDate) {
        return false;
      }
      return true;
    });

    const direction = sortDirection === 'asc' ? 1 : -1;
    return filtered.slice().sort((first, second) => {
      const compare = this.compareBill(first, second, sortBy);
      return compare * direction;
    });
  });

  protected readonly sortBy = signal<'description' | 'category' | 'dueDate' | 'amount' | 'status' | 'recurring'>('dueDate');
  protected readonly sortDirection = signal<'asc' | 'desc'>('asc');

  constructor(protected readonly facade: FinanceFacade) {
    effect(() => {
      const prefs = this.facade.appPreferences();
      if (!this.editingBillId) {
        this.form.patchValue({
          category: prefs.defaultBillCategory,
          recurring: prefs.defaultBillRecurring,
          dueDate: this.dateForDayInCurrentMonth(prefs.defaultBillDueDay)
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

    if (this.editingBillId) {
      const confirmed = await this.confirmDialog.confirm({
        title: 'Confirmar edicao',
        message: 'Deseja salvar as alteracoes desta conta?',
        confirmLabel: 'Salvar'
      });
      if (!confirmed) {
        return;
      }
      const current = this.facade.allBills().find((item) => item.id === this.editingBillId);
      if (!current) {
        return;
      }
      this.facade.updateBill({
        ...current,
        description: value.description.trim(),
        category: value.category,
        amount: this.toAmount(value.amount),
        dueDate: value.dueDate,
        recurring: value.recurring
      });
    } else {
      const confirmed = await this.confirmDialog.confirm({
        title: 'Confirmar inclusao',
        message: 'Deseja adicionar esta nova conta?',
        confirmLabel: 'Adicionar'
      });
      if (!confirmed) {
        return;
      }
      this.facade.addBill({
        description: value.description.trim(),
        category: value.category,
        amount: this.toAmount(value.amount),
        dueDate: value.dueDate,
        recurring: value.recurring,
        paid: false,
        internalTransfer: false
      });
    }

    this.resetForm();
  }

  protected toggleStatus(bill: BillRecord): void {
    this.facade.toggleBillPaid(bill);
  }

  protected async generateRecurring(): Promise<void> {
    const confirmed = await this.confirmDialog.confirm({
      title: 'Gerar recorrentes',
      message: 'Deseja gerar contas recorrentes para o mes atual?',
      confirmLabel: 'Gerar'
    });
    if (!confirmed) {
      return;
    }
    this.facade.createRecurringBillsForCurrentMonth();
  }

  protected startEdit(bill: BillRecord): void {
    this.editingBillId = bill.id;
    this.form.patchValue({
      description: bill.description,
      category: bill.category,
      amount: bill.amount,
      dueDate: bill.dueDate,
      recurring: bill.recurring
    });
  }

  protected cancelEdit(): void {
    this.resetForm();
  }

  protected async removeBill(bill: BillRecord): Promise<void> {
    const confirmed = await this.confirmDialog.confirm({
      title: 'Excluir conta',
      message: `Deseja excluir "${bill.description}"?`,
      confirmLabel: 'Excluir',
      tone: 'danger'
    });
    if (!confirmed) {
      return;
    }
    if (this.editingBillId === bill.id) {
      this.resetForm();
    }
    this.facade.deleteBill(bill.id);
  }

  private today(): string {
    return new Date().toISOString().slice(0, 10);
  }

  private toAmount(value: number): number {
    return Math.max(0, Number(value.toFixed(2)));
  }

  private resetForm(): void {
    const prefs = this.facade.appPreferences();
    this.editingBillId = null;
    this.form.patchValue({
      description: '',
      category: prefs.defaultBillCategory,
      amount: 0,
      dueDate: this.dateForDayInCurrentMonth(prefs.defaultBillDueDay),
      recurring: prefs.defaultBillRecurring
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
      status: 'ALL',
      recurring: 'ALL',
      startDate: '',
      endDate: ''
    });
    this.sortBy.set('dueDate');
    this.sortDirection.set('asc');
  }

  protected setSort(column: 'description' | 'category' | 'dueDate' | 'amount' | 'status' | 'recurring'): void {
    if (this.sortBy() === column) {
      this.sortDirection.set(this.sortDirection() === 'asc' ? 'desc' : 'asc');
      return;
    }
    this.sortBy.set(column);
    this.sortDirection.set('asc');
  }

  protected sortLabel(column: 'description' | 'category' | 'dueDate' | 'amount' | 'status' | 'recurring'): string {
    if (this.sortBy() !== column) {
      return '';
    }
    return this.sortDirection() === 'asc' ? '▲' : '▼';
  }

  private compareBill(first: BillRecord, second: BillRecord, sortBy: string): number {
    if (sortBy === 'description') {
      return first.description.localeCompare(second.description);
    }
    if (sortBy === 'category') {
      return first.category.localeCompare(second.category);
    }
    if (sortBy === 'amount') {
      return first.amount - second.amount;
    }
    if (sortBy === 'status') {
      return Number(first.paid) - Number(second.paid);
    }
    if (sortBy === 'recurring') {
      return Number(first.recurring) - Number(second.recurring);
    }
    return first.dueDate.localeCompare(second.dueDate);
  }

  private matchesBankFilter(selectedBankId: string, bill: BillRecord): boolean {
    if (selectedBankId === 'ALL') {
      return true;
    }

    if ((bill.bankAccountId ?? null) === selectedBankId) {
      return true;
    }

    if (bill.bankAccountId) {
      return false;
    }

    const bank = this.bankFiltersMap().get(selectedBankId);
    if (!bank) {
      return false;
    }

    const normalizedDescription = this.normalizeText(bill.description);
    const tokens = this.resolveBankTokens(bank.label, bank.bankId);
    return tokens.some((token) => normalizedDescription.includes(token));
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

