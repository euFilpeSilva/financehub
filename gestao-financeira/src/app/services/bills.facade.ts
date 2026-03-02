import { computed, Injectable, signal } from '@angular/core';
import { AppPreferences, BillRecord } from '../models/finance.models';
import { createSafeDate, getCurrentMonth, matchesMonthAndRange } from './finance-period.utils';
import { safeBillCategories } from './finance-preferences.utils';

@Injectable({ providedIn: 'root' })
export class BillsFacade {
  private readonly billsSource = signal<BillRecord[]>([]);
  private readonly selectedMonthSource = signal(getCurrentMonth());
  private readonly rangeStartSource = signal('');
  private readonly rangeEndSource = signal('');
  private readonly appPreferencesSource = signal<Partial<AppPreferences>>({
    billCategories: safeBillCategories({})
  });

  readonly filteredBills = computed(() =>
    this.billsSource()
      .filter((bill) =>
        matchesMonthAndRange(
          bill.dueDate,
          this.selectedMonthSource(),
          this.rangeStartSource(),
          this.rangeEndSource()
        )
      )
      .sort((a, b) => a.dueDate.localeCompare(b.dueDate))
  );

  readonly allBills = computed(() =>
    this.billsSource().slice().sort((a, b) => a.dueDate.localeCompare(b.dueDate))
  );

  readonly availableExpenseCategories = computed(() =>
    Array.from(
      new Set([
        ...safeBillCategories(this.appPreferencesSource()),
        ...this.billsSource().map((item) => item.category)
      ])
    ).sort((a, b) => a.localeCompare(b))
  );

  setPeriod(month: string, start: string, end: string): void {
    this.selectedMonthSource.set(month);
    this.rangeStartSource.set(start);
    this.rangeEndSource.set(end);
  }

  setAppPreferences(preferences: AppPreferences): void {
    this.appPreferencesSource.set(preferences);
  }

  setBills(items: BillRecord[]): void {
    this.billsSource.set(items);
  }

  addBill(item: BillRecord): void {
    this.billsSource.update((list) => [...list, item]);
  }

  updateBill(item: BillRecord): void {
    this.billsSource.update((list) => list.map((current) => (current.id === item.id ? item : current)));
  }

  removeBill(id: string): void {
    this.billsSource.update((list) => list.filter((item) => item.id !== id));
  }

  addBills(items: BillRecord[]): void {
    this.billsSource.update((list) => [...list, ...items]);
  }

  snapshot(): BillRecord[] {
    return this.billsSource();
  }

  buildRecurringBillPayloads(month: string): Omit<BillRecord, 'id'>[] {
    const recurringTemplates = this.billsSource().filter((item) => item.recurring);

    return recurringTemplates
      .filter((template) => {
        const alreadyExists = this.billsSource().some(
          (item) =>
            item.description.toLowerCase() === template.description.toLowerCase() &&
            item.category === template.category &&
            item.dueDate.startsWith(month)
        );
        return !alreadyExists;
      })
      .map((template) => {
        const templateDay = Number(template.dueDate.slice(8, 10));
        return {
          description: template.description,
          category: template.category,
          amount: template.amount,
          dueDate: createSafeDate(month, templateDay),
          recurring: true,
          paid: false,
          internalTransfer: false
        };
      });
  }
}
