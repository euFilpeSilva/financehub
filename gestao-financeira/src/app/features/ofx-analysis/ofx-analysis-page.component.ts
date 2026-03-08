import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { FileSearch, LucideAngularModule } from 'lucide-angular';
import { startWith } from 'rxjs/operators';
import { OfxAnalysisTransaction } from '../../models/finance.models';
import { FinanceFacade } from '../../services/finance.facade';
import { ToastService } from '../../shared/toast/toast.service';

interface OfxPatternGroupView {
  patternKey: string;
  sampleMemo: string;
  totalCount: number;
  creditCount: number;
  debitCount: number;
  ignoredCount: number;
  internalCount: number;
  duplicatePairCount: number;
  totalCreditAmount: number;
  totalDebitAmount: number;
}

@Component({
  selector: 'app-ofx-analysis-page',
  imports: [CommonModule, ReactiveFormsModule, CurrencyPipe, DatePipe, LucideAngularModule],
  templateUrl: './ofx-analysis-page.component.html'
})
export class OfxAnalysisPageComponent {
  protected readonly FileSearch = FileSearch;

  private readonly fb = inject(FormBuilder);
  private readonly toast = inject(ToastService);

  protected readonly uploadForm = this.fb.nonNullable.group({
    ownerName: [''],
    ownerCpf: ['']
  });

  protected readonly filterForm = this.fb.nonNullable.group({
    startDate: [''],
    endDate: [''],
    ownerBankAccountId: ['ALL'],
    year: ['ALL'],
    month: ['ALL'],
    direction: ['ALL'],
    sourceType: ['ALL'],
    query: [''],
    ignoredOnly: [false],
    internalOnly: [false],
    duplicatePairOnly: [false]
  });

  private readonly filterValues = toSignal(
    this.filterForm.valueChanges.pipe(startWith(this.filterForm.getRawValue())),
    { initialValue: this.filterForm.getRawValue() }
  );

  protected readonly selectedFiles = signal<File[]>([]);
  protected readonly busy = signal(false);
  protected readonly selectedPatternKey = signal<string | null>(null);
  protected readonly analysis = signal<{
    totalFiles: number;
    totalTransactions: number;
    totalCredits: number;
    totalDebits: number;
    availableYears: number[];
    availableYearMonths: string[];
    transactions: OfxAnalysisTransaction[];
  } | null>(null);

  protected readonly monthOptions = computed(() => {
    const data = this.analysis();
    if (!data) {
      return [] as string[];
    }
    const year = this.filterValues().year;
    const months = new Set<string>();
    for (const yearMonth of data.availableYearMonths) {
      if (year === 'ALL' || yearMonth.startsWith(`${year}-`)) {
        months.add(yearMonth.split('-')[1]);
      }
    }
    return Array.from(months).sort();
  });

  protected readonly ownerBankOptions = computed(() => {
    const data = this.analysis();
    if (!data) {
      return [] as Array<{ value: string; label: string }>;
    }

    const map = new Map<string, string>();
    for (const item of data.transactions) {
      const key = item.ofxOwnerBankAccountId && item.ofxOwnerBankAccountId.trim().length > 0
        ? item.ofxOwnerBankAccountId
        : '__UNKNOWN__';
      const label = item.ofxOwnerBankLabel && item.ofxOwnerBankLabel.trim().length > 0
        ? item.ofxOwnerBankLabel
        : 'Banco nao identificado';
      if (!map.has(key)) {
        map.set(key, label);
      }
    }

    return Array.from(map.entries())
      .map(([value, label]) => ({ value, label }))
      .sort((a, b) => a.label.localeCompare(b.label));
  });

