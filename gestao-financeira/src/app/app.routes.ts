import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'dashboard'
  },
  {
    path: 'dashboard',
    loadComponent: () =>
      import('./features/dashboard/dashboard-page.component').then((m) => m.DashboardPageComponent)
  },
  {
    path: 'contas',
    loadComponent: () =>
      import('./features/bills/bills-page.component').then((m) => m.BillsPageComponent)
  },
  {
    path: 'entradas',
    loadComponent: () =>
      import('./features/incomes/incomes-page.component').then((m) => m.IncomesPageComponent)
  },
  {
    path: 'planejamento',
    loadComponent: () =>
      import('./features/planning/planning-page.component').then((m) => m.PlanningPageComponent)
  },
  {
    path: 'extratos',
    loadComponent: () =>
      import('./features/statements-import/statements-import-page.component').then((m) => m.StatementsImportPageComponent)
  },
  {
    path: 'analise-ofx',
    loadComponent: () =>
      import('./features/ofx-analysis/ofx-analysis-page.component').then((m) => m.OfxAnalysisPageComponent)
  },
  {
    path: 'contas-bancarias',
    loadComponent: () =>
      import('./features/bank-accounts/bank-accounts-page.component').then((m) => m.BankAccountsPageComponent)
  },
  {
    path: 'transferencias-internas',
    loadComponent: () =>
      import('./features/internal-transfers/internal-transfers-page.component').then((m) => m.InternalTransfersPageComponent)
  },
  {
    path: 'lixeira',
    loadComponent: () =>
      import('./features/trash/trash-page.component').then((m) => m.TrashPageComponent)
  },
  {
    path: 'auditoria',
    loadComponent: () =>
      import('./features/audit/audit-page.component').then((m) => m.AuditPageComponent)
  },
  {
    path: 'configuracoes',
    loadComponent: () =>
      import('./features/settings/settings-page.component').then((m) => m.SettingsPageComponent)
  },
  {
    path: '**',
    redirectTo: 'dashboard'
  }
];
