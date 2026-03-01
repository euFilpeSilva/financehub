import { DOCUMENT } from '@angular/common';
import { Inject, Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly storageKey = 'gestao-financeira-theme';
  readonly isDark = signal(false);

  constructor(@Inject(DOCUMENT) private readonly document: Document) {
    this.initialize();
  }

  toggleTheme(): void {
    this.setDarkMode(!this.isDark());
  }

  setDarkMode(enabled: boolean): void {
    this.isDark.set(enabled);
    this.document.documentElement.classList.toggle('dark', enabled);
    try {
      window.localStorage.setItem(this.storageKey, enabled ? 'dark' : 'light');
    } catch {
      // ignore storage errors
    }
  }

  private initialize(): void {
    let resolved = false;
    try {
      const stored = window.localStorage.getItem(this.storageKey);
      if (stored === 'dark' || stored === 'light') {
        this.setDarkMode(stored === 'dark');
        resolved = true;
      }
    } catch {
      // ignore storage errors
    }

    if (!resolved) {
      const prefersDark = window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false;
      this.setDarkMode(prefersDark);
    }
  }
}

