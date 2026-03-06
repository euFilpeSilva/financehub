import { CommonModule } from '@angular/common';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AlertTriangle, CheckCircle2, CloudUpload, Landmark, LucideAngularModule, Moon, Sun, X } from 'lucide-angular';
import { NAV_GROUPS } from './core/constants/finance.constants';
import { ThemeService } from './core/theme.service';
import { FinanceFacade } from './services/finance.facade';
import { ConfirmDialogComponent } from './shared/confirm-dialog/confirm-dialog.component';
import { GlobalFilterBarComponent } from './shared/global-filter-bar/global-filter-bar.component';
import { ToastContainerComponent } from './shared/toast/toast-container.component';
import { ToastService } from './shared/toast/toast.service';

@Component({
  selector: 'app-root',
  imports: [
    CommonModule,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    LucideAngularModule,
    GlobalFilterBarComponent,
    ConfirmDialogComponent,
    ToastContainerComponent
  ],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  protected readonly CloudUpload = CloudUpload;
  protected readonly CheckCircle2 = CheckCircle2;
  protected readonly AlertTriangle = AlertTriangle;
  protected readonly X = X;
  protected readonly Landmark = Landmark;
  protected readonly Moon = Moon;
  protected readonly Sun = Sun;
  protected readonly navGroups = NAV_GROUPS;
  protected readonly facade = inject(FinanceFacade);
  protected readonly importWidgetExpanded = signal(false);
  protected readonly importProgress = this.facade.ofxImportBatchProgress;
  protected readonly importPhaseLabel = computed(() => {
    const phase = this.importProgress().currentFilePhase;
    if (phase === 'uploading') {
      return 'Enviando arquivo';
    }
    if (phase === 'processing') {
      return 'Processando importacao';
    }
    return 'Concluido';
  });

  constructor(
    protected readonly theme: ThemeService,
    private readonly router: Router,
    private readonly toast: ToastService
  ) {
    effect(() => {
      const notice = this.facade.operationNotice();
      if (!notice) {
        return;
      }
      if (notice.type === 'success') {
        this.toast.success(notice.message);
      } else {
        this.toast.error(notice.message);
      }
      this.facade.clearOperationNotice();
    });
  }

  protected isDashboardRoute(): boolean {
    const path = this.router.url.split('?')[0];
    return path === '/' || path === '/dashboard';
  }

  protected onImportWidgetMouseEnter(): void {
    this.importWidgetExpanded.set(true);
  }

  protected onImportWidgetMouseLeave(): void {
    if (this.importProgress().running) {
      return;
    }
    this.importWidgetExpanded.set(false);
  }

  protected toggleImportWidget(): void {
    this.importWidgetExpanded.update((expanded) => !expanded);
  }

  protected closeImportWidget(event: Event): void {
    event.stopPropagation();
    this.importWidgetExpanded.set(false);
    this.facade.dismissOfxImportWidget();
  }
}
