import { CommonModule } from '@angular/common';
import { Component, effect, inject } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Check, Clock3, LucideAngularModule, Moon, Pencil, Plus, Save, Settings, Sun, Trash2, X } from 'lucide-angular';
import { ThemeService } from '../../core/theme.service';
import { FinanceFacade } from '../../services/finance.facade';
import { ConfirmDialogService } from '../../shared/confirm-dialog/confirm-dialog.service';
import { ToastService } from '../../shared/toast/toast.service';

@Component({
  selector: 'app-settings-page',
  imports: [CommonModule, FormsModule, ReactiveFormsModule, LucideAngularModule],
  templateUrl: './settings-page.component.html'
})
export class SettingsPageComponent {
  protected readonly Check = Check;
  protected readonly Clock3 = Clock3;
  protected readonly Moon = Moon;
  protected readonly Pencil = Pencil;
  protected readonly Plus = Plus;
  protected readonly Save = Save;
  protected readonly Settings = Settings;
  protected readonly Sun = Sun;
  protected readonly Trash2 = Trash2;
  protected readonly X = X;

  private readonly fb = inject(FormBuilder);
  private readonly confirmDialog = inject(ConfirmDialogService);
  private readonly toast = inject(ToastService);

  protected readonly retentionForm = this.fb.nonNullable.group({
    trashRetentionDays: [30, [Validators.required, Validators.min(1), Validators.max(3650)]],
    auditRetentionDays: [180, [Validators.required, Validators.min(1), Validators.max(3650)]]
  });
  protected readonly preferencesForm = this.fb.nonNullable.group({
    defaultBillCategory: ['Moradia', Validators.required],
    defaultBillRecurring: [false],
    defaultBillDueDay: [5, [Validators.required, Validators.min(1), Validators.max(31)]],
    defaultIncomeCategory: ['Trabalho', Validators.required],
    defaultIncomeRecurring: [false],
    defaultIncomeReceivedDay: [1, [Validators.required, Validators.min(1), Validators.max(31)]],
    defaultDashboardMode: ['month' as 'month' | 'range', Validators.required],
    defaultDashboardMonthComparisonOffset: [1, [Validators.required, Validators.min(1), Validators.max(12)]]
  });

  protected billCategories: string[] = [];
  protected incomeCategories: string[] = [];

  protected newBillCategory = '';
  protected newIncomeCategory = '';
  protected editingBillCategoryIndex: number | null = null;
  protected editingIncomeCategoryIndex: number | null = null;
  protected editingBillCategoryValue = '';
  protected editingIncomeCategoryValue = '';

  constructor(
    protected readonly facade: FinanceFacade,
    protected readonly theme: ThemeService
  ) {
    effect(() => {
      const settings = this.facade.retentionSettings();
      this.retentionForm.patchValue({
        trashRetentionDays: settings.trashRetentionDays,
        auditRetentionDays: settings.auditRetentionDays
      });
    });
    effect(() => {
      const prefs = this.facade.appPreferences();
      this.preferencesForm.patchValue({
        defaultBillCategory: prefs.defaultBillCategory,
        defaultBillRecurring: prefs.defaultBillRecurring,
        defaultBillDueDay: prefs.defaultBillDueDay,
        defaultIncomeCategory: prefs.defaultIncomeCategory,
        defaultIncomeRecurring: prefs.defaultIncomeRecurring,
        defaultIncomeReceivedDay: prefs.defaultIncomeReceivedDay,
        defaultDashboardMode: prefs.defaultDashboardMode,
        defaultDashboardMonthComparisonOffset: prefs.defaultDashboardMonthComparisonOffset
      });
      this.billCategories = Array.isArray(prefs.billCategories) ? [...prefs.billCategories] : [];
      this.incomeCategories = Array.isArray(prefs.incomeCategories) ? [...prefs.incomeCategories] : [];
      this.ensureSelectedDefaults();
    });
  }

  protected setTheme(mode: 'light' | 'dark'): void {
    this.theme.setDarkMode(mode === 'dark');
  }

  protected async saveRetention(): Promise<void> {
    if (this.retentionForm.invalid) {
      this.retentionForm.markAllAsTouched();
      return;
    }
    const confirmed = await this.confirmDialog.confirm({
      title: 'Salvar retencao',
      message: 'Deseja salvar as configuracoes de retencao e auditoria?',
      confirmLabel: 'Salvar'
    });
    if (!confirmed) {
      return;
    }
    const value = this.retentionForm.getRawValue();
    this.facade.updateRetentionSettings({
      trashRetentionDays: Number(value.trashRetentionDays),
      auditRetentionDays: Number(value.auditRetentionDays)
    });
  }

