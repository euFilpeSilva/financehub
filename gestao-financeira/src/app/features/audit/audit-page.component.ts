import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { History, LucideAngularModule } from 'lucide-angular';
import { startWith } from 'rxjs/operators';
import { getEntityLabel } from '../../core/constants/finance.constants';
import { AuditEvent } from '../../models/finance.models';
import { FinanceFacade } from '../../services/finance.facade';
import { AuditEventListFilters } from '../../services/finance.gateway';
import { CurrencyMaskDirective } from '../../shared/directives/currency-mask.directive';
import { FilterPanelComponent } from '../../shared/filter-panel/filter-panel.component';

@Component({
  selector: 'app-audit-page',
  imports: [CommonModule, ReactiveFormsModule, DatePipe, CurrencyPipe, LucideAngularModule, CurrencyMaskDirective, FilterPanelComponent],
  templateUrl: './audit-page.component.html'
})
export class AuditPageComponent {
  protected readonly History = History;
  protected readonly viewMode = signal<'list' | 'grid'>('list');

  private readonly fb = inject(FormBuilder);

  protected readonly filterForm = this.fb.nonNullable.group({
    startDate: [''],
    endDate: [''],
    entityType: ['ALL'],
    action: ['ALL'],
    transactionBankAccountId: ['ALL'],
    statementImportBankAccountId: ['ALL'],
    name: [''],
    minValue: [null as number | null],
    maxValue: [null as number | null]
  });
  protected readonly sortBy = signal<'timestamp' | 'entityType' | 'entityName' | 'amount' | 'action'>('timestamp');
  protected readonly sortDirection = signal<'asc' | 'desc'>('desc');
  private readonly filterValues = toSignal(
    this.filterForm.valueChanges.pipe(startWith(this.filterForm.getRawValue())),
    { initialValue: this.filterForm.getRawValue() }
  );
  private readonly backendFilteredEvents = signal<AuditEvent[]>([]);

  protected readonly filteredEvents = computed(() => {
    const values = this.filterValues();
    const sortBy = this.sortBy();
    const sortDirection = this.sortDirection();

    const direction = sortDirection === 'asc' ? 1 : -1;
    return this.backendFilteredEvents().slice().sort((first, second) => {
      const compare = this.compareAuditEvent(first, second, sortBy);
      return compare * direction;
    });
  });

  constructor(protected readonly facade: FinanceFacade) {
    effect(() => {
      this.filterValues();
      this.fetchAuditEventsFromBackend();
    });

    effect(() => {
      this.facade.auditEvents();
      this.fetchAuditEventsFromBackend();
    });
  }

  protected applyFilters(): void {
    this.filterForm.updateValueAndValidity();
  }

  protected clearFilters(): void {
    this.filterForm.patchValue({
      startDate: '',
      endDate: '',
      entityType: 'ALL',
      action: 'ALL',
      transactionBankAccountId: 'ALL',
      statementImportBankAccountId: 'ALL',
      name: '',
      minValue: null,
      maxValue: null
    });
    this.sortBy.set('timestamp');
    this.sortDirection.set('desc');
  }

  protected entityLabel(entity: AuditEvent['entityType']): string {
    return getEntityLabel(entity);
  }

  protected auditActionLabel(action: AuditEvent['action']): string {
    if (action === 'create') {
      return 'Criacao';
    }
    if (action === 'update') {
      return 'Atualizacao';
    }
    if (action === 'delete') {
      return 'Exclusao';
    }
    if (action === 'import') {
      return 'Importacao';
    }
    if (action === 'restore') {
      return 'Restauracao';
    }
    if (action === 'purge') {
      return 'Exclusao permanente';
    }
    return 'Mudanca de status';
  }

  private compareAuditEvent(first: AuditEvent, second: AuditEvent, sortBy: string): number {
    if (sortBy === 'entityType') {
      return first.entityType.localeCompare(second.entityType);
    }
    if (sortBy === 'entityName') {
      return (first.entityName || '').localeCompare(second.entityName || '');
    }
    if (sortBy === 'amount') {
      return (first.amount ?? Number.MIN_SAFE_INTEGER) - (second.amount ?? Number.MIN_SAFE_INTEGER);
    }
    if (sortBy === 'action') {
      return first.action.localeCompare(second.action);
    }
    return first.timestamp.localeCompare(second.timestamp);
  }

  protected setSort(column: 'timestamp' | 'entityType' | 'entityName' | 'amount' | 'action'): void {
    if (this.sortBy() === column) {
      this.sortDirection.set(this.sortDirection() === 'asc' ? 'desc' : 'asc');
      return;
    }
    this.sortBy.set(column);
    this.sortDirection.set('asc');
  }

  protected sortLabel(column: 'timestamp' | 'entityType' | 'entityName' | 'amount' | 'action'): string {
    if (this.sortBy() !== column) {
      return '';
    }
    return this.sortDirection() === 'asc' ? '▲' : '▼';
  }

  private fetchAuditEventsFromBackend(): void {
    const filters = this.toAuditFilters();
    this.facade.listAuditEventsFiltered(filters).subscribe({
      next: (items) => this.backendFilteredEvents.set(items),
      error: () => this.backendFilteredEvents.set([])
    });
  }

  private toAuditFilters(): AuditEventListFilters {
    const values = this.filterValues();
    return {
      startDate: values.startDate || '',
      endDate: values.endDate || '',
      entityType: values.entityType || 'ALL',
      action: values.action || 'ALL',
      transactionBankAccountId: values.transactionBankAccountId || 'ALL',
      statementImportBankAccountId: values.statementImportBankAccountId || 'ALL',
      name: (values.name ?? '').trim(),
      minValue: values.minValue,
      maxValue: values.maxValue
    };
  }
}
