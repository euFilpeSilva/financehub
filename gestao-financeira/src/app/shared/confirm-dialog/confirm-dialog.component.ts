import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AlertTriangle, LucideAngularModule } from 'lucide-angular';
import { ConfirmDialogService } from './confirm-dialog.service';

@Component({
  selector: 'app-confirm-dialog',
  imports: [CommonModule, FormsModule, LucideAngularModule],
  templateUrl: './confirm-dialog.component.html'
})
export class ConfirmDialogComponent {
  protected readonly AlertTriangle = AlertTriangle;
  protected readonly dialog = inject(ConfirmDialogService);
}
