import { describe, expect, it } from 'vitest';
import { formatFieldReasoningSummary } from '../services/fieldReasoning';
import type { FieldDashboardResponse } from '../types/report';

const dashboard = {
  field: { cropName: '상추' },
  alerts: [{ title: '오후 고온 주의' }],
  tasks: [{ title: '차광과 통풍 확인', acknowledged: false }],
  reasoning: { summary: '오늘 예상 최고기온 31℃', points: [] },
} as unknown as FieldDashboardResponse;

describe('formatFieldReasoningSummary', () => {
  it('rephrases a legacy numeric-only reason as a crop-condition-action explanation', () => {
    expect(formatFieldReasoningSummary(dashboard)).toBe(
      '상추에 오후 고온 주의가 예상돼요. 그래서 차광과 통풍 확인을 먼저 안내했어요.',
    );
  });

  it('keeps a generated causal summary intact', () => {
    const generated = '상추에 오후 고온 주의가 예상돼요. 잎이 처질 수 있어 차광과 통풍 확인을 먼저 안내했어요.';
    expect(formatFieldReasoningSummary({
      ...dashboard,
      reasoning: { summary: generated, points: [] },
    })).toBe(generated);
  });
});
