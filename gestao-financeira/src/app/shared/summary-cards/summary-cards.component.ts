import { CommonModule, CurrencyPipe, PercentPipe } from '@angular/common';
import { Component } from '@angular/core';
import {
  ArrowDownCircle,
  ArrowUpCircle,
  CircleHelp,
  Goal,
  LucideAngularModule,
  WalletCards
} from 'lucide-angular';
import { FinanceFacade } from '../../services/finance.facade';

@Component({
  selector: 'app-summary-cards',
  imports: [CommonModule, CurrencyPipe, PercentPipe, LucideAngularModule],
  templateUrl: './summary-cards.component.html'
})
export class SummaryCardsComponent {
  protected readonly ArrowDownCircle = ArrowDownCircle;
  protected readonly ArrowUpCircle = ArrowUpCircle;
  protected readonly CircleHelp = CircleHelp;
  protected readonly Goal = Goal;
  protected readonly WalletCards = WalletCards;

  constructor(protected readonly facade: FinanceFacade) {}

  protected get goalCompletionRatio(): number {
    return this.facade.goalsProgress() / 100;
  }

}

