// @vitest-environment jsdom
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MainDashboardView } from '../components/farmflate/MainDashboardView';
import type { FieldProfile } from '../types/report';

describe('MainDashboardView', () => {
  it('shows today\'s status badge for each attention field and preserves the region report action', () => {
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
    expect(screen.getByText('주의 구역 현황')).toBeInTheDocument();
    expect(screen.getByText('오이밭')).toBeInTheDocument();
    expect(screen.getByText('감자밭')).toBeInTheDocument();
    expect(container.querySelector('img[src="/assets/field-alert-mascot.png"]')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '지역 리포트 바로가기' }));
    expect(onOpenReport).toHaveBeenCalledOnce();
    expect(onTabChange).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: '확인하기 →' }));
    expect(onTabChange).toHaveBeenCalledWith('myfield');
  });

  it('shows an all-clear message when no field needs attention', () => {
    render(
      <MainDashboardView
        userName="염예님"
        homeData={null}
        fields={[{ id: 'stable', fieldName: '상추밭', dailyStatus: 'STABLE' }]}
        onGoToExplore={vi.fn()}
        onOpenAIChat={vi.fn()}
        activeTab="home"
        onTabChange={vi.fn()}
      />
    );

    expect(screen.getByText('오늘 주의해야 할 밭이 없어요')).toBeInTheDocument();
    expect(screen.getByText('현재 특별한 위험은 없어요.')).toBeInTheDocument();
  });
});
