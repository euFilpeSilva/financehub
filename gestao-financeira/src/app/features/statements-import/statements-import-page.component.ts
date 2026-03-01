import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { FileUp, FolderOpen, LucideAngularModule, UploadCloud } from 'lucide-angular';
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
    const files = event.dataTransfer?.files;
    if (!files || files.length === 0) {
      return;
    }
    this.importFiles(files);
  }

  protected onFileSelection(event: Event): void {
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
    for (const file of files) {
      this.facade.importOfxStatement(file, ownerName, ownerCpf);
    }
  }
}
