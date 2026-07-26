// @vitest-environment jsdom
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MainDashboardView } from '../components/farmflate/MainDashboardView';
import type { FieldProfile } from '../types/report';

describe('MainDashboardView', () => {
  it('summarizes attention fields by name and preserves the region report action', () => {
    const onTabChange = vi.fn();
    const onOpenReport = vi.fn();
    const fields: FieldProfile[] = [
      { id: 'stable', fieldName: '상추밭', dailyStatus: 'STABLE' },
      { id: 'caution', fieldName: '오이밭', dailyStatus: 'CAUTION' },
      { id: 'danger', fieldName: '감자밭', dailyStatus: 'DANGER' }
    ];

    const { container } = render(
      <MainDashboardView
        userName="염예님"
        homeData={null}
        fields={fields}
        onGoToExplore={vi.fn()}
        onOpenReport={onOpenReport}
        onOpenAIChat={vi.fn()}
        activeTab="home"
        onTabChange={onTabChange}
      />
    );

    expect(screen.getByText('오늘 주의해야 할 밭이 2개 있어요')).toBeInTheDocument();
    expect(screen.getByText('오이밭은 주의, 감자밭은 위험 상태예요.')).toBeInTheDocument();
    expect(screen.queryByText('내 밭 확인하기 ›')).not.toBeInTheDocument();
    expect(container.querySelector('img[src="/svg-assets/weather/water-drop-alert.svg"]')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '지역 리포트 보기 ›' }));
    expect(onOpenReport).toHaveBeenCalledOnce();
    expect(onTabChange).not.toHaveBeenCalled();
  });

  it('uses a natural Korean topic particle for a field name ending in a vowel', () => {
    render(
      <MainDashboardView
        userName="염예님"
        homeData={null}
        fields={[{ id: 'demo', fieldName: '고창 감자밭 데모', dailyStatus: 'CAUTION' }]}
        onGoToExplore={vi.fn()}
        onOpenAIChat={vi.fn()}
        activeTab="home"
        onTabChange={vi.fn()}
      />
    );

    expect(screen.getByText('고창 감자밭 데모는 주의 상태예요.')).toBeInTheDocument();
  });
});
