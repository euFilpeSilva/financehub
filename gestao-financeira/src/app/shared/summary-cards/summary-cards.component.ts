import { CommonModule, CurrencyPipe, PercentPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import {
  ArrowDownCircle,
  ArrowUpCircle,
  CircleHelp,
  Goal,
  LucideAngularModule,
  WalletCards
} from 'lucide-angular';
import { FinanceFacade } from '../../services/finance.facade';
import { ToastService } from '../toast/toast.service';

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

  private readonly toast = inject(ToastService);

  constructor(protected readonly facade: FinanceFacade) {}

  protected get goalCompletionRatio(): number {
    return this.facade.goalsProgress() / 100;
  }

  protected explainCard(card: 'incomes' | 'expenses' | 'balance' | 'planning'): void {
    const messages: Record<'incomes' | 'expenses' | 'balance' | 'planning', string> = {
      incomes: 'Entradas: soma de todas as receitas do periodo selecionado.',
      expenses: 'Contas: soma das contas pagas e pendentes no periodo selecionado.',
      balance: 'Saldo: resultado de entradas menos contas no periodo selecionado.',
      planning: 'Planejamento: media do progresso percentual das metas cadastradas.'
    };
    this.toast.info(messages[card]);
  }
}

