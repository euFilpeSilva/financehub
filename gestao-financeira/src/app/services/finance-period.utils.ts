export function createSafeDate(month: string, day: number): string {
  const [year, monthNumber] = month.split('-').map((value) => Number(value));
  const maxDay = new Date(year, monthNumber, 0).getDate();
  const safeDay = Math.max(1, Math.min(day, maxDay));
  return `${month}-${safeDay.toString().padStart(2, '0')}`;
}

export function lastDayOfMonth(month: string): string {
  const [year, monthNumber] = month.split('-').map((value) => Number(value));
  const last = new Date(year, monthNumber, 0).getDate();
  return `${month}-${last.toString().padStart(2, '0')}`;
}

export function getCurrentMonth(): string {
  const today = new Date();
  const year = today.getFullYear();
  const month = (today.getMonth() + 1).toString().padStart(2, '0');
  return `${year}-${month}`;
}

export function matchesMonthAndRange(date: string, selectedMonth: string, start: string, end: string): boolean {
  const monthMatch = date.startsWith(selectedMonth);

  if (!start && !end) {
    return monthMatch;
  }

  if (start && date < start) {
    return false;
  }

  if (end && date > end) {
    return false;
  }

  return true;
}

export function resolveDashboardSummaryPeriod(selectedMonth: string, start: string, end: string): { startDate: string; endDate: string } {
  if (start || end) {
    return {
      startDate: start || '0001-01-01',
      endDate: end || '9999-12-31'
    };
  }

  return {
    startDate: `${selectedMonth}-01`,
    endDate: lastDayOfMonth(selectedMonth)
  };
}
