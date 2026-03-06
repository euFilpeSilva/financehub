import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { FileUp, FolderOpen, LucideAngularModule, UploadCloud } from 'lucide-angular';
import { Subscription } from 'rxjs';
import { FinanceFacade } from '../../services/finance.facade';
import { ConfirmDialogService } from '../../shared/confirm-dialog/confirm-dialog.service';
import { ToastService } from '../../shared/toast/toast.service';

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
  protected readonly dragging = signal(false);
  protected readonly importing = signal(false);
  protected readonly currentFileName = signal('');
  protected readonly currentFileProgress = signal(0);
  protected readonly currentFilePhase = signal<'uploading' | 'processing' | 'completed'>('uploading');
  protected readonly processedFiles = signal(0);
  protected readonly totalFiles = signal(0);
  protected readonly importPhaseLabel = computed(() => {
    const phase = this.currentFilePhase();
    if (phase === 'uploading') {
      return 'Enviando arquivo';
    }
    if (phase === 'processing') {
      return 'Processando importacao';
    }
    return 'Concluido';
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
    if (this.importing()) {
      return;
    }
    const files = event.dataTransfer?.files;
    if (!files || files.length === 0) {
      return;
    }
    this.importFiles(files);
  }

  protected onFileSelection(event: Event): void {
    if (this.importing()) {
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

    this.importing.set(true);
    this.processedFiles.set(0);
    this.totalFiles.set(files.length);

    let successCount = 0;
    for (const file of files) {
      this.currentFileName.set(file.name);
      this.currentFileProgress.set(0);
      this.currentFilePhase.set('uploading');

      try {
        await this.runFileImportWithProgress(file, ownerName, ownerCpf);
        successCount += 1;
      } catch {
        this.toast.error(`Falha ao importar OFX: ${file.name}`);
      } finally {
        this.processedFiles.update((value) => value + 1);
      }
    }

    this.importing.set(false);
    this.currentFileName.set('');
    this.currentFileProgress.set(0);

    if (successCount === files.length) {
      this.toast.success(`Importacao concluida: ${successCount} arquivo(s) processado(s).`);
      return;
    }

    this.toast.info(
      `Importacao finalizada com pendencias: ${successCount}/${files.length} arquivo(s) importado(s).`
    );
  }

  private runFileImportWithProgress(file: File, ownerName: string, ownerCpf: string): Promise<void> {
    return new Promise((resolve, reject) => {
      let subscription: Subscription | null = null;
      subscription = this.facade.importOfxStatementWithProgress(file, ownerName, ownerCpf).subscribe({
        next: (event) => {
          this.currentFileName.set(event.fileName);
          this.currentFileProgress.set(event.progress);
          this.currentFilePhase.set(event.phase);
          if (event.kind === 'completed') {
            subscription?.unsubscribe();
            resolve();
          }
        },
        error: (error) => {
          subscription?.unsubscribe();
          reject(error);
        }
      });
    });
  }
}