  protected async savePreferences(): Promise<void> {
    await this.persistPreferences(false);
  }

  protected isPreferenceFieldInvalid(
    field:
      | 'defaultBillCategory'
      | 'defaultBillDueDay'
      | 'defaultIncomeCategory'
      | 'defaultIncomeReceivedDay'
      | 'defaultDashboardMode'
      | 'defaultDashboardMonthComparisonOffset'
  ): boolean {
    const control = this.preferencesForm.controls[field];
    return control.invalid && (control.touched || control.dirty);
  }

  protected async emergencyResetAllData(): Promise<void> {
    const result = await this.confirmDialog.confirmWithCheckbox({
      title: 'Zerar base de dados',
      message: 'Esta acao remove todos os dados de todas as tabelas. Deseja continuar?',
      confirmLabel: 'Zerar base',
      tone: 'danger',
      requiredText: 'ZERAR',
      requiredTextLabel: 'Digite ZERAR para confirmar',
      checkboxLabel: 'Manter contas bancarias cadastradas',
      checkboxDefaultChecked: true
    });
    if (!result.confirmed) {
      return;
    }
    this.facade.emergencyResetAllData(result.checkboxChecked);
  }

  private async persistPreferences(requireConfirmation: boolean): Promise<void> {
    if (this.preferencesForm.invalid) {
      this.preferencesForm.markAllAsTouched();
      this.toast.error('Corrija os campos de padroes antes de salvar categorias.');
      return;
    }
    if (requireConfirmation) {
      const confirmed = await this.confirmDialog.confirm({
        title: 'Salvar padroes',
        message: 'Deseja salvar os padroes da aplicacao?',
        confirmLabel: 'Salvar'
      });
      if (!confirmed) {
        return;
      }
    }
    const value = this.preferencesForm.getRawValue();
    const billCategories = this.normalizeCategories(this.billCategories);
    const incomeCategories = this.normalizeCategories(this.incomeCategories);
    if (!billCategories.length || !incomeCategories.length) {
      this.toast.error('Mantenha ao menos uma categoria para saidas e uma para entradas.');
      return;
    }

    this.facade.updateAppPreferences({
      defaultBillCategory: this.resolveDefaultCategory(value.defaultBillCategory, billCategories),
      defaultBillRecurring: value.defaultBillRecurring,
      defaultBillDueDay: Number(value.defaultBillDueDay),
      defaultIncomeCategory: this.resolveDefaultCategory(value.defaultIncomeCategory, incomeCategories),
      defaultIncomeRecurring: value.defaultIncomeRecurring,
      defaultIncomeReceivedDay: Number(value.defaultIncomeReceivedDay),
      defaultDashboardMode: value.defaultDashboardMode,
      defaultDashboardMonthComparisonOffset: Number(value.defaultDashboardMonthComparisonOffset),
      billCategories,
      incomeCategories
    });
  }

  protected addBillCategory(): void {
    const value = this.normalizeCategoryValue(this.newBillCategory);
    if (!value) {
      return;
    }
    if (this.hasCategory(this.billCategories, value)) {
      this.toast.error('Categoria de saida ja existe.');
      return;
    }
    this.billCategories = [...this.billCategories, value];
    this.newBillCategory = '';
    this.ensureSelectedDefaults();
    void this.persistPreferences(false);
  }

  protected addIncomeCategory(): void {
    const value = this.normalizeCategoryValue(this.newIncomeCategory);
    if (!value) {
      return;
    }
    if (this.hasCategory(this.incomeCategories, value)) {
      this.toast.error('Categoria de entrada ja existe.');
      return;
    }
    this.incomeCategories = [...this.incomeCategories, value];
    this.newIncomeCategory = '';
    this.ensureSelectedDefaults();
    void this.persistPreferences(false);
  }

  protected startEditBillCategory(index: number): void {
    this.editingBillCategoryIndex = index;
    this.editingBillCategoryValue = this.billCategories[index] ?? '';
  }

  protected startEditIncomeCategory(index: number): void {
    this.editingIncomeCategoryIndex = index;
    this.editingIncomeCategoryValue = this.incomeCategories[index] ?? '';
  }

  protected cancelBillCategoryEdit(): void {
    this.editingBillCategoryIndex = null;
    this.editingBillCategoryValue = '';
  }

