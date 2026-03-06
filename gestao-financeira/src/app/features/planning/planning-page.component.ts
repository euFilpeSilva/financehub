import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Goal, LucideAngularModule, Pencil, Plus, Trash2, X } from 'lucide-angular';
import { startWith } from 'rxjs/operators';
import { PlanningGoal } from '../../models/finance.models';
import { FinanceFacade } from '../../services/finance.facade';
import { PlanningGoalListFilters } from '../../services/finance.gateway';
import { ConfirmDialogService } from '../../shared/confirm-dialog/confirm-dialog.service';
import { CurrencyMaskDirective } from '../../shared/directives/currency-mask.directive';
import { FilterPanelComponent } from '../../shared/filter-panel/filter-panel.component';

@Component({
  selector: 'app-planning-page',
  imports: [CommonModule, ReactiveFormsModule, CurrencyPipe, DatePipe, LucideAngularModule, CurrencyMaskDirective, FilterPanelComponent],
  templateUrl: './planning-page.component.html'
})
export class PlanningPageComponent {
  protected readonly Goal = Goal;
  protected readonly Pencil = Pencil;
  protected readonly Plus = Plus;
  protected readonly Trash2 = Trash2;
  protected readonly X = X;
  protected readonly viewMode = signal<'list' | 'grid'>('list');

  private readonly fb = inject(FormBuilder);
  private readonly confirmDialog = inject(ConfirmDialogService);

  protected readonly form = this.fb.nonNullable.group({
    title: ['', [Validators.required, Validators.maxLength(120)]],
    targetAmount: [0, [Validators.required, Validators.min(0.01)]],
    currentAmount: [0, [Validators.required, Validators.min(0)]],
    targetDate: [this.nextQuarter(), Validators.required],
    notes: ['']
  });
  protected readonly filterForm = this.fb.nonNullable.group({
    query: [''],
    status: ['ALL'],
    startDate: [''],
    endDate: ['']
  });
  protected readonly sortBy = signal<'targetDate' | 'title' | 'targetAmount' | 'currentAmount' | 'status'>('targetDate');
  protected readonly sortDirection = signal<'asc' | 'desc'>('asc');
  private readonly filterValues = toSignal(
    this.filterForm.valueChanges.pipe(startWith(this.filterForm.getRawValue())),
    { initialValue: this.filterForm.getRawValue() }
  );
  private readonly backendFilteredGoals = signal<PlanningGoal[]>([]);
  protected editingGoalId: string | null = null;
  protected readonly filteredGoals = computed(() => {
    const sortBy = this.sortBy();
    const sortDirection = this.sortDirection();

    const direction = sortDirection === 'asc' ? 1 : -1;
    return this.backendFilteredGoals().slice().sort((first, second) => {
      const compare = this.compareGoal(first, second, sortBy);
      return compare * direction;
    });
  });

  constructor(protected readonly facade: FinanceFacade) {
    effect(() => {
      this.filterValues();
      this.fetchGoalsFromBackend();
    });

    effect(() => {
      this.facade.allGoals();
      this.fetchGoalsFromBackend();
    });
  }

  protected async submit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    if (this.editingGoalId) {
      const confirmed = await this.confirmDialog.confirm({
        title: 'Confirmar edicao',
        message: 'Deseja salvar as alteracoes desta meta?',
        confirmLabel: 'Salvar'
      });
      if (!confirmed) {
        return;
      }
      const current = this.facade.allGoals().find((item) => item.id === this.editingGoalId);
      if (!current) {
        return;
      }
      const currentAmount = this.toAmount(value.currentAmount);
      this.facade.updateGoal({
        ...current,
        title: value.title.trim(),
        targetAmount: this.toAmount(value.targetAmount),
        currentAmount,
        targetDate: value.targetDate,
        notes: value.notes.trim(),
        complete: current.complete
      });
    } else {
      const confirmed = await this.confirmDialog.confirm({
        title: 'Confirmar inclusao',
        message: 'Deseja criar esta nova meta?',
        confirmLabel: 'Criar'
      });
      if (!confirmed) {
        return;
      }
      this.facade.addGoal({
        title: value.title.trim(),
        targetAmount: this.toAmount(value.targetAmount),
        currentAmount: this.toAmount(value.currentAmount),
        targetDate: value.targetDate,
        notes: value.notes.trim(),
        complete: false
      });
    }

