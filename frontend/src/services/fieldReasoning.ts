import type { FieldDashboardResponse } from '../types/report';

const LEGACY_NUMERIC_REASON = /\d+(?:\.\d+)?\s*(?:℃|mm|%|m\/s|점)/;

function objectParticle(value: string): '을' | '를' {
  const last = value.charCodeAt(value.length - 1);
  return last >= 0xac00 && last <= 0xd7a3 && (last - 0xac00) % 28 === 0 ? '를' : '을';
}

/**
 * Keeps a generated daily explanation intact, while making legacy reports that
 * stored a raw weather value readable until their next daily generation.
 */
export function formatFieldReasoningSummary(dashboard: FieldDashboardResponse): string {
  const summary = dashboard.reasoning.summary?.trim();
  if (summary && !LEGACY_NUMERIC_REASON.test(summary)) {
    return summary;
  }

  const crop = dashboard.field.cropName?.trim() || '이 작물';
  const alert = dashboard.alerts[0]?.title?.trim() || '오늘의 환경 변화';
  const task = dashboard.tasks.find(item => !item.acknowledged)?.title?.trim()
    || dashboard.tasks[0]?.title?.trim()
    || '밭 상태 확인';

  return `${crop}에 ${alert}가 예상돼요. 그래서 ${task}${objectParticle(task)} 먼저 안내했어요.`;
}
