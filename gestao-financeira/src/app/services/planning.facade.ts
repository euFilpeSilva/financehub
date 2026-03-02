import { computed, Injectable, signal } from '@angular/core';
import { PlanningGoal, SpendingGoal, SpendingGoalStatus } from '../models/finance.models';

@Injectable({ providedIn: 'root' })
export class PlanningFacade {
  private readonly goalsSource = signal<PlanningGoal[]>([]);
  private readonly spendingGoalsSource = signal<SpendingGoal[]>([]);
  private readonly spendingGoalStatusesSource = signal<SpendingGoalStatus[]>([]);

  readonly allGoals = computed(() => this.goalsSource().slice().sort((a, b) => a.targetDate.localeCompare(b.targetDate)));

  readonly spendingGoals = computed(() =>
    this.spendingGoalsSource().slice().sort((a, b) => a.title.localeCompare(b.title))
  );

  readonly goalsProgress = computed(() => {
    const list = this.allGoals();
    if (!list.length) {
      return 0;
    }
    const progressSum = list.reduce((sum, goal) => {
      if (!goal.targetAmount) {
        return sum;
      }
      return sum + Math.min(100, (goal.currentAmount / goal.targetAmount) * 100);
    }, 0);
    return Math.round(progressSum / list.length);
  });

  readonly spendingGoalStatuses = computed<SpendingGoalStatus[]>(() =>
    this.spendingGoalStatusesSource().slice().sort((a, b) => a.goal.title.localeCompare(b.goal.title))
  );

  setGoals(items: PlanningGoal[]): void {
    this.goalsSource.set(items);
  }

  addGoal(item: PlanningGoal): void {
    this.goalsSource.update((list) => [...list, item]);
  }

  updateGoal(item: PlanningGoal): void {
    this.goalsSource.update((list) => list.map((current) => (current.id === item.id ? item : current)));
  }

  removeGoal(id: string): void {
    this.goalsSource.update((list) => list.filter((item) => item.id !== id));
  }

  setSpendingGoals(items: SpendingGoal[]): void {
    this.spendingGoalsSource.set(items);
  }

  addSpendingGoal(item: SpendingGoal): void {
    this.spendingGoalsSource.update((list) => [...list, item]);
  }

  updateSpendingGoal(item: SpendingGoal): void {
    this.spendingGoalsSource.update((list) => list.map((current) => (current.id === item.id ? item : current)));
  }

  removeSpendingGoal(id: string): void {
    this.spendingGoalsSource.update((list) => list.filter((item) => item.id !== id));
  }

  setSpendingGoalStatuses(items: SpendingGoalStatus[]): void {
    this.spendingGoalStatusesSource.set(items);
  }
}
