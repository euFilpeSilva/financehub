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
    typedText: ''
  });

  private resolver: ((confirmed: boolean) => void) | null = null;

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
      typedText: ''
    });
    return new Promise<boolean>((resolve) => {
      this.resolver = resolve;
    });
  }

  updateTypedText(value: string): void {
    this.stateSource.update((state) => ({ ...state, typedText: value }));
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
    if (this.resolver) {
      this.resolver(confirmed);
      this.resolver = null;
    }
    this.stateSource.update((state) => ({
      ...state,
      open: false,
      requiredText: '',
      requiredTextLabel: '',
      typedText: ''
    }));
  }
}
