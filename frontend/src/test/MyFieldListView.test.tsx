// @vitest-environment jsdom
import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MyFieldListView } from '../components/farmflate/MyFieldListView';
import type { FieldProfile } from '../types/report';

describe('MyFieldListView', () => {
  it('uses the daily status for the top-right card badge and keeps alert details in the card body', () => {
    const field: FieldProfile = {
      id: 'field-1',
      fieldName: '감자밭',
      cropName: '감자',
      stage: 'GROWING',
      cultivationDay: 18,
      dailyStatus: 'CAUTION',
      dailyStatusLabel: '주의',
      dailyAlerts: [{
        key: 'HIGH_TEMPERATURE',
        severity: 'MEDIUM',
        title: '오후 고온 주의',
        description: '오늘 최고기온이 30℃까지 오를 전망이에요.'
      }]
    };

    render(
      <MyFieldListView
        fields={[field]}
        activeTab="myfield"
        onAddField={vi.fn()}
        onSelectField={vi.fn()}
        onOpenAIChat={vi.fn()}
        onTabChange={vi.fn()}
      />
    );

    expect(screen.getByTitle('주의')).toHaveTextContent('주의');
    expect(screen.getByText('오후 고온 주의')).toBeInTheDocument();
  });
});
