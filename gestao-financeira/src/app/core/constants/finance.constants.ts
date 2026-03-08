import { TrackedEntityType } from '../../models/finance.models';

export interface NavItem {
  path: string;
  label: string;
}

export interface NavGroup {
  label: string;
  items: NavItem[];
}

export const NAV_GROUPS: NavGroup[] = [
  {
    label: 'Visao Geral',
    items: [{ path: '/dashboard', label: 'Dashboard' }]
  },
  {
    label: 'Operacional',
    items: [
      { path: '/contas', label: 'Saidas' },
      { path: '/entradas', label: 'Entradas' },
      { path: '/extratos', label: 'Importar OFX' },
      { path: '/analise-ofx', label: 'Analise OFX' },
      { path: '/contas-bancarias', label: 'Contas bancarias' },
      { path: '/transferencias-internas', label: 'Transferencias internas' }
    ]
  },
  {
    label: 'Planejamento',
    items: [{ path: '/planejamento', label: 'Metas' }]
  },
  {
    label: 'Governanca',
    items: [
      { path: '/lixeira', label: 'Lixeira' },
      { path: '/auditoria', label: 'Auditoria' },
      { path: '/configuracoes', label: 'Configuracoes' }
    ]
  }
];

const ENTITY_LABELS: Record<TrackedEntityType, string> = {
  bill: 'Saida',
  income: 'Entrada',
  'bank-account': 'Conta bancaria',
  'planning-goal': 'Meta',
  'spending-goal': 'Meta de gasto',
  settings: 'Configuracoes',
  preferences: 'Preferencias',
  transfer: 'Transferencia',
  statement: 'Extrato'
};

export const getEntityLabel = (entity: TrackedEntityType): string => ENTITY_LABELS[entity];
