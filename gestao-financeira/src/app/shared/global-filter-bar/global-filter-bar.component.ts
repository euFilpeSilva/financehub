import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FilterX, LucideAngularModule } from 'lucide-angular';
import { FinanceFacade } from '../../services/finance.facade';

@Component({
  selector: 'app-global-filter-bar',
  imports: [CommonModule, FormsModule, LucideAngularModule],
  templateUrl: './global-filter-bar.component.html'
})
export class GlobalFilterBarComponent {
  protected readonly FilterX = FilterX;

  constructor(private readonly facade: FinanceFacade) {}

  protected get selectedMonth(): string {
    return this.facade.selectedMonth();
  }

  protected set selectedMonth(value: string) {
    this.facade.setMonth(value);
  }

  protected get filterStart(): string {
    return this.facade.rangeStart();
  }

  protected set filterStart(value: string) {
    this.facade.setDateRange(value, this.facade.rangeEnd());
  }

  protected get filterEnd(): string {
    return this.facade.rangeEnd();
  }

  protected set filterEnd(value: string) {
    this.facade.setDateRange(this.facade.rangeStart(), value);
  }

  protected clearFilter(): void {
    this.facade.clearDateRange();
  }

  protected applyCurrentMonth(): void {
    this.facade.setMonth(new Date().toISOString().slice(0, 7));
  }
}

