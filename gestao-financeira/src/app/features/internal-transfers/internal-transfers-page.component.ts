import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ArrowRightLeft, CheckCircle2, CircleHelp, Link2, LucideAngularModule, Search, ShieldAlert, ShieldCheck, Trash2 } from 'lucide-angular';
import { startWith } from 'rxjs/operators';
import { InternalTransferSuggestion } from '../../models/finance.models';
import { FinanceFacade } from '../../services/finance.facade';

interface ClassifiedInternalTransferPair {
  billId: string;
  incomeId: string;
  billDescription: string;
  incomeSource: string;
  billBankAccountLabel: string;
  incomeBankAccountLabel: string;
  billDate: string;
  incomeDate: string;
  amount: number;
  daysDiff: number;
}

interface UnpairedInternalMovement {
  type: 'ORIGEM' | 'DESTINO';
  id: string;
  description: string;
  bankAccountLabel: string;
  date: string;
  amount: number;
}

@Component({
  selector: 'app-internal-transfers-page',
  imports: [CommonModule, ReactiveFormsModule, CurrencyPipe, DatePipe, LucideAngularModule],
  templateUrl: './internal-transfers-page.component.html'
})
export class InternalTransfersPageComponent {
  protected readonly Search = Search;
  protected readonly Link2 = Link2;
  protected readonly Trash2 = Trash2;
  protected readonly ArrowRightLeft = ArrowRightLeft;
  protected readonly ShieldCheck = ShieldCheck;
  protected readonly ShieldAlert = ShieldAlert;
  protected readonly CheckCircle2 = CheckCircle2;
  protected readonly CircleHelp = CircleHelp;

  private readonly fb = inject(FormBuilder);
  protected readonly facade = inject(FinanceFacade);

  protected readonly detectForm = this.fb.nonNullable.group({
    ownerName: [''],
    ownerCpf: [''],
    dateToleranceDays: [1, [Validators.min(0), Validators.max(5)]],
    autoApply: [false]
  });
  protected readonly reviewForm = this.fb.nonNullable.group({
    bankAccountId: ['ALL'],
    confidence: ['ALL'],
    minAmount: [null as number | null],
    maxAmount: [null as number | null],
    query: ['']
  });

  private readonly ignoredPairs = signal<Set<string>>(new Set());
  private readonly reviewValues = toSignal(
    this.reviewForm.valueChanges.pipe(startWith(this.reviewForm.getRawValue())),
    { initialValue: this.reviewForm.getRawValue() }
  );

  protected readonly visibleSuggestions = computed(() => {
    const filters = this.reviewValues();
    const ignored = this.ignoredPairs();
    const selectedAccountId = (filters.bankAccountId || 'ALL').trim();
    const selectedConfidence = (filters.confidence || 'ALL') as 'ALL' | InternalTransferSuggestion['confidence'];
    const query = (filters.query || '').trim().toLowerCase();
    const minAmount = typeof filters.minAmount === 'number' ? filters.minAmount : null;
    const maxAmount = typeof filters.maxAmount === 'number' ? filters.maxAmount : null;

    return this.facade.internalTransferSuggestions()
      .filter((item) => !ignored.has(this.pairKey(item)))
      .filter((item) => {
        if (selectedAccountId === 'ALL') {
          return true;
        }
        return item.billBankAccountId === selectedAccountId || item.incomeBankAccountId === selectedAccountId;
      })
      .filter((item) => selectedConfidence === 'ALL' || item.confidence === selectedConfidence)
      .filter((item) => {
        if (minAmount === null || Number.isNaN(minAmount)) {
          return true;
        }
        return item.amount >= minAmount;
      })
      .filter((item) => {
        if (maxAmount === null || Number.isNaN(maxAmount)) {
          return true;
        }
        return item.amount <= maxAmount;
      })
      .filter((item) => {
        if (!query) {
          return true;
        }
        const haystack = [
          item.billDescription,
          item.incomeSource,
          item.billBankAccountLabel,
          item.incomeBankAccountLabel,
          ...(item.reasons || [])
        ].join(' ').toLowerCase();
        return haystack.includes(query);
      });
  });
  protected readonly highConfidenceVisibleCount = computed(() =>
    this.visibleSuggestions().filter((item) => item.confidence === 'ALTA').length
  );
  protected readonly classifiedInternalPairs = computed<ClassifiedInternalTransferPair[]>(() => {
    const toleranceDays = this.resolveToleranceDays();
    const bills = this.facade.allBills()
      .filter((item) => item.internalTransfer)
      .slice()
      .sort((a, b) => a.dueDate.localeCompare(b.dueDate));
    const incomes = this.facade.allIncomes()
      .filter((item) => item.internalTransfer)
      .slice()
      .sort((a, b) => a.receivedAt.localeCompare(b.receivedAt));

    const usedIncomeIds = new Set<string>();
    const pairs: ClassifiedInternalTransferPair[] = [];

    for (const bill of bills) {
      const billAmount = Math.abs(Number(bill.amount) || 0);
      const matches = incomes
        .filter((income) => !usedIncomeIds.has(income.id))
        .filter((income) => Math.abs((Number(income.amount) || 0) - billAmount) <= 0.009)
        .map((income) => ({
          income,
          daysDiff: this.calculateDaysDiff(bill.dueDate, income.receivedAt)
        }))
        .filter((item) => item.daysDiff <= toleranceDays)
        .sort((first, second) => first.daysDiff - second.daysDiff);

      const best = matches[0];
      if (!best) {
        continue;
      }

      usedIncomeIds.add(best.income.id);
      pairs.push({
        billId: bill.id,
        incomeId: best.income.id,
        billDescription: bill.description,
        incomeSource: best.income.source,
        billBankAccountLabel: this.resolveBankLabel(bill.bankAccountId),
        incomeBankAccountLabel: this.resolveBankLabel(best.income.bankAccountId),
        billDate: bill.dueDate,
        incomeDate: best.income.receivedAt,
        amount: billAmount,
        daysDiff: best.daysDiff
      });
    }

    return pairs;
  });
  protected readonly unpairedInternalMovements = computed<UnpairedInternalMovement[]>(() => {
    const pairedBills = new Set(this.classifiedInternalPairs().map((item) => item.billId));
    const pairedIncomes = new Set(this.classifiedInternalPairs().map((item) => item.incomeId));

    const billOnly: UnpairedInternalMovement[] = this.facade.allBills()
      .filter((item) => item.internalTransfer)
      .filter((item) => !pairedBills.has(item.id))
      .map((item) => ({
        type: 'ORIGEM' as const,
        id: item.id,
        description: item.description,
        bankAccountLabel: this.resolveBankLabel(item.bankAccountId),
        date: item.dueDate,
        amount: Math.abs(Number(item.amount) || 0)
      }));

    const incomeOnly: UnpairedInternalMovement[] = this.facade.allIncomes()
      .filter((item) => item.internalTransfer)
      .filter((item) => !pairedIncomes.has(item.id))
      .map((item) => ({
        type: 'DESTINO' as const,
        id: item.id,
        description: item.source,
        bankAccountLabel: this.resolveBankLabel(item.bankAccountId),
        date: item.receivedAt,
        amount: Math.abs(Number(item.amount) || 0)
      }));

    return [...billOnly, ...incomeOnly]
      .sort((a, b) => b.date.localeCompare(a.date));
  });

