import { describe, expect, it } from 'vitest';
import { seasonalThemeFromReportDate } from '../components/farmflate/fieldDashboardTheme';

describe('field dashboard seasonal background', () => {
  it('uses the report month for date-only and ISO datetime values', () => {
    expect(seasonalThemeFromReportDate('2026-04-02')).toBe('spring');
    expect(seasonalThemeFromReportDate('2026-07-02T06:00:00+09:00')).toBe('summer');
    expect(seasonalThemeFromReportDate('2026-06-01T00:10:00+09:00')).toBe('summer');
    expect(seasonalThemeFromReportDate('2026-10-02')).toBe('autumn');
    expect(seasonalThemeFromReportDate('2026-01-02')).toBe('winter');
  });

  it('falls back safely for malformed dates', () => {
    expect(seasonalThemeFromReportDate('not-a-date', new Date(2026, 6, 1))).toBe('summer');
  });
});
