import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { History, LucideAngularModule } from 'lucide-angular';
import { startWith } from 'rxjs/operators';
import { getEntityLabel } from '../../core/constants/finance.constants';
import { AuditEvent } from '../../models/finance.models';
import { FinanceFacade } from '../../services/finance.facade';
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

  protected readonly filteredEvents = computed(() => {
    const values = this.filterValues();
    const start = values.startDate || '';
    const end = values.endDate || '';
    const entityType = values.entityType || 'ALL';
    const action = values.action || 'ALL';
    const transactionBankAccountId = values.transactionBankAccountId || 'ALL';
    const statementImportBankAccountId = values.statementImportBankAccountId || 'ALL';
    const nameQuery = (values.name ?? '').trim().toLowerCase();
    const minValue = values.minValue !== null ? Number(values.minValue) : null;
    const maxValue = values.maxValue !== null ? Number(values.maxValue) : null;
    const sortBy = this.sortBy();
    const sortDirection = this.sortDirection();

    const filtered = this.facade.auditEvents().filter((event) => {
      const eventDate = event.timestamp.slice(0, 10);
      if (start && eventDate < start) {
        return false;
      }
      if (end && eventDate > end) {
        return false;
      }

      if (entityType !== 'ALL' && event.entityType !== entityType) {
        return false;
      }
      if (action !== 'ALL' && event.action !== action) {
        return false;
      }

      if (transactionBankAccountId !== 'ALL') {
        const eventBankId = this.resolveTransactionBankAccountId(event);
        if (!eventBankId || eventBankId !== transactionBankAccountId) {
          return false;
        }
      }

      if (statementImportBankAccountId !== 'ALL') {
        if (event.entityType !== 'statement' || event.action !== 'import') {
          return false;
        }
        const importBankId = this.resolveStatementImportBankAccountId(event);
        if (!importBankId || importBankId !== statementImportBankAccountId) {
          return false;
        }
      }

      if (nameQuery) {
        const target = `${event.entityName ?? ''} ${event.message}`.toLowerCase();
        if (!target.includes(nameQuery)) {
          return false;
        }
      }

      if (minValue !== null || maxValue !== null) {
        if (event.amount === undefined) {
          return false;
        }
        if (minValue !== null && event.amount < minValue) {
          return false;
        }
        if (maxValue !== null && event.amount > maxValue) {
          return false;
        }
      }

      return true;
    });

    const direction = sortDirection === 'asc' ? 1 : -1;
    return filtered.slice().sort((first, second) => {
      const compare = this.compareAuditEvent(first, second, sortBy);
      return compare * direction;
    });
  });

  constructor(protected readonly facade: FinanceFacade) {}

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

  private resolveTransactionBankAccountId(event: AuditEvent): string | null {
    if (event.entityType === 'bill') {
      return this.facade.allBills().find((item) => item.id === event.entityId)?.bankAccountId ?? null;
    }
    if (event.entityType === 'income') {
      return this.facade.allIncomes().find((item) => item.id === event.entityId)?.bankAccountId ?? null;
    }
    return null;
  }

  private resolveStatementImportBankAccountId(event: AuditEvent): string | null {
    const accounts = this.facade.bankAccounts();

    const byEntityId = accounts.find((account) => account.id === event.entityId);
    if (byEntityId) {
      return byEntityId.id;
    }

    const digits = `${event.entityId} ${event.message}`.replace(/\D/g, '');
    for (const account of accounts) {
      const accountDigits = (account.accountId || '').replace(/\D/g, '');
      if (accountDigits.length >= 4 && digits.includes(accountDigits)) {
        return account.id;
      }
    }

    const normalized = this.normalizeText(`${event.entityId} ${event.message}`);
    for (const account of accounts) {
      const label = this.normalizeText(account.label || '');
      if (label && normalized.includes(label)) {
        return account.id;
      }
    }

    return null;
  }

  private normalizeText(value: string): string {
    if (!value) {
      return '';
    }
    return value
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toUpperCase()
      .replace(/[^A-Z0-9 ]/g, ' ')
      .replace(/\s{2,}/g, ' ')
      .trim();
  }
}