  protected cancelIncomeCategoryEdit(): void {
    this.editingIncomeCategoryIndex = null;
    this.editingIncomeCategoryValue = '';
  }

  protected saveBillCategoryEdit(index: number): void {
    const value = this.normalizeCategoryValue(this.editingBillCategoryValue);
    if (!value) {
      return;
    }
    if (this.hasCategory(this.billCategories, value, index)) {
      this.toast.error('Categoria de saida ja existe.');
      return;
    }
    this.billCategories = this.billCategories.map((item, currentIndex) => (currentIndex === index ? value : item));
    this.cancelBillCategoryEdit();
    this.ensureSelectedDefaults();
    void this.persistPreferences(false);
  }

  protected saveIncomeCategoryEdit(index: number): void {
    const value = this.normalizeCategoryValue(this.editingIncomeCategoryValue);
    if (!value) {
      return;
    }
    if (this.hasCategory(this.incomeCategories, value, index)) {
      this.toast.error('Categoria de entrada ja existe.');
      return;
    }
    this.incomeCategories = this.incomeCategories.map((item, currentIndex) => (currentIndex === index ? value : item));
    this.cancelIncomeCategoryEdit();
    this.ensureSelectedDefaults();
    void this.persistPreferences(false);
  }

  protected async removeBillCategory(index: number): Promise<void> {
    if (this.billCategories.length <= 1) {
      this.toast.error('Mantenha ao menos uma categoria de saida.');
      return;
    }
    const category = this.billCategories[index];
    const confirmed = await this.confirmDialog.confirm({
      title: 'Excluir categoria',
      message: `Deseja excluir a categoria "${category}"?`,
      confirmLabel: 'Excluir',
      tone: 'danger'
    });
    if (!confirmed) {
      return;
    }
    this.billCategories = this.billCategories.filter((_, currentIndex) => currentIndex !== index);
    this.cancelBillCategoryEdit();
    this.ensureSelectedDefaults();
    await this.persistPreferences(false);
  }

  protected async removeIncomeCategory(index: number): Promise<void> {
    if (this.incomeCategories.length <= 1) {
      this.toast.error('Mantenha ao menos uma categoria de entrada.');
      return;
    }
    const category = this.incomeCategories[index];
    const confirmed = await this.confirmDialog.confirm({
      title: 'Excluir categoria',
      message: `Deseja excluir a categoria "${category}"?`,
      confirmLabel: 'Excluir',
      tone: 'danger'
    });
    if (!confirmed) {
      return;
    }
    this.incomeCategories = this.incomeCategories.filter((_, currentIndex) => currentIndex !== index);
    this.cancelIncomeCategoryEdit();
    this.ensureSelectedDefaults();
    await this.persistPreferences(false);
  }

  private normalizeCategoryValue(value: string): string {
    const normalized = value.trim().replace(/\s+/g, ' ');
    if (!normalized) {
      this.toast.error('Informe um nome de categoria valido.');
      return '';
    }
    if (normalized.length > 120) {
      this.toast.error('Categoria deve ter no maximo 120 caracteres.');
      return '';
    }
    return normalized;
  }

  private hasCategory(list: string[], value: string, skipIndex?: number): boolean {
    const normalized = value.toLowerCase();
    return list.some((item, index) => index !== skipIndex && item.toLowerCase() === normalized);
  }

  private normalizeCategories(list: string[]): string[] {
    const unique = new Map<string, string>();
    for (const raw of list) {
      const normalized = this.normalizeCategoryValue(raw);
      if (!normalized) {
        continue;
      }
      const key = normalized.toLowerCase();
      if (!unique.has(key)) {
        unique.set(key, normalized);
      }
    }
    return Array.from(unique.values());
  }

  private ensureSelectedDefaults(): void {
    const selectedBill = this.preferencesForm.controls.defaultBillCategory.value;
    const selectedIncome = this.preferencesForm.controls.defaultIncomeCategory.value;
    const safeBill = this.resolveDefaultCategory(selectedBill, this.billCategories);
    const safeIncome = this.resolveDefaultCategory(selectedIncome, this.incomeCategories);
    this.preferencesForm.patchValue({
      defaultBillCategory: safeBill,
      defaultIncomeCategory: safeIncome
    });
  }

  private resolveDefaultCategory(value: string, categories: string[]): string {
    const normalized = value?.trim().toLowerCase();
    const found = categories.find((item) => item.toLowerCase() === normalized);
    return found ?? categories[0];
  }
}
