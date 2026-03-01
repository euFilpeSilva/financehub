import { Directive, ElementRef, forwardRef, HostListener, Renderer2 } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

@Directive({
  selector: 'input[appCurrencyMask]',
  standalone: true,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => CurrencyMaskDirective),
      multi: true
    }
  ]
})
export class CurrencyMaskDirective implements ControlValueAccessor {
  private onChange: (value: number) => void = () => {};
  private onTouched: () => void = () => {};
  private isDisabled = false;

  constructor(
    private readonly elementRef: ElementRef<HTMLInputElement>,
    private readonly renderer: Renderer2
  ) {}

  writeValue(value: number | null): void {
    const safeValue = typeof value === 'number' && Number.isFinite(value) ? value : 0;
    this.setDisplayValue(safeValue);
  }

  registerOnChange(fn: (value: number) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.isDisabled = isDisabled;
    this.renderer.setProperty(this.elementRef.nativeElement, 'disabled', isDisabled);
  }

  @HostListener('input', ['$event.target.value'])
  handleInput(raw: string): void {
    if (this.isDisabled) {
      return;
    }
    const numeric = this.parseCurrencyInput(raw);
    this.setDisplayValue(numeric);
    this.onChange(numeric);
  }

  @HostListener('blur')
  handleBlur(): void {
    this.onTouched();
  }

  private parseCurrencyInput(value: string): number {
    const digits = value.replace(/\D/g, '');
    if (!digits) {
      return 0;
    }
    return Number(digits) / 100;
  }

  private setDisplayValue(value: number): void {
    const formatted = new Intl.NumberFormat('pt-BR', {
      style: 'currency',
      currency: 'BRL',
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    }).format(value);
    this.renderer.setProperty(this.elementRef.nativeElement, 'value', formatted);
  }
}

