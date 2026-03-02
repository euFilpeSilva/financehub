import { computed, Injectable, signal } from '@angular/core';
import { BankAccount } from '../models/finance.models';

@Injectable({ providedIn: 'root' })
export class BankAccountsFacade {
  private readonly bankAccountsSource = signal<BankAccount[]>([]);

  readonly bankAccounts = computed(() =>
    this.bankAccountsSource().slice().sort((a, b) => a.label.localeCompare(b.label))
  );

  setBankAccounts(items: BankAccount[]): void {
    this.bankAccountsSource.set(items);
  }

  addBankAccount(item: BankAccount): void {
    this.bankAccountsSource.update((list) => [...list, item]);
  }

  updateBankAccount(item: BankAccount): void {
    this.bankAccountsSource.update((list) => list.map((current) => (current.id === item.id ? item : current)));
  }

  removeBankAccount(id: string): void {
    this.bankAccountsSource.update((list) => list.filter((item) => item.id !== id));
  }
}
