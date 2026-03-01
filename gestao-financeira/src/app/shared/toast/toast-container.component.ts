import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { CheckCircle2, CircleHelp, LucideAngularModule, TriangleAlert, X } from 'lucide-angular';
import { ToastService } from './toast.service';

@Component({
  selector: 'app-toast-container',
  imports: [CommonModule, LucideAngularModule],
  templateUrl: './toast-container.component.html'
})
export class ToastContainerComponent {
  protected readonly CheckCircle2 = CheckCircle2;
  protected readonly CircleHelp = CircleHelp;
  protected readonly TriangleAlert = TriangleAlert;
  protected readonly X = X;

  protected readonly toast = inject(ToastService);
}
