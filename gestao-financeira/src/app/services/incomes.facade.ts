import { computed, Injectable, signal } from '@angular/core';
import { AppPreferences, IncomeEntry } from '../models/finance.models';
import { getCurrentMonth, matchesMonthAndRange } from './finance-period.utils';
import { safeIncomeCategories } from './finance-preferences.utils';

@Injectable({ providedIn: 'root' })
export class IncomesFacade {
  private readonly incomesSource = signal<IncomeEntry[]>([]);
  private readonly selectedMonthSource = signal(getCurrentMonth());
  private readonly rangeStartSource = signal('');
  private readonly rangeEndSource = signal('');
  private readonly appPreferencesSource = signal<Partial<AppPreferences>>({
    incomeCategories: safeIncomeCategories({})
  });

  readonly filteredIncomes = computed(() =>
    this.incomesSource()
      .filter((income) =>
        matchesMonthAndRange(
          income.receivedAt,
          this.selectedMonthSource(),
          this.rangeStartSource(),
          this.rangeEndSource()
        )
      )
      .sort((a, b) => a.receivedAt.localeCompare(b.receivedAt))
  );

  readonly allIncomes = computed(() =>
    this.incomesSource().slice().sort((a, b) => a.receivedAt.localeCompare(b.receivedAt))
  );

  readonly availableIncomeCategories = computed(() =>
    Array.from(
      new Set([
        ...safeIncomeCategories(this.appPreferencesSource()),
        ...this.incomesSource().map((item) => item.category)
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

  setIncomes(items: IncomeEntry[]): void {
    this.incomesSource.set(items);
  }

  addIncome(item: IncomeEntry): void {
    this.incomesSource.update((list) => [...list, item]);
  }

  updateIncome(item: IncomeEntry): void {
    this.incomesSource.update((list) => list.map((current) => (current.id === item.id ? item : current)));
  }

  removeIncome(id: string): void {
    this.incomesSource.update((list) => list.filter((item) => item.id !== id));
  }

  snapshot(): IncomeEntry[] {
    return this.incomesSource();
  }
}
