export type SeasonalTheme = 'spring' | 'summer' | 'autumn' | 'winter';

const CROP_ILLUSTRATION_BY_NAME: Record<string, string> = {
  '상추': '/svg-assets/crops/lettuce.svg',
  '오이': '/svg-assets/crops/cucumber.svg',
  '감자': '/svg-assets/crops/potato.svg',
  '고추': '/svg-assets/crops/pepper.svg',
  '토마토': '/svg-assets/crops/tomato.svg',
  '배추': '/svg-assets/crops/cabbage.svg',
  '사과': '/svg-assets/crops/apple.svg',
  '배': '/svg-assets/crops/pear.svg'
};

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

export const cropIllustrationForName = (cropName: string | null | undefined): string =>
  CROP_ILLUSTRATION_BY_NAME[cropName ?? ''] ?? '/svg-assets/crops/sprout.svg';