  private readonly baseFilteredTransactions = computed(() => {
    const data = this.analysis();
    if (!data) {
      return [] as OfxAnalysisTransaction[];
    }

    const filters = this.filterValues();
    const query = (filters.query ?? '').trim().toUpperCase();

    return data.transactions.filter((item) => {
      if (filters.startDate && item.postedAt < filters.startDate) {
        return false;
      }
      if (filters.endDate && item.postedAt > filters.endDate) {
        return false;
      }
      if (filters.ownerBankAccountId !== 'ALL') {
        const ownerBankKey = item.ofxOwnerBankAccountId && item.ofxOwnerBankAccountId.trim().length > 0
          ? item.ofxOwnerBankAccountId
          : '__UNKNOWN__';
        if (ownerBankKey !== filters.ownerBankAccountId) {
          return false;
        }
      }
      if (filters.year !== 'ALL' && String(item.year) !== filters.year) {
        return false;
      }
      if (filters.month !== 'ALL' && item.yearMonth.split('-')[1] !== filters.month) {
        return false;
      }
      if (filters.direction !== 'ALL' && item.direction !== filters.direction) {
        return false;
      }
      if (filters.sourceType !== 'ALL' && item.sourceType !== filters.sourceType) {
        return false;
      }
      if (filters.ignoredOnly && !item.ignoredByMarker) {
        return false;
      }
      if (filters.internalOnly && !item.likelyInternalTransfer) {
        return false;
      }
      if (filters.duplicatePairOnly && !item.itauPairDuplicateCandidate) {
        return false;
      }
      if (!query) {
        return true;
      }
      const text = `${item.memo} ${item.patternKey} ${item.normalizedMemo}`.toUpperCase();
      return text.includes(query);
    });
  });

  protected readonly displayedTransactions = computed(() => {
    const items = this.baseFilteredTransactions();
    const selectedPatternKey = this.selectedPatternKey();
    if (!selectedPatternKey) {
      return items;
    }
    return items.filter((item) => item.patternKey === selectedPatternKey);
  });

  protected readonly groupedPatterns = computed(() => {
    const map = new Map<string, OfxPatternGroupView>();
    for (const item of this.baseFilteredTransactions()) {
      const current = map.get(item.patternKey) ?? {
        patternKey: item.patternKey,
        sampleMemo: item.memo,
        totalCount: 0,
        creditCount: 0,
        debitCount: 0,
        ignoredCount: 0,
        internalCount: 0,
        duplicatePairCount: 0,
        totalCreditAmount: 0,
        totalDebitAmount: 0
      };

      current.totalCount += 1;
      if (item.direction === 'credit') {
        current.creditCount += 1;
        current.totalCreditAmount += item.amount;
      } else {
        current.debitCount += 1;
        current.totalDebitAmount += Math.abs(item.amount);
      }
      if (item.ignoredByMarker) {
        current.ignoredCount += 1;
      }
      if (item.likelyInternalTransfer) {
        current.internalCount += 1;
      }
      if (item.itauPairDuplicateCandidate) {
        current.duplicatePairCount += 1;
      }

      map.set(item.patternKey, current);
    }

    return Array.from(map.values()).sort((a, b) => b.totalCount - a.totalCount);
  });

  constructor(protected readonly facade: FinanceFacade) {}

  protected onFileSelection(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    const files = input?.files ? Array.from(input.files) : [];
    const ofxFiles = files.filter((file) => file.name.toLowerCase().endsWith('.ofx'));
    this.selectedFiles.set(ofxFiles);

    if (!ofxFiles.length) {
      this.toast.error('Selecione ao menos um arquivo OFX valido.');
    }

    if (input) {
      input.value = '';
    }
  }

  protected async runAnalysis(): Promise<void> {
    const files = this.selectedFiles();
    if (!files.length) {
      this.toast.error('Selecione ao menos um arquivo OFX para analisar.');
      return;
    }

    this.busy.set(true);
    try {
      const result = await this.facade.analyzeOfxStatements(
        files,
        this.uploadForm.controls.ownerName.value,
        this.uploadForm.controls.ownerCpf.value
      );
      this.analysis.set(result);
      this.selectedPatternKey.set(null);
      this.toast.success(`Analise concluida: ${result.totalTransactions} transacao(oes) mapeada(s).`);
    } catch {
      this.toast.error('Falha ao executar analise OFX.');
    } finally {
      this.busy.set(false);
    }
  }

  protected clearAll(): void {
    this.selectedFiles.set([]);
    this.analysis.set(null);
    this.filterForm.reset({
      startDate: '',
      endDate: '',
      ownerBankAccountId: 'ALL',
      year: 'ALL',
      month: 'ALL',
      direction: 'ALL',
      sourceType: 'ALL',
      query: '',
      ignoredOnly: false,
      internalOnly: false,
      duplicatePairOnly: false
    });
    this.selectedPatternKey.set(null);
  }

  protected togglePatternSelection(patternKey: string): void {
    if (this.selectedPatternKey() === patternKey) {
      this.selectedPatternKey.set(null);
      return;
    }
    this.selectedPatternKey.set(patternKey);
  }

  protected clearPatternSelection(): void {
    this.selectedPatternKey.set(null);
  }

  protected isPatternSelected(patternKey: string): boolean {
    return this.selectedPatternKey() === patternKey;
  }
}
