import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { FileUp, FolderOpen, LucideAngularModule, UploadCloud } from 'lucide-angular';
import { ImportedStatementYearCleanupResult } from '../../models/finance.models';
import { FinanceFacade } from '../../services/finance.facade';
import { ConfirmDialogService } from '../../shared/confirm-dialog/confirm-dialog.service';
import { ToastService } from '../../shared/toast/toast.service';

interface StatementCleanupProgressState {
  running: boolean;
  status: 'idle' | 'running' | 'success' | 'error';
  phase: string;
  progress: number;
  details: string;
}

@Component({
  selector: 'app-statements-import-page',
  imports: [CommonModule, ReactiveFormsModule, LucideAngularModule],
  templateUrl: './statements-import-page.component.html'
})
export class StatementsImportPageComponent {
  protected readonly UploadCloud = UploadCloud;
  protected readonly FolderOpen = FolderOpen;
  protected readonly FileUp = FileUp;

  private readonly fb = inject(FormBuilder);
  private readonly confirmDialog = inject(ConfirmDialogService);
  private readonly toast = inject(ToastService);

  protected readonly form = this.fb.nonNullable.group({
    ownerName: ['FILIPE SOUSA DA SILVA'],
    ownerCpf: ['05287907141']
  });
  protected readonly cleanupForm = this.fb.nonNullable.group({
    year: [new Date().getFullYear()],
    month: ['ALL'],
    bankAccountIds: [[] as string[]]
  });
  protected readonly cleanupMonthOptions = [
    { value: 'ALL', label: 'Todos os meses' },
    { value: '1', label: 'Janeiro' },
    { value: '2', label: 'Fevereiro' },
    { value: '3', label: 'Marco' },
    { value: '4', label: 'Abril' },
    { value: '5', label: 'Maio' },
    { value: '6', label: 'Junho' },
    { value: '7', label: 'Julho' },
    { value: '8', label: 'Agosto' },
    { value: '9', label: 'Setembro' },
    { value: '10', label: 'Outubro' },
    { value: '11', label: 'Novembro' },
    { value: '12', label: 'Dezembro' }
  ];
  protected readonly dragging = signal(false);
  protected readonly cleanupBusy = signal(false);
  protected readonly cleanupPreview = signal<ImportedStatementYearCleanupResult | null>(null);
  protected readonly cleanupHistoryYearFilter = signal('ALL');
  protected readonly cleanupHistoryActionFilter = signal<'ALL' | 'delete' | 'purge'>('ALL');
  protected readonly cleanupHistoryYearOptions = computed(() => {
    const years = new Set<string>();
    for (const event of this.facade.auditEvents()) {
      if (
        event.entityType === 'statement'
          && (event.action === 'delete' || event.action === 'purge')
          && event.message.toUpperCase().includes('LIMPEZA DE IMPORTADOS DO ANO')
      ) {
        const year = this.extractYearFromCleanupMessage(event.message);
        if (year) {
          years.add(String(year));
        }
      }
    }
    return Array.from(years).sort((a, b) => Number(b) - Number(a));
  });
  protected readonly cleanupHistory = computed(() =>
    this.facade.auditEvents()
      .filter((event) =>
        event.entityType === 'statement'
          && (event.action === 'delete' || event.action === 'purge')
          && event.message.toUpperCase().includes('LIMPEZA DE IMPORTADOS DO ANO')
      )
      .filter((event) => {
        const actionFilter = this.cleanupHistoryActionFilter();
        if (actionFilter === 'ALL') {
          return true;
        }
        return event.action === actionFilter;
      })
      .filter((event) => {
        const yearFilter = this.cleanupHistoryYearFilter();
        if (yearFilter === 'ALL') {
          return true;
        }
        const year = this.extractYearFromCleanupMessage(event.message);
        return year !== null && String(year) === yearFilter;
      })
      .slice(0, 8)
  );
  protected readonly cleanupProgress = signal<StatementCleanupProgressState>({
    running: false,
    status: 'idle',
    phase: 'Aguardando',
    progress: 0,
    details: 'Selecione o ano para visualizar e executar a limpeza.'
  });

  constructor(protected readonly facade: FinanceFacade) {}

