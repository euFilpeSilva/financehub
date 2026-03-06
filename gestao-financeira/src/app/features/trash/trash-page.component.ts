import { CommonModule, DatePipe } from '@angular/common';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { LucideAngularModule, RotateCcw, Trash2 } from 'lucide-angular';
import { startWith } from 'rxjs/operators';
import { getEntityLabel } from '../../core/constants/finance.constants';
import { TrashItem } from '../../models/finance.models';
import { FinanceFacade } from '../../services/finance.facade';
import { TrashListFilters } from '../../services/finance.gateway';
import { ConfirmDialogService } from '../../shared/confirm-dialog/confirm-dialog.service';
import { FilterPanelComponent } from '../../shared/filter-panel/filter-panel.component';

@Component({
  selector: 'app-trash-page',
  imports: [CommonModule, DatePipe, ReactiveFormsModule, LucideAngularModule, FilterPanelComponent],
  templateUrl: './trash-page.component.html'
})
export class TrashPageComponent {
  protected readonly RotateCcw = RotateCcw;
  protected readonly Trash2 = Trash2;
  protected readonly viewMode = signal<'list' | 'grid'>('list');
  private readonly fb = inject(FormBuilder);
  private readonly confirmDialog = inject(ConfirmDialogService);

  protected readonly filterForm = this.fb.nonNullable.group({
    query: [''],
    entityType: ['ALL'],
    startDate: [''],
    endDate: ['']
  });
  protected readonly sortBy = signal<'deletedAt' | 'label' | 'entityType' | 'purgeAt'>('deletedAt');
  protected readonly sortDirection = signal<'asc' | 'desc'>('desc');
  private readonly filterValues = toSignal(
    this.filterForm.valueChanges.pipe(startWith(this.filterForm.getRawValue())),
    { initialValue: this.filterForm.getRawValue() }
  );
  private readonly backendFilteredTrashItems = signal<TrashItem[]>([]);

  protected readonly filteredTrashItems = computed(() => {
    const sortBy = this.sortBy();
    const sortDirection = this.sortDirection();

    const direction = sortDirection === 'asc' ? 1 : -1;
    return this.backendFilteredTrashItems().slice().sort((first, second) => {
      const compare = this.compareTrashItem(first, second, sortBy);
      return compare * direction;
    });
  });

  constructor(protected readonly facade: FinanceFacade) {
    effect(() => {
      this.filterValues();
      this.fetchTrashItemsFromBackend();
    });

    effect(() => {
      this.facade.trashItems();
      this.fetchTrashItemsFromBackend();
    });
  }

  protected async restore(item: TrashItem): Promise<void> {
    const confirmed = await this.confirmDialog.confirm({
      title: 'Restaurar item',
      message: `Deseja restaurar "${item.label}" da lixeira?`,
      confirmLabel: 'Restaurar'
    });
    if (!confirmed) {
      return;
    }
    this.facade.restoreTrashItem(item.id);
  }

  protected async purge(item: TrashItem): Promise<void> {
    const confirmed = await this.confirmDialog.confirm({
      title: 'Excluir permanentemente',
      message: `Deseja excluir permanentemente "${item.label}"?`,
      confirmLabel: 'Excluir',
      tone: 'danger'
    });
    if (!confirmed) {
      return;
    }
    this.facade.purgeTrashItem(item.id);
  }

  protected entityLabel(entity: TrashItem['entityType']): string {
    return getEntityLabel(entity);
  }

  protected timeUntilPurge(purgeAt: string): string {
    const diffMs = new Date(purgeAt).getTime() - Date.now();
    if (diffMs <= 0) {
      return 'A qualquer momento';
    }
    const days = Math.floor(diffMs / (24 * 60 * 60 * 1000));
    const hours = Math.floor((diffMs % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000));
    if (days > 0) {
      return `${days}d ${hours}h`;
    }
    return `${hours}h`;
  }

  protected clearFilters(): void {
    this.filterForm.patchValue({
      query: '',
      entityType: 'ALL',
      startDate: '',
      endDate: ''
    });
    this.sortBy.set('deletedAt');
    this.sortDirection.set('desc');
  }

  protected setSort(column: 'deletedAt' | 'label' | 'entityType' | 'purgeAt'): void {
    if (this.sortBy() === column) {
      this.sortDirection.set(this.sortDirection() === 'asc' ? 'desc' : 'asc');
      return;
    }
    this.sortBy.set(column);
    this.sortDirection.set('asc');
  }

  protected sortLabel(column: 'deletedAt' | 'label' | 'entityType' | 'purgeAt'): string {
    if (this.sortBy() !== column) {
      return '';
    }
    return this.sortDirection() === 'asc' ? '▲' : '▼';
  }

  private compareTrashItem(first: TrashItem, second: TrashItem, sortBy: string): number {
    if (sortBy === 'label') {
      return first.label.localeCompare(second.label);
    }
    if (sortBy === 'entityType') {
      return first.entityType.localeCompare(second.entityType);
    }
    if (sortBy === 'purgeAt') {
      return first.purgeAt.localeCompare(second.purgeAt);
    }
    return first.deletedAt.localeCompare(second.deletedAt);
  }

  private fetchTrashItemsFromBackend(): void {
    const filters = this.toTrashFilters();
    this.facade.listTrashItemsFiltered(filters).subscribe({
      next: (items) => this.backendFilteredTrashItems.set(items),
      error: () => this.backendFilteredTrashItems.set([])
    });
  }

  private toTrashFilters(): TrashListFilters {
    const filters = this.filterValues();
    return {
      query: (filters.query ?? '').trim(),
      entityType: filters.entityType ?? 'ALL',
      startDate: filters.startDate ?? '',
      endDate: filters.endDate ?? ''
    };
  }
}
