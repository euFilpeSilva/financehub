import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { routes } from './app.routes';
import { FINANCE_GATEWAY } from './services/finance.gateway';
import { HttpFinanceGateway } from './services/http-finance.gateway';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideHttpClient(),
    provideRouter(routes),
    HttpFinanceGateway,
    { provide: FINANCE_GATEWAY, useExisting: HttpFinanceGateway }
  ]
};
