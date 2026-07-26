import { describe, expect, it } from 'vitest';
import { needsFreshCropAnalysis } from '../services/reportLifecycle';

describe('needsFreshCropAnalysis', () => {
  it('refreshes a stale crop score when the persisted report already has both required dimensions', () => {
    expect(needsFreshCropAnalysis({
      calculable: false,
      soilSuitabilityScore: 47,
      seasonalTemperatureScore: 93,
    })).toBe(true);
  });

  it('does not refresh when a required environmental dimension is genuinely absent', () => {
    expect(needsFreshCropAnalysis({
      calculable: false,
      soilSuitabilityScore: null,
      seasonalTemperatureScore: 93,
    })).toBe(false);
  });
});
