import { describe, expect, it } from 'vitest';
import { getChatSuggestions } from '../services/chatSuggestions';

describe('chat suggestions', () => {
  it('shows questions that match a persisted field dashboard', () => {
    const suggestions = getChatSuggestions({
      route: 'field_dashboard',
      fieldId: 'field-1',
      regionAnalysisId: null,
      reportDate: '2026-07-26',
    });

    expect(suggestions.map(({ title }) => title)).toContain('오늘 이 밭에서 가장 먼저 할 일은 무엇인가요?');
  });

  it('shows report questions only when a regional analysis exists', () => {
    const suggestions = getChatSuggestions({
      route: 'region_report',
      fieldId: null,
      regionAnalysisId: 'analysis-1',
      reportDate: null,
    });

    expect(suggestions[0].title).toContain('점수와 가장 큰 위험');
  });

  it('uses immediately answerable learning questions before analysis', () => {
    const suggestions = getChatSuggestions({
      route: 'home',
      fieldId: null,
      regionAnalysisId: null,
      reportDate: null,
    });

    expect(suggestions.map(({ title }) => title)).toContain('토양 pH는 무슨 뜻인가요?');
  });
});
