import { Injectable, signal } from '@angular/core';

export interface ConfirmDialogOptions {
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  tone?: 'danger' | 'default';
  requiredText?: string;
  requiredTextLabel?: string;
}

export interface ConfirmDialogWithCheckboxOptions extends ConfirmDialogOptions {
  checkboxLabel: string;
  checkboxDefaultChecked?: boolean;
}

export interface ConfirmDialogCheckboxResult {
  confirmed: boolean;
  checkboxChecked: boolean;
}

interface ConfirmDialogState {
  open: boolean;
  title: string;
  message: string;
  confirmLabel: string;
  cancelLabel: string;
  tone: 'danger' | 'default';
  requiredText: string;
  requiredTextLabel: string;
  typedText: string;
  checkboxLabel: string;
  checkboxChecked: boolean;
}

@Injectable({ providedIn: 'root' })
export class ConfirmDialogService {
  private readonly stateSource = signal<ConfirmDialogState>({
    open: false,
    title: '',
    message: '',
    confirmLabel: 'Confirmar',
    cancelLabel: 'Cancelar',
    tone: 'default',
    requiredText: '',
    requiredTextLabel: '',
    typedText: '',
    checkboxLabel: '',
    checkboxChecked: false
  });

  private resolver: ((confirmed: boolean) => void) | null = null;
  private checkboxResolver: ((result: ConfirmDialogCheckboxResult) => void) | null = null;

  readonly state = this.stateSource.asReadonly();

  confirm(options: ConfirmDialogOptions): Promise<boolean> {
    this.stateSource.set({
      open: true,
      title: options.title,
      message: options.message,
      confirmLabel: options.confirmLabel ?? 'Confirmar',
      cancelLabel: options.cancelLabel ?? 'Cancelar',
      tone: options.tone ?? 'default',
      requiredText: options.requiredText?.trim() ?? '',
      requiredTextLabel: options.requiredTextLabel?.trim() ?? '',
      typedText: '',
      checkboxLabel: '',
      checkboxChecked: false
    });
    return new Promise<boolean>((resolve) => {
      this.resolver = resolve;
      this.checkboxResolver = null;
    });
  }

  confirmWithCheckbox(options: ConfirmDialogWithCheckboxOptions): Promise<ConfirmDialogCheckboxResult> {
    this.stateSource.set({
      open: true,
      title: options.title,
      message: options.message,
      confirmLabel: options.confirmLabel ?? 'Confirmar',
      cancelLabel: options.cancelLabel ?? 'Cancelar',
      tone: options.tone ?? 'default',
      requiredText: options.requiredText?.trim() ?? '',
      requiredTextLabel: options.requiredTextLabel?.trim() ?? '',
      typedText: '',
      checkboxLabel: options.checkboxLabel.trim(),
      checkboxChecked: Boolean(options.checkboxDefaultChecked)
    });
    return new Promise<ConfirmDialogCheckboxResult>((resolve) => {
      this.checkboxResolver = resolve;
      this.resolver = null;
    });
  }

  updateTypedText(value: string): void {
    this.stateSource.update((state) => ({ ...state, typedText: value }));
  }

  updateCheckbox(value: boolean): void {
    this.stateSource.update((state) => ({ ...state, checkboxChecked: value }));
  }

  canConfirm(): boolean {
    const state = this.stateSource();
    if (!state.requiredText) {
      return true;
    }
    return state.typedText.trim().toUpperCase() === state.requiredText.toUpperCase();
  }

  resolve(confirmed: boolean): void {
    if (confirmed && !this.canConfirm()) {
      return;
    }
    const checkboxChecked = this.stateSource().checkboxChecked;

    if (this.resolver) {
      this.resolver(confirmed);
      this.resolver = null;
    }
    if (this.checkboxResolver) {
      this.checkboxResolver({ confirmed, checkboxChecked });
      this.checkboxResolver = null;
    }

    this.stateSource.update((state) => ({
      ...state,
      open: false,
      requiredText: '',
      requiredTextLabel: '',
      typedText: '',
      checkboxLabel: '',
      checkboxChecked: false
    }));
  }
}