    this.resetForm();
  }

  protected updateGoalProgress(goal: PlanningGoal, event: Event): void {
    const input = event.target as HTMLInputElement | null;
    if (!input) {
      return;
    }
    const value = this.parseCurrency(input.value);
    this.facade.updateGoalProgress(goal, value);
  }

  protected startEdit(goal: PlanningGoal): void {
    this.editingGoalId = goal.id;
    this.form.patchValue({
      title: goal.title,
      targetAmount: goal.targetAmount,
      currentAmount: goal.currentAmount,
      targetDate: goal.targetDate,
      notes: goal.notes
    });
  }

  protected cancelEdit(): void {
    this.resetForm();
  }

  protected async removeGoal(goal: PlanningGoal): Promise<void> {
    const confirmed = await this.confirmDialog.confirm({
      title: 'Excluir meta',
      message: `Deseja excluir "${goal.title}"?`,
      confirmLabel: 'Excluir',
      tone: 'danger'
    });
    if (!confirmed) {
      return;
    }
    if (this.editingGoalId === goal.id) {
      this.resetForm();
    }
    this.facade.deleteGoal(goal.id);
  }

  private nextQuarter(): string {
    const base = new Date();
    base.setMonth(base.getMonth() + 3);
    return base.toISOString().slice(0, 10);
  }

  private toAmount(value: number): number {
    return Math.max(0, Number(value.toFixed(2)));
  }

  private parseCurrency(value: string): number {
    const digits = value.replace(/\D/g, '');
    if (!digits) {
      return 0;
    }
    return Number(digits) / 100;
  }

  private resetForm(): void {
    this.editingGoalId = null;
    this.form.patchValue({
      title: '',
      targetAmount: 0,
      currentAmount: 0,
      targetDate: this.nextQuarter(),
      notes: ''
    });
  }

  protected clearFilters(): void {
    this.filterForm.patchValue({
      query: '',
      status: 'ALL',
      startDate: '',
      endDate: ''
    });
    this.sortBy.set('targetDate');
    this.sortDirection.set('asc');
  }

  protected setSort(column: 'targetDate' | 'title' | 'targetAmount' | 'currentAmount' | 'status'): void {
    if (this.sortBy() === column) {
      this.sortDirection.set(this.sortDirection() === 'asc' ? 'desc' : 'asc');
      return;
    }
    this.sortBy.set(column);
    this.sortDirection.set('asc');
  }

  protected sortLabel(column: 'targetDate' | 'title' | 'targetAmount' | 'currentAmount' | 'status'): string {
    if (this.sortBy() !== column) {
      return '';
    }
    return this.sortDirection() === 'asc' ? '▲' : '▼';
  }

  private compareGoal(first: PlanningGoal, second: PlanningGoal, sortBy: string): number {
    if (sortBy === 'title') {
      return first.title.localeCompare(second.title);
    }
    if (sortBy === 'targetAmount') {
      return first.targetAmount - second.targetAmount;
    }
    if (sortBy === 'currentAmount') {
      return first.currentAmount - second.currentAmount;
    }
    if (sortBy === 'status') {
      return Number(first.complete) - Number(second.complete);
    }
    return first.targetDate.localeCompare(second.targetDate);
  }

  private fetchGoalsFromBackend(): void {
    const filters = this.toPlanningFilters();
    this.facade.listGoalsFiltered(filters).subscribe({
      next: (items) => this.backendFilteredGoals.set(items),
      error: () => this.backendFilteredGoals.set([])
    });
  }

  private toPlanningFilters(): PlanningGoalListFilters {
    const filters = this.filterValues();
    return {
      query: (filters.query ?? '').trim(),
      status: filters.status ?? 'ALL',
      startDate: filters.startDate ?? '',
      endDate: filters.endDate ?? ''
    };
  }
}

