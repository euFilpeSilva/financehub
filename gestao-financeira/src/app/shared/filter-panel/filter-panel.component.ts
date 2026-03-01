import { CommonModule } from '@angular/common';
import { Component, input } from '@angular/core';

@Component({
  selector: 'app-filter-panel',
  imports: [CommonModule],
  templateUrl: './filter-panel.component.html'
})
export class FilterPanelComponent {
  readonly title = input('Filtros');
  readonly subtitle = input('');
}
