import { Injectable, signal } from '@angular/core';

export interface ToastMessage {
  id: number;
  type: 'success' | 'error' | 'info';
  text: string;
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly messagesSource = signal<ToastMessage[]>([]);
  private nextId = 0;

  readonly messages = this.messagesSource.asReadonly();

  success(text: string): void {
    this.push('success', text);
  }

  error(text: string): void {
    this.push('error', text);
  }

  info(text: string): void {
    this.push('info', text);
  }

  dismiss(id: number): void {
    this.messagesSource.update((list) => list.filter((item) => item.id !== id));
  }

  private push(type: ToastMessage['type'], text: string): void {
    this.nextId += 1;
    const id = this.nextId;
    this.messagesSource.update((list) => [...list, { id, type, text }]);
    setTimeout(() => this.dismiss(id), 4500);
  }
}
