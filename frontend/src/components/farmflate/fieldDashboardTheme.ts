export type SeasonalTheme = 'spring' | 'summer' | 'autumn' | 'winter';

export const seasonalThemeFromReportDate = (
  reportDate: string | null | undefined,
  fallbackDate = new Date()
): SeasonalTheme => {
  const normalizedDate = reportDate?.trim();
  const reportMonth = normalizedDate?.match(/^(?:\d{4})-(\d{2})/)?.[1];
  const monthFromReport = reportMonth ? Number(reportMonth) : Number.NaN;
  if (monthFromReport >= 1 && monthFromReport <= 12) {
    if (monthFromReport >= 3 && monthFromReport <= 5) return 'spring';
    if (monthFromReport >= 6 && monthFromReport <= 8) return 'summer';
    if (monthFromReport >= 9 && monthFromReport <= 11) return 'autumn';
    return 'winter';
  }

  const parsedDate = normalizedDate
    ? new Date(normalizedDate.includes('T') ? normalizedDate : `${normalizedDate}T12:00:00`)
    : fallbackDate;
  const effectiveDate = Number.isNaN(parsedDate.getTime()) ? fallbackDate : parsedDate;
  const month = effectiveDate.getMonth() + 1;

  if (month >= 3 && month <= 5) return 'spring';
  if (month >= 6 && month <= 8) return 'summer';
  if (month >= 9 && month <= 11) return 'autumn';
  return 'winter';
};
