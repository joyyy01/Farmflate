import type { AIChatContext } from '../types/chat';

export interface ChatSuggestion {
  title: string;
  desc: string;
  img: string;
}

const FIELD_SUGGESTIONS: ChatSuggestion[] = [
  {
    title: '오늘 이 밭에서 가장 먼저 할 일은 무엇인가요?',
    desc: '저장된 오늘의 상태와 관리 우선순위를 알려드려요.',
    img: '/svg-assets/ai/faq-icons/state-question.svg'
  },
  {
    title: '현재 상태가 나온 이유를 설명해 주세요',
    desc: '날씨와 현장 분석 근거를 쉬운 말로 풀어드려요.',
    img: '/svg-assets/ai/faq-icons/season-question.svg'
  },
  {
    title: '물을 주기 전에 무엇을 확인해야 하나요?',
    desc: '오늘 날씨를 바탕으로 직접 확인할 지점을 알려드려요.',
    img: '/svg-assets/ai/faq-icons/crop-question.svg'
  }
];

const REPORT_SUGGESTIONS: ChatSuggestion[] = [
  {
    title: '이 지역의 점수와 가장 큰 위험 요인을 쉽게 설명해 주세요',
    desc: '분석 점수와 주의할 환경을 알기 쉽게 정리해 드려요.',
    img: '/svg-assets/ai/faq-icons/season-question.svg'
  },
  {
    title: '첫 번째 추천 작물은 왜 추천됐나요?',
    desc: '추천 작물의 환경 적합 근거를 설명해 드려요.',
    img: '/svg-assets/ai/faq-icons/crop-question.svg'
  },
  {
    title: '이번 주에 특히 확인할 날씨 위험은 무엇인가요?',
    desc: '예보와 분석 결과에서 우선 살필 위험을 알려드려요.',
    img: '/svg-assets/ai/faq-icons/state-question.svg'
  }
];

const LEARNING_SUGGESTIONS: ChatSuggestion[] = [
  {
    title: '상추 재배에 필요한 환경을 알려주세요',
    desc: '분석 전에도 볼 수 있는 기본 재배 조건을 알려드려요.',
    img: '/svg-assets/ai/faq-icons/crop-question.svg'
  },
  {
    title: '토양 pH는 무슨 뜻인가요?',
    desc: '농사 용어를 쉬운 말로 설명해 드려요.',
    img: '/svg-assets/ai/faq-icons/term-question.svg'
  },
  {
    title: '지역 분석에서는 무엇을 확인하나요?',
    desc: '날씨와 토양 정보를 어떻게 살피는지 알려드려요.',
    img: '/svg-assets/ai/faq-icons/season-question.svg'
  }
];

export function getChatSuggestions(context: AIChatContext): ChatSuggestion[] {
  if (context.fieldId) return FIELD_SUGGESTIONS;
  if (context.regionAnalysisId) return REPORT_SUGGESTIONS;
  return LEARNING_SUGGESTIONS;
}
