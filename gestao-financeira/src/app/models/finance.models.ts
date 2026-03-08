export interface BillRecord {
  id: string;
  description: string;
  category: string;
  amount: number;
  dueDate: string;
  recurring: boolean;
  paid: boolean;
  internalTransfer?: boolean;
  bankAccountId?: string | null;
}

export interface IncomeEntry {
  id: string;
  source: string;
  category: string;
  amount: number;
  receivedAt: string;
  recurring: boolean;
  internalTransfer?: boolean;
  bankAccountId?: string | null;
}

export interface PlanningGoal {
  id: string;
  title: string;
  targetAmount: number;
  currentAmount: number;
  targetDate: string;
  notes: string;
  complete: boolean;
}

export type SpendingGoalSchedule = 'monthly' | 'custom';

export interface SpendingGoal {
  id: string;
  title: string;
  limitAmount: number;
  category: string;
  schedule: SpendingGoalSchedule;
  startMonth?: string;
  startDate?: string;
  endDate?: string;
  active: boolean;
}

export interface PeriodSnapshot {
  income: number;
  expenses: number;
  savings: number;
}

export interface ComparisonSummary {
  firstLabel: string;
  secondLabel: string;
  first: PeriodSnapshot;
  second: PeriodSnapshot;
  incomeDelta: number;
  expenseDelta: number;
  savingsDelta: number;
}

export interface MonthlyPerformance {
  month: string;
  income: number;
  expenses: number;
  savings: number;
}

export interface SpendingGoalStatus {
  goal: SpendingGoal;
  spentAmount: number;
  remainingAmount: number;
  usagePercent: number;
  onTrack: boolean;
  periodLabel: string;
}

export interface BankAccount {
  id: string;
  label: string;
  bankId: string;
  branchId?: string | null;
  accountId: string;
  primaryIncome: boolean;
  active: boolean;
}

export type TrackedEntityType =
  | 'bill'
  | 'income'
  | 'bank-account'
  | 'planning-goal'
  | 'spending-goal'
  | 'settings'
  | 'preferences'
  | 'transfer'
  | 'statement';

export type AuditAction =
  | 'create'
  | 'update'
  | 'delete'
  | 'import'
  | 'restore'
  | 'purge'
  | 'toggle-status'
  | 'export'
  | 'link-internal'
  | 'auto-link-internal';

export interface TrashItem {
  id: string;
  entityType: TrackedEntityType;
  entityId: string;
  label: string;
  payload: unknown;
  deletedAt: string;
  purgeAt: string;
}

export interface AuditEvent {
  id: string;
  entityType: TrackedEntityType;
  entityId: string;
  action: AuditAction;
  entityName?: string;
  amount?: number;
  message: string;
  timestamp: string;
}

export interface DataRetentionSettings {
  trashRetentionDays: number;
  auditRetentionDays: number;
}

export type DashboardComparisonMode = 'month' | 'range';

export interface AppPreferences {
  defaultBillCategory: string;
  defaultBillRecurring: boolean;
  defaultBillDueDay: number;
  defaultIncomeCategory: string;
  defaultIncomeRecurring: boolean;
  defaultIncomeReceivedDay: number;
  defaultDashboardMode: DashboardComparisonMode;
  defaultDashboardMonthComparisonOffset: number;
  billCategories: string[];
  incomeCategories: string[];
}

export interface OfxImportResult {
  fileName: string;
  totalTransactions: number;
  createdBills: number;
  createdIncomes: number;
  skippedDuplicates: number;
  ignoredAlreadyImported: number;
  internalTransfersMarked: number;
}

export interface OfxAnalysisTransaction {
  fileName: string;
  ofxOwnerBankAccountId?: string | null;
  ofxOwnerBankLabel?: string | null;
  postedAt: string;
  year: number;
  yearMonth: string;
  sourceType: 'STMTTRN' | 'BAL';
  amount: number;
  direction: 'credit' | 'debit';
  memo: string;
  normalizedMemo: string;
  patternKey: string;
  ignoredByMarker: boolean;
  likelyInternalTransfer: boolean;
  itauPairDuplicateCandidate: boolean;
}

export interface OfxAnalysisGroup {
  patternKey: string;
  sampleMemo: string;
  totalCount: number;
  creditCount: number;
  debitCount: number;
  ignoredCount: number;
  likelyInternalCount: number;
  itauPairCandidateCount: number;
  totalCreditAmount: number;
  totalDebitAmount: number;
  years: number[];
  yearMonths: string[];
}

export interface OfxAnalysisResult {
  totalFiles: number;
  totalTransactions: number;
  totalCredits: number;
  totalDebits: number;
  availableYears: number[];
  availableYearMonths: string[];
  groups: OfxAnalysisGroup[];
  transactions: OfxAnalysisTransaction[];
}

export interface ImportedStatementYearCleanupRequest {
  year: number;
  month?: number | null;
  bankAccountIds?: string[] | null;
  dryRun: boolean;
  permanentDelete: boolean;
}

export interface ImportedStatementYearCleanupResult {
  year: number;
  month?: number | null;
  startDate: string;
  endDate: string;
  bankAccountIds?: string[] | null;
  dryRun: boolean;
  permanentDelete: boolean;
  matchedBills: number;
  matchedIncomes: number;
  processedBills: number;
  processedIncomes: number;
  movedToTrash: number;
  deletedPermanently: number;
  totalMatched: number;
  totalProcessed: number;
}

export type OfxImportProgressEvent =
  | {
      kind: 'progress';
      fileName: string;
      progress: number;
      phase: 'uploading' | 'processing';
    }
  | {
      kind: 'completed';
      fileName: string;
      progress: 100;
      phase: 'completed';
      result: OfxImportResult;
    };

export type OfxImportBatchStatus = 'idle' | 'running' | 'success' | 'partial' | 'error';

export interface OfxImportBatchProgress {
  visible: boolean;
  running: boolean;
  status: OfxImportBatchStatus;
  currentFileName: string;
  currentFileProgress: number;
  currentFilePhase: 'uploading' | 'processing' | 'completed';
  processedFiles: number;
  totalFiles: number;
  successCount: number;
  failureCount: number;
  overallProgress: number;
}

export interface DashboardSummary {
  incomeTotal: number;
  expenseTotal: number;
  balance: number;
  paidBillsTotal: number;
  pendingBillsTotal: number;
}

export interface AccountReconciliation {
  bankAccountId: string;
  bankAccountLabel: string;
  startDate: string;
  endDate: string;
  incomeTotal: number;
  expenseTotal: number;
  calculatedBalance: number;
  referenceBalance?: number | null;
  difference?: number | null;
  incomeCount: number;
  expenseCount: number;
  legacyIncomeMatches: number;
  legacyExpenseMatches: number;
}

export interface InternalTransferSuggestion {
  billId: string;
  incomeId: string;
  billDescription: string;
  incomeSource: string;
  billBankAccountId?: string | null;
  incomeBankAccountId?: string | null;
  billBankAccountLabel: string;
  incomeBankAccountLabel: string;
  billDate: string;
  incomeDate: string;
  amount: number;
  score: number;
  confidence: 'ALTA' | 'MEDIA' | 'BAIXA';
  reasons: string[];
}
