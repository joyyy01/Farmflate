// @vitest-environment jsdom
import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { FieldDashboardView } from '../components/farmflate/FieldDashboardView';
import { ApiService } from '../services/api';
import type { FieldDashboardResponse, FieldProfile } from '../types/report';

const field: FieldProfile = {
  id: 'field-1',
  fieldName: '상추밭',
  cropName: '상추',
  stage: 'GROWING'
};

const dashboard: FieldDashboardResponse = {
  field: {
    id: 'field-1',
    fieldName: '상추밭',
    cropCode: 'LETTUCE',
    cropName: '상추',
    regionName: '고창군',
    cultivationStartDate: '2026-07-08',
    cultivationDay: 18,
    stage: 'GROWING'
  },
  report: {
    id: 'report-1',
    reportDate: '2026-07-26',
    generatedAt: '2026-07-26T06:00:00+09:00',
    generationReason: 'DAILY_0630',
    status: 'CAUTION',
    headline: '오후 고온 주의',
    headlineDescription: '고온 시간대에 잎이 처질 수 있어요.',
    historical: false,
    taskCountBeforeAcknowledgement: 1,
    statusScore: 62,
    statusScoreZone: '주의'
  },
  weather: {
    status: 'AVAILABLE', currentTemperature: 25, minTemperature: 19, maxTemperature: 29,
    precipitationProbability: 0, rainfallMm: 0, humidity: 55, windSpeed: 1.4, condition: '맑음'
  },
  tasks: [{
    key: 'CHECK_SOIL_MOISTURE', title: '흙 상태 확인 후 물주기 결정',
    description: '토양 수분을 확인해요.', badge: 'MORNING_RECOMMENDED', acknowledged: false
  }],
  alerts: [],
  reasoning: { summary: '고온이 예상돼요.', points: [] },
  todayLogs: [],
  history: []
};

describe('FieldDashboardView', () => {
  afterEach(() => vi.restoreAllMocks());

  it('does not show a record action in today\'s required tasks', async () => {
    vi.spyOn(ApiService, 'getFieldDashboard').mockResolvedValue(dashboard);

    render(<FieldDashboardView field={field} onBack={vi.fn()} onOpenAIChat={vi.fn()} />);

    await screen.findByText('흙 상태 확인 후 물주기 결정');
    expect(screen.queryByRole('button', { name: '기록 남기기' })).not.toBeInTheDocument();
  });
});
