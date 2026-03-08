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

  protected readonly groupedPatternsSummary = computed(() => {
    let totalItems = 0;
    let totalCredits = 0;
    let totalDebits = 0;
    for (const group of this.groupedPatterns()) {
      totalItems += group.totalCount;
      totalCredits += group.totalCreditAmount;
      totalDebits += group.totalDebitAmount;
    }
    return {
      totalItems,
      totalCredits,
      totalDebits,
      netAmount: totalCredits - totalDebits
    };
  });

  protected readonly displayedTransactionsSummary = computed(() => {
    let totalItems = 0;
    let totalCredits = 0;
    let totalDebits = 0;
    for (const item of this.displayedTransactions()) {
      totalItems += 1;
      if (item.amount >= 0) {
        totalCredits += item.amount;
      } else {
        totalDebits += Math.abs(item.amount);
      }
    }
    return {
      totalItems,
      totalCredits,
      totalDebits,
      netAmount: totalCredits - totalDebits
    };
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

  protected exportFilteredCsv(): void {
    const groups = this.groupedPatterns();
    const transactions = this.displayedTransactions();
    if (!groups.length && !transactions.length) {
      this.toast.error('Nao ha dados filtrados para exportar.');
      return;
    }

    const lines: string[] = [];
    lines.push('RESUMO;METRICA;VALOR');
    lines.push(`GRUPOS FILTRADOS;Quantidade;${this.groupedPatternsSummary().totalItems}`);
    lines.push(`GRUPOS FILTRADOS;Entradas;${this.groupedPatternsSummary().totalCredits.toFixed(2)}`);
    lines.push(`GRUPOS FILTRADOS;Saidas;${this.groupedPatternsSummary().totalDebits.toFixed(2)}`);
    lines.push(`GRUPOS FILTRADOS;Saldo liquido;${this.groupedPatternsSummary().netAmount.toFixed(2)}`);
    lines.push(`TRANSACOES EXIBIDAS;Quantidade;${this.displayedTransactionsSummary().totalItems}`);
    lines.push(`TRANSACOES EXIBIDAS;Entradas;${this.displayedTransactionsSummary().totalCredits.toFixed(2)}`);
    lines.push(`TRANSACOES EXIBIDAS;Saidas;${this.displayedTransactionsSummary().totalDebits.toFixed(2)}`);
    lines.push(`TRANSACOES EXIBIDAS;Saldo liquido;${this.displayedTransactionsSummary().netAmount.toFixed(2)}`);
    lines.push('');

    lines.push('GRUPOS');
    lines.push('PADRAO;AMOSTRA;QTD;ENTRADAS_VALOR;SAIDAS_VALOR;IGNORADAS;INTERNAS;PAR_DUPLICADO');
    for (const group of groups) {
      lines.push([
        this.csvCell(group.patternKey),
        this.csvCell(group.sampleMemo),
        group.totalCount,
        group.totalCreditAmount.toFixed(2),
        group.totalDebitAmount.toFixed(2),
        group.ignoredCount,
        group.internalCount,
        group.duplicatePairCount
      ].join(';'));
    }
    lines.push('');

    lines.push('TRANSACOES');
    lines.push('DATA;ANO_MES;DIRECAO;VALOR;BANCO_OFX;ARQUIVO;PADRAO;DESCRICAO;IGNORADA;INTERNA;PAR_DUPLICADO');
    for (const item of transactions) {
      lines.push([
        this.csvCell(item.postedAt),
        this.csvCell(item.yearMonth),
        this.csvCell(item.direction),
        item.amount.toFixed(2),
        this.csvCell(item.ofxOwnerBankLabel || 'Banco nao identificado'),
        this.csvCell(item.fileName),
        this.csvCell(item.patternKey),
        this.csvCell(item.memo),
        item.ignoredByMarker ? 'SIM' : 'NAO',
        item.likelyInternalTransfer ? 'SIM' : 'NAO',
        item.itauPairDuplicateCandidate ? 'SIM' : 'NAO'
      ].join(';'));
    }

    const csvContent = '\uFEFF' + lines.join('\n');
    this.downloadTextFile(`ofx-analise-filtrada-${this.timestampForFile()}.csv`, 'text/csv;charset=utf-8;', csvContent);
    this.toast.success('Exportacao CSV concluida.');
  }

  protected exportFilteredPdf(): void {
    const groups = this.groupedPatterns();
    const transactions = this.displayedTransactions();
    if (!groups.length && !transactions.length) {
      this.toast.error('Nao ha dados filtrados para exportar.');
      return;
    }

    const now = new Date();
    const summaryGroups = this.groupedPatternsSummary();
    const summaryTransactions = this.displayedTransactionsSummary();

    const groupsRows = groups.map((group) => `
      <tr>
        <td>${this.escapeHtml(group.patternKey)}</td>
        <td>${group.totalCount}</td>
        <td>${this.formatCurrency(group.totalCreditAmount)}</td>
        <td>${this.formatCurrency(group.totalDebitAmount)}</td>
        <td>${group.ignoredCount}</td>
        <td>${group.internalCount}</td>
        <td>${group.duplicatePairCount}</td>
      </tr>
    `).join('');

    const txRows = transactions.map((item) => `
      <tr>
        <td>${this.escapeHtml(this.formatDate(item.postedAt))}</td>
        <td>${this.escapeHtml(item.yearMonth)}</td>
        <td>${this.escapeHtml(item.direction)}</td>
        <td>${this.formatCurrency(item.amount)}</td>
        <td>${this.escapeHtml(item.ofxOwnerBankLabel || 'Banco nao identificado')}</td>
        <td>${this.escapeHtml(item.fileName)}</td>
        <td>${this.escapeHtml(item.patternKey)}</td>
        <td>${this.escapeHtml(item.memo)}</td>
      </tr>
    `).join('');

    const html = `
      <html>
        <head>
          <meta charset="utf-8" />
          <title>Analise OFX Filtrada</title>
          <style>
            body { font-family: Arial, sans-serif; margin: 24px; color: #0f172a; }
            h1, h2 { margin: 0 0 10px; }
            h2 { margin-top: 20px; font-size: 16px; }
            .meta { margin-bottom: 12px; font-size: 12px; color: #334155; }
            .cards { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; margin-bottom: 12px; }
            .card { border: 1px solid #cbd5e1; border-radius: 8px; padding: 8px; font-size: 12px; }
            table { width: 100%; border-collapse: collapse; margin-top: 8px; font-size: 11px; }
            th, td { border: 1px solid #cbd5e1; padding: 6px; text-align: left; vertical-align: top; }
            th { background: #f8fafc; }
          </style>
        </head>
        <body>
          <h1>Analise OFX - Exportacao Filtrada</h1>
          <p class="meta">Gerado em ${this.escapeHtml(now.toLocaleString('pt-BR'))}</p>

          <h2>Resumo Grupos</h2>
          <div class="cards">
            <div class="card"><strong>Qtd.</strong><br/>${summaryGroups.totalItems}</div>
            <div class="card"><strong>Entradas</strong><br/>${this.formatCurrency(summaryGroups.totalCredits)}</div>
            <div class="card"><strong>Saidas</strong><br/>${this.formatCurrency(summaryGroups.totalDebits)}</div>
            <div class="card"><strong>Saldo</strong><br/>${this.formatCurrency(summaryGroups.netAmount)}</div>
          </div>

          <h2>Resumo Transacoes</h2>
          <div class="cards">
            <div class="card"><strong>Qtd.</strong><br/>${summaryTransactions.totalItems}</div>
            <div class="card"><strong>Entradas</strong><br/>${this.formatCurrency(summaryTransactions.totalCredits)}</div>
            <div class="card"><strong>Saidas</strong><br/>${this.formatCurrency(summaryTransactions.totalDebits)}</div>
            <div class="card"><strong>Saldo</strong><br/>${this.formatCurrency(summaryTransactions.netAmount)}</div>
          </div>

          <h2>Grupos Filtrados</h2>
          <table>
            <thead>
              <tr><th>Padrao</th><th>Qtd</th><th>Entradas</th><th>Saidas</th><th>Ignoradas</th><th>Internas</th><th>Par duplicado</th></tr>
            </thead>
            <tbody>${groupsRows}</tbody>
          </table>

          <h2>Transacoes Filtradas</h2>
          <table>
            <thead>
              <tr><th>Data</th><th>Ano/Mes</th><th>Direcao</th><th>Valor</th><th>Banco OFX</th><th>Arquivo</th><th>Padrao</th><th>Descricao</th></tr>
            </thead>
            <tbody>${txRows}</tbody>
          </table>
        </body>
      </html>
    `;

    const popup = window.open('', '_blank', 'width=1200,height=800');
    if (!popup) {
      this.toast.error('Nao foi possivel abrir a janela para exportacao em PDF.');
      return;
    }

    popup.document.open();
    popup.document.write(html);
    popup.document.close();
    popup.focus();
    popup.print();
    this.toast.success('Visualizacao para PDF aberta. Use "Salvar como PDF" no dialog de impressao.');
  }

  private csvCell(value: string): string {
    const safe = String(value ?? '').replace(/"/g, '""');
    return `"${safe}"`;
  }

  private downloadTextFile(fileName: string, mimeType: string, content: string): void {
    const blob = new Blob([content], { type: mimeType });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = fileName;
    anchor.click();
    URL.revokeObjectURL(url);
  }

  private timestampForFile(): string {
    const now = new Date();
    const pad = (value: number) => String(value).padStart(2, '0');
    return `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}_${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
  }

  private formatDate(value: string): string {
    if (!value || value.length < 10) {
      return value;
    }
    const [year, month, day] = value.split('-');
    return `${day}/${month}/${year}`;
  }

  private formatCurrency(value: number): string {
    return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value);
  }

  private escapeHtml(value: string): string {
    return String(value ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }
}
