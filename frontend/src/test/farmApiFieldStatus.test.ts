import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiService } from '../services/api';

describe('ApiService.getFields', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('keeps the backend DANGER status for a field card', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify([{
      id: 'field-danger',
      fieldName: '고창 감자밭',
      dailyStatus: 'DANGER',
      dailyStatusLabel: '위험',
      dailyAlerts: [{
        key: 'HEAT_STRESS',
        severity: 'HIGH',
        title: '오후 고온 주의',
        description: '고온 피해를 막기 위해 점검이 필요합니다.'
      }]
    }]), { status: 200, headers: { 'Content-Type': 'application/json' } })));

    const [field] = await ApiService.getFields();

    expect(field.dailyStatus).toBe('DANGER');
    expect(field.dailyStatusLabel).toBe('위험');
    expect(field.dailyAlerts).toHaveLength(1);
  });
});
