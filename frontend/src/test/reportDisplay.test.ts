import { describe, expect, it } from 'vitest';
import { formatReportPeriod } from '../utils/reportDisplay';

describe('report period formatting', () => {
  it('orders valid risk dates chronologically before presenting them', () => {
    expect(formatReportPeriod('2026-07-31', '2026-07-27')).toBe(
      '2026년 7월 27일 ~ 2026년 7월 31일',
    );
  });

  it('keeps an unparseable provider value visible instead of inventing a date', () => {
    expect(formatReportPeriod('예보 갱신 대기', '2026-07-27')).toBe(
      '예보 갱신 대기 ~ 2026년 7월 27일',
    );
  });
});