  protected detectSuggestions(): void {
    if (this.detectForm.invalid) {
      this.detectForm.markAllAsTouched();
      return;
    }

    this.ignoredPairs.set(new Set());
    const value = this.detectForm.getRawValue();
    this.facade.detectInternalTransfers({
      ownerName: value.ownerName,
      ownerCpf: value.ownerCpf,
      dateToleranceDays: value.dateToleranceDays,
      autoApply: value.autoApply
    });
  }

  protected linkSuggestion(item: InternalTransferSuggestion): void {
    this.facade.linkInternalTransfer(item.billId, item.incomeId);
  }

  protected ignoreSuggestion(item: InternalTransferSuggestion): void {
    this.ignoredPairs.update((current) => {
      const next = new Set(current);
      next.add(this.pairKey(item));
      return next;
    });
  }

  protected clearSuggestions(): void {
    this.ignoredPairs.set(new Set());
    this.reviewForm.patchValue({
      bankAccountId: 'ALL',
      confidence: 'ALL',
      minAmount: null,
      maxAmount: null,
      query: ''
    });
    this.facade.clearInternalTransferSuggestions();
  }

  protected linkAllHighConfidence(): void {
    const ids = this.visibleSuggestions()
      .filter((item) => item.confidence === 'ALTA')
      .map((item) => ({ billId: item.billId, incomeId: item.incomeId }));
    if (!ids.length) {
      return;
    }
    this.facade.linkInternalTransfersBatch(ids);
  }

  protected confidenceClass(value: InternalTransferSuggestion['confidence']): string {
    if (value === 'ALTA') {
      return 'border-emerald-200 bg-emerald-50 text-emerald-800';
    }
    if (value === 'MEDIA') {
      return 'border-amber-200 bg-amber-50 text-amber-800';
    }
    return 'border-rose-200 bg-rose-50 text-rose-800';
  }

  protected confidenceLabel(value: InternalTransferSuggestion['confidence']): string {
    if (value === 'ALTA') {
      return 'Alta';
    }
    if (value === 'MEDIA') {
      return 'Media';
    }
    return 'Baixa';
  }

  private pairKey(item: InternalTransferSuggestion): string {
    return `${item.billId}:${item.incomeId}`;
  }

  private resolveToleranceDays(): number {
    const value = Number(this.detectForm.controls.dateToleranceDays.value);
    if (!Number.isFinite(value)) {
      return 1;
    }
    return Math.max(0, Math.min(5, Math.floor(value)));
  }

  private calculateDaysDiff(firstDate: string, secondDate: string): number {
    const first = new Date(firstDate + 'T00:00:00');
    const second = new Date(secondDate + 'T00:00:00');
    const diffMs = Math.abs(second.getTime() - first.getTime());
    return Math.floor(diffMs / (1000 * 60 * 60 * 24));
  }

  private resolveBankLabel(bankAccountId?: string | null): string {
    if (!bankAccountId) {
      return 'Sem conta vinculada';
    }
    const account = this.facade.bankAccounts().find((item) => item.id === bankAccountId);
    return account?.label ?? 'Conta nao localizada';
  }
}
