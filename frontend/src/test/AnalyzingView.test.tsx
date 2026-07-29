import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { AnalyzingView } from '../components/farmflate/AnalyzingView';
import { deriveAnalysisStepDisplay } from '../services/analysisProgress';
import type { AnalysisState } from '../services/reportLifecycle';

const regionSteps = [
  '지역 행정구역 및 법정동 정보 확인 중',
  '기상청 30일 기온 관측 및 단기예보 수집 중',
  '흙토람 시군구 농경지 토양 화학성 분석 중',
  '재배 적합 대표 작물군 및 추천 알고리즘 산출 중',
  '지역 통합 농사 환경 평가 점수 계산 중'
];

const renderView = (state: AnalysisState) => render(
  <AnalyzingView
    regionName="수원시"
    state={state}
    onRetry={vi.fn()}
    onBack={vi.fn()}
    onLogin={vi.fn()}
  />
);

describe('AnalyzingView', () => {
  it('keeps exactly one spinner on the server-reported current row', () => {
    const state: AnalysisState = {
      kind: 'POLLING',
      analysisId: 'analysis-1',
      currentStepCode: 'SOIL',
      completedStepCodes: ['REGION', 'RECENT_WEATHER'],
      currentStep: null,
      completedSteps: []
    };

    const display = deriveAnalysisStepDisplay(state, regionSteps);
    expect(display).toEqual({ activeStepIndex: 2, completedStepIndexes: [0, 1] });

    renderView(state);
    expect(screen.getAllByLabelText('분석 중')).toHaveLength(1);
    expect(screen.getAllByLabelText('완료')).toHaveLength(2);
  });

  it('hands an instant terminal catch-up to the next incomplete row', () => {
    const state: AnalysisState = {
      kind: 'POLLING',
      analysisId: 'analysis-2',
      currentStepCode: null,
      completedStepCodes: ['REGION', 'RECENT_WEATHER'],
      currentStep: null,
      completedSteps: []
    };

    expect(deriveAnalysisStepDisplay(state, regionSteps)).toEqual({
      activeStepIndex: 2,
      completedStepIndexes: [0, 1]
    });

    renderView(state);
    expect(screen.getAllByLabelText('분석 중')).toHaveLength(1);
    expect(screen.getAllByLabelText('완료')).toHaveLength(2);
  });

  it('stops every spinner and exposes the retry action after an analysis failure', () => {
    renderView({ kind: 'ERROR', message: '분석에 실패했습니다.', retryable: true });

    expect(screen.queryByLabelText('분석 중')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '지역 다시 선택' })).toBeInTheDocument();
  });
});
