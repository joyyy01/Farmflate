// @vitest-environment jsdom
import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { RegionReportSummaryView } from '../components/farmflate/RegionReportSummaryView';
import type { RegionReport } from '../types/report';

const report: RegionReport = {
  analysisId: 'analysis-1',
  status: 'COMPLETED',
  region: { sidoCode: '52', sidoName: '전북특별자치도', sigunguCode: '52180', sigunguName: '고창군' },
  baseFitness: 74,
  seasonReadiness: 68,
  dataConfidence: { score: 82, level: 'HIGH', message: '일부 토양 자료는 대표 표본으로 반영되었습니다.' },
  summary: '기본 재배 조건이 비교적 양호합니다.',
  components: {},
  environmentFeatures: [],
  recommendedCrops: [],
  cropResults: [],
  topRisks: [],
  prioritizedActions: [],
  tips: [],
  sources: [{ transformations: ['LEGAL_DONG_SAMPLE_COVERAGE:1/2_OF_7'] }],
  missingMetrics: []
};

describe('RegionReportSummaryView', () => {
  it('explains score meanings and the representative legal-dong sample scope', () => {
    render(<RegionReportSummaryView regionName="전북특별자치도 고창군" report={report} onBack={vi.fn()} onNext={vi.fn()} onOpenAIChat={vi.fn()} />);

    expect(screen.getByText('전반적으로 재배가 가능하지만 일부 환경 관리가 필요합니다.')).toBeInTheDocument();
    expect(screen.getByText('토양과 평년 기후처럼 쉽게 바뀌지 않는 기본 조건이 이 지역에 얼마나 맞는지 보여줘요.')).toBeInTheDocument();
    expect(screen.getByText('앞으로의 날씨와 재배 시기가 지금 시작하기에 얼마나 알맞은지 보여줘요.')).toBeInTheDocument();
    expect(screen.getByText('대상 법정동 7곳 중 대표 2곳을 표본으로 확인했고, 공공 토양 자료가 있는 1곳을 분석에 반영했어요.')).toBeInTheDocument();
    expect(screen.getByText('지역 참고값이므로 실제 밭의 토양검사 결과와 차이가 날 수 있어요.')).toBeInTheDocument();
    expect(screen.getByText('자료 신뢰도는 공공 기상·토양 자료가 현재 지역을 얼마나 충분히 반영하는지 알려주는 참고값이에요.')).toBeInTheDocument();
  });
});
