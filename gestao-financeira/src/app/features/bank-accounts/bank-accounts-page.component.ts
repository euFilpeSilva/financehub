import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Building2, Check, LucideAngularModule, Pencil, Plus, Save, Trash2, X } from 'lucide-angular';
import { BankAccount } from '../../models/finance.models';
import { FinanceFacade } from '../../services/finance.facade';
import { ConfirmDialogService } from '../../shared/confirm-dialog/confirm-dialog.service';
import { ToastService } from '../../shared/toast/toast.service';

@Component({
  selector: 'app-bank-accounts-page',
  imports: [CommonModule, ReactiveFormsModule, LucideAngularModule],
  templateUrl: './bank-accounts-page.component.html'
})
export class BankAccountsPageComponent {
  protected readonly Building2 = Building2;
  protected readonly Check = Check;
  protected readonly Pencil = Pencil;
  protected readonly Plus = Plus;
  protected readonly Save = Save;
  protected readonly Trash2 = Trash2;
  protected readonly X = X;

  protected editingId: string | null = null;

  private readonly fb = inject(FormBuilder);
  private readonly confirmDialog = inject(ConfirmDialogService);
  private readonly toast = inject(ToastService);

  protected readonly accountForm = this.fb.nonNullable.group({
    label: ['', [Validators.required, Validators.maxLength(120)]],
    bankId: ['', [Validators.required, Validators.maxLength(20)]],
    branchId: ['', [Validators.maxLength(20)]],
    accountId: ['', [Validators.required, Validators.maxLength(40)]],
    primaryIncome: [false],
    active: [true]
  });

  constructor(protected readonly facade: FinanceFacade) {
  }

  protected async save(): Promise<void> {
    if (this.accountForm.invalid) {
      this.accountForm.markAllAsTouched();
      this.toast.error('Preencha os campos obrigatorios da conta bancaria.');
      return;
    }

    const value = this.accountForm.getRawValue();
    if (this.editingId) {
      const current = this.facade.bankAccounts().find((item) => item.id === this.editingId);
      if (!current) {
        return;
      }
      this.facade.updateBankAccount({
        ...current,
        label: this.normalize(value.label),
        bankId: this.onlyDigits(value.bankId),
        branchId: this.normalizeOptional(value.branchId),
        accountId: this.normalizeAccountId(value.accountId),
        primaryIncome: value.primaryIncome,
        active: value.active
      });
    } else {
      this.facade.addBankAccount({
        label: this.normalize(value.label),
        bankId: this.onlyDigits(value.bankId),
        branchId: this.normalizeOptional(value.branchId),
        accountId: this.normalizeAccountId(value.accountId),
        primaryIncome: value.primaryIncome,
        active: value.active
      });
    }

    this.resetForm();
  }

  protected startEdit(account: BankAccount): void {
    this.editingId = account.id;
    this.accountForm.patchValue({
      label: account.label,
      bankId: account.bankId,
      branchId: account.branchId ?? '',
      accountId: account.accountId,
      primaryIncome: account.primaryIncome,
      active: account.active
    });
  }

  protected cancelEdit(): void {
    this.resetForm();
  }

  protected async setPrimary(account: BankAccount): Promise<void> {
    if (account.primaryIncome) {
      return;
    }

    const confirmed = await this.confirmDialog.confirm({
      title: 'Marcar conta elegivel',
      message: `Deseja marcar "${account.label}" como elegivel para entrada?`,
      confirmLabel: 'Marcar'
    });

    if (!confirmed) {
      return;
    }

    this.facade.updateBankAccount({
      ...account,
      primaryIncome: true
    });
  }

  protected async remove(account: BankAccount): Promise<void> {
    const confirmed = await this.confirmDialog.confirm({
      title: 'Excluir conta bancaria',
      message: `Deseja excluir "${account.label}"?`,
      confirmLabel: 'Excluir',
      tone: 'danger'
    });

    if (!confirmed) {
      return;
    }

    if (this.editingId === account.id) {
      this.resetForm();
    }

    this.facade.deleteBankAccount(account.id);
  }

  private resetForm(): void {
    this.editingId = null;
    this.accountForm.reset({
      label: '',
      bankId: '',
      branchId: '',
      accountId: '',
      primaryIncome: false,
      active: true
    });
  }

  private normalize(value: string): string {
    return value.trim().replace(/\s+/g, ' ');
  }

  private normalizeOptional(value: string): string | null {
    const normalized = this.normalize(value);
    return normalized ? normalized : null;
  }

  private onlyDigits(value: string): string {
    return value.replace(/\D/g, '');
  }

  private normalizeAccountId(value: string): string {
    return value.replace(/[^0-9A-Za-z]/g, '').toUpperCase();
  }
}
