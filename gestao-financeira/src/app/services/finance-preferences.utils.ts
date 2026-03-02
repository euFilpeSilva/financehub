import { AppPreferences } from '../models/finance.models';

export const DEFAULT_BILL_CATEGORIES = [
  'Moradia',
  'Alimentacao',
  'Utilidades',
  'Saude',
  'Transporte',
  'Educacao',
  'Lazer',
  'Outros'
];

export const DEFAULT_INCOME_CATEGORIES = ['Trabalho', 'Extra', 'Investimentos', 'Reembolso', 'Outros'];

export function normalizePreferences(payload: AppPreferences): AppPreferences {
  const billCategories = safeBillCategories(payload);
  const incomeCategories = safeIncomeCategories(payload);
  return {
    ...payload,
    defaultBillCategory: resolveDefaultCategory(payload.defaultBillCategory, billCategories),
    defaultIncomeCategory: resolveDefaultCategory(payload.defaultIncomeCategory, incomeCategories),
    billCategories,
    incomeCategories
  };
}

export function safeBillCategories(payload: Partial<AppPreferences>): string[] {
  return sanitizeCategories(payload.billCategories, DEFAULT_BILL_CATEGORIES);
}

export function safeIncomeCategories(payload: Partial<AppPreferences>): string[] {
  return sanitizeCategories(payload.incomeCategories, DEFAULT_INCOME_CATEGORIES);
}

export function sanitizeCategories(values: string[] | undefined, fallback: string[]): string[] {
  if (!Array.isArray(values) || !values.length) {
    return [...fallback];
  }
  const unique = new Map<string, string>();
  for (const raw of values) {
    const value = raw?.trim();
    if (!value || value.length > 120) {
      continue;
    }
    const key = value.toLowerCase();
    if (!unique.has(key)) {
      unique.set(key, value);
    }
  }
  return unique.size ? Array.from(unique.values()) : [...fallback];
}

export function resolveDefaultCategory(value: string | undefined, categories: string[]): string {
  const normalized = value?.trim()?.toLowerCase();
  const found = categories.find((item) => item.toLowerCase() === normalized);
  return found ?? categories[0];
}