  protected onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(true);
  }

  protected onDragLeave(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
  }

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
    if (this.facade.ofxImportBatchProgress().running) {
      return;
    }
    const files = event.dataTransfer?.files;
    if (!files || files.length === 0) {
      return;
    }
    this.importFiles(files);
  }

  protected onFileSelection(event: Event): void {
    if (this.facade.ofxImportBatchProgress().running) {
      return;
    }
    const input = event.target as HTMLInputElement | null;
    if (!input?.files?.length) {
      return;
    }
    this.importFiles(input.files);
    input.value = '';
  }

  protected hasEligibleIncomeAccount(): boolean {
    return this.facade.bankAccounts().some((account) => account.active && account.primaryIncome);
  }

  protected async runImportedYearCleanup(): Promise<void> {
    if (this.cleanupBusy()) {
      return;
    }

    this.cleanupBusy.set(true);

    try {
      const year = Number(this.cleanupForm.controls.year.value);
      if (!Number.isFinite(year) || year < 2000 || year > 2100) {
        this.toast.error('Informe um ano valido entre 2000 e 2100.');
        return;
      }

      const selectedBankAccountIds = this.cleanupForm.controls.bankAccountIds.value;
      const bankAccountIds = selectedBankAccountIds.length ? selectedBankAccountIds : null;
      const selectedMonth = this.cleanupForm.controls.month.value;
      const month = selectedMonth === 'ALL' ? null : Number(selectedMonth);

      if (month !== null && (!Number.isFinite(month) || month < 1 || month > 12)) {
        this.toast.error('Informe um mes valido entre 1 e 12.');
        return;
      }

      this.cleanupProgress.set({
        running: false,
        status: 'idle',
        phase: 'Calculando previa',
        progress: 0,
        details: `Levantando registros importados para ${year}...`
      });

      let preview: ImportedStatementYearCleanupResult;
      try {
        preview = await this.facade.cleanupImportedStatementYear({
          year,
          month,
          bankAccountIds,
          dryRun: true,
          permanentDelete: false
        });
      } catch {
        this.cleanupProgress.set({
          running: false,
          status: 'error',
          phase: 'Falha na analise',
          progress: 0,
          details: 'Nao foi possivel calcular a previa da limpeza.'
        });
        this.toast.error('Falha ao gerar previa da limpeza.');
        return;
      }

      this.cleanupPreview.set(preview);

      if (preview.totalMatched === 0) {
        this.cleanupProgress.set({
          running: false,
          status: 'success',
          phase: 'Nenhum dado encontrado',
          progress: 0,
          details: 'Nao existem registros importados para os filtros selecionados.'
        });
        this.toast.info('Nenhum dado importado encontrado para o periodo informado.');
        return;
      }

      const periodText = `${this.formatDate(preview.startDate)} ate ${this.formatDate(preview.endDate)}`;
      const scopeText = this.resolveBankScopeLabel(preview.bankAccountIds);

      const confirmation = await this.confirmDialog.confirmWithCheckbox({
        title: 'Confirmar limpeza de importacao',
        message:
          `Periodo: ${periodText}. Escopo: ${scopeText}. Registros que serao afetados: ${preview.totalMatched} (Entradas: ${preview.matchedIncomes}, Saidas: ${preview.matchedBills}).`,
        confirmLabel: 'Executar limpeza',
        cancelLabel: 'Cancelar',
        tone: 'danger',
        checkboxLabel: 'Excluir definitivamente (se desmarcado, envia para a lixeira)',
        checkboxDefaultChecked: false
      });

      if (!confirmation.confirmed) {
        this.cleanupProgress.set({
          running: false,
          status: 'idle',
          phase: 'Operacao cancelada',
          progress: 0,
          details: 'A limpeza foi cancelada antes da execucao.'
        });
        return;
      }

      this.cleanupProgress.set({
        running: true,
        status: 'running',
        phase: confirmation.checkboxChecked ? 'Exclusao definitiva em andamento' : 'Movendo para lixeira',
        progress: 55,
        details: confirmation.checkboxChecked
          ? 'Aplicando exclusao definitiva dos registros selecionados.'
          : 'Movendo registros selecionados para a lixeira.'
      });

      try {
        const result = await this.facade.cleanupImportedStatementYear({
          year,
          month,
          bankAccountIds,
          dryRun: false,
          permanentDelete: confirmation.checkboxChecked
        });

        this.cleanupPreview.set(result);
        this.cleanupProgress.set({
          running: false,
          status: 'success',
          phase: 'Limpeza concluida',
          progress: 100,
          details: confirmation.checkboxChecked
            ? `${result.totalProcessed} registro(s) excluido(s) definitivamente.`
            : `${result.totalProcessed} registro(s) enviado(s) para a lixeira.`
        });

        if (confirmation.checkboxChecked) {
          this.toast.success(`Limpeza concluida: ${result.totalProcessed} registro(s) excluido(s) definitivamente.`);
        } else {
          this.toast.success(`Limpeza concluida: ${result.totalProcessed} registro(s) enviado(s) para a lixeira.`);
        }
      } catch {
        this.cleanupProgress.set({
          running: false,
          status: 'error',
          phase: 'Falha na execucao',
          progress: 100,
          details: 'Ocorreu um erro durante a limpeza dos dados importados.'
        });
        this.toast.error('Falha ao executar limpeza de dados importados.');
      }
    } finally {
      this.cleanupBusy.set(false);
    }
  }

  protected clearCleanupProgress(): void {
    if (this.cleanupBusy()) {
      return;
    }
    this.cleanupProgress.set({
      running: false,
      status: 'idle',
      phase: 'Aguardando',
      progress: 0,
      details: 'Selecione o ano para visualizar e executar a limpeza.'
    });
    this.cleanupPreview.set(null);
  }

  private async importFiles(fileList: FileList): Promise<void> {
    if (!this.hasEligibleIncomeAccount()) {
      this.toast.error(
        'Importacao OFX bloqueada: cadastre e marque pelo menos uma conta ativa como elegivel para entrada em Contas bancarias.'
      );
      return;
    }

    const ownerName = this.form.controls.ownerName.value.trim();
    const ownerCpf = this.form.controls.ownerCpf.value.trim();
    const files = Array.from(fileList).filter((file) => file.name.toLowerCase().endsWith('.ofx'));
    if (!files.length) {
      return;
    }
    const confirmed = await this.confirmDialog.confirm({
      title: 'Confirmar importacao',
      message: `Deseja importar ${files.length} arquivo(s) OFX?`,
      confirmLabel: 'Importar'
    });
    if (!confirmed) {
      return;
    }

    const summary = await this.facade.importOfxBatch(files, ownerName, ownerCpf);
    if (summary.total === 0) {
      return;
    }

    if (summary.failureCount === 0) {
      this.toast.success(`Importacao concluida: ${summary.successCount} arquivo(s) processado(s).`);
      return;
    }

    if (summary.successCount === 0) {
      this.toast.error('Falha ao importar arquivos OFX.');
      return;
    }

    this.toast.info(
      `Importacao finalizada com pendencias: ${summary.successCount}/${summary.total} arquivo(s) importado(s).`
    );
  }

  protected toggleCleanupBankSelection(accountId: string): void {
    const current = this.cleanupForm.controls.bankAccountIds.value;
    const set = new Set(current);
    if (set.has(accountId)) {
      set.delete(accountId);
    } else {
      set.add(accountId);
    }
    this.cleanupForm.controls.bankAccountIds.setValue(Array.from(set));
  }

  protected clearCleanupBankSelection(): void {
    this.cleanupForm.controls.bankAccountIds.setValue([]);
  }

  protected selectAllCleanupBanks(): void {
    this.cleanupForm.controls.bankAccountIds.setValue(this.facade.bankAccounts().map((account) => account.id));
  }

  protected isCleanupBankSelected(accountId: string): boolean {
    return this.cleanupForm.controls.bankAccountIds.value.includes(accountId);
  }

  private resolveBankScopeLabel(bankAccountIds: string[] | null | undefined): string {
    if (!bankAccountIds || bankAccountIds.length === 0) {
      return 'Todas as contas';
    }
    if (bankAccountIds.length === 1) {
      const account = this.facade.bankAccounts().find((item) => item.id === bankAccountIds[0]);
      return account?.label ?? 'Conta selecionada';
    }
    return `${bankAccountIds.length} contas selecionadas`;
  }

  private formatDate(value: string): string {
    if (!value || value.length < 10) {
      return value;
    }
    const [year, month, day] = value.split('-');
    return `${day}/${month}/${year}`;
  }

  protected formatTimestamp(value: string): string {
    if (!value) {
      return '-';
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return value;
    }
    return date.toLocaleString('pt-BR');
  }

  protected setCleanupHistoryYearFilter(value: string): void {
    this.cleanupHistoryYearFilter.set(value || 'ALL');
  }

  protected setCleanupHistoryActionFilter(value: string): void {
    if (value === 'delete' || value === 'purge' || value === 'ALL') {
      this.cleanupHistoryActionFilter.set(value);
      return;
    }
    this.cleanupHistoryActionFilter.set('ALL');
  }

  private extractYearFromCleanupMessage(message: string): number | null {
    if (!message) {
      return null;
    }
    const match = message.match(/ano\s+(\d{4})/i);
    if (!match) {
      return null;
    }
    const year = Number(match[1]);
    return Number.isFinite(year) ? year : null;
  }
}
