/**
 * User-entered field activity log (물주기/비료/잎 상태 확인 등) and soil test
 * results. There is no backend endpoint for either of these yet, so both are
 * kept in localStorage, scoped per field id. This is real user input, not
 * generated data — it should only ever contain what the user actually typed.
 * Once a backend endpoint exists, swap the bodies of these functions for the
 * real API calls and keep the exported shapes the same.
 */

export type FieldLogCategory = '물주기' | '비료' | '잎 상태 확인' | '병해충 방제' | '기타';

export interface FieldLogEntry {
  id: string;
  fieldId: string;
  category: FieldLogCategory;
  note: string;
  loggedAt: string; // ISO date-time, when the user saved this entry
}

export interface SoilTestResult {
  ph: number | null;
  ec: number | null;
  testedAt: string; // YYYY-MM-DD, user-entered
}

const LOG_KEY_PREFIX = 'farmflate_field_logs_';
const SOIL_KEY_PREFIX = 'farmflate_soil_test_';

export function getFieldLogs(fieldId: string): FieldLogEntry[] {
  try {
    const raw = localStorage.getItem(LOG_KEY_PREFIX + fieldId);
    const parsed = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export function addFieldLog(fieldId: string, category: FieldLogCategory, note: string): FieldLogEntry[] {
  const entry: FieldLogEntry = {
    id: `log_${Date.now()}`,
    fieldId,
    category,
    note,
    loggedAt: new Date().toISOString()
  };
  const next = [entry, ...getFieldLogs(fieldId)].slice(0, 100);
  localStorage.setItem(LOG_KEY_PREFIX + fieldId, JSON.stringify(next));
  return next;
}

export function hasRecentLog(fieldId: string, category: FieldLogCategory, withinDays: number): boolean {
  const cutoff = Date.now() - withinDays * 24 * 60 * 60 * 1000;
  return getFieldLogs(fieldId).some(log => log.category === category && new Date(log.loggedAt).getTime() >= cutoff);
}

export function getSoilTestResult(fieldId: string): SoilTestResult | null {
  try {
    const raw = localStorage.getItem(SOIL_KEY_PREFIX + fieldId);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export function saveSoilTestResult(fieldId: string, result: SoilTestResult): void {
  localStorage.setItem(SOIL_KEY_PREFIX + fieldId, JSON.stringify(result));
}
