# Agent trust-boundary hardening design

## Goal

Farmflate의 채팅이 검증 가능한 근거 없이 그럴듯한 답변을 만들거나, 실패 뒤 더 약한 경로로 우회하지 않도록 하나의 Agent 경계로 정리한다.

## Context and evidence

초기 구조에는 엄격한 Responses Agent가 실패했을 때 별도 `LocalChatWorkflow`가 Chat Completions를 다시 호출하는 경로가 있었다. 그 결과 실패한 검증을 다시 통과하지 않는 답변이 나올 수 있었다. 또한 최종 답변과 claim을 별도 필드로 모델이 생성해, 서로 다른 문장·인용이 생길 수 있었다.

OWASP LLM01은 RAG가 프롬프트 인젝션을 완전히 해소하지 못하므로 비신뢰 데이터를 분리하고, 최소 권한·출력 검증·공격 시뮬레이션을 결합할 것을 권고한다. Structured Outputs는 형태를 돕지만, 근거와 문장의 실제 결속은 애플리케이션이 검증해야 한다.

## Decision

1. `LocalChatWorkflow`와 레거시 `/chat/completions` 경로를 제거한다. Agent가 실패하거나 근거 검증을 통과하지 못하면 새 답변을 만들어 우회하지 않고 `needs_context`를 반환한다.
2. 완료 응답은 `answer_blocks[]` 단위로 받는다. 각 블록은 사용자에게 그대로 보이는 문장과 도구가 반환한 인용 ID를 함께 가진다. 서버는 이 블록으로 answer·claim을 조립하고, 인용 집합·claim 렌더링을 재검증한다.
3. `핵심 판단`·`근거`·`지금 할 일`은 초기 strict schema와 프롬프트로 유도한다. 형식만 고치기 위한 추가 모델 요청은 근거를 늘리지 않으면서 비용·지연·보류 실패를 만들므로 두지 않는다. 완료 판정은 표시 형식이 아니라 claim·인용 결속으로 한다.
4. Spring도 Python Agent의 응답을 신뢰하기 전에 상태·답변·출처·인용을 확인한다. 장애 또는 계약 위반은 규칙 답변으로 대체하지 않고 `needs_context`로 끝낸다.
5. 규칙 엔진으로 완결되는 일일 밭 리포트는 LLM Agent 대상에서 제외한다. 검색·인용·사용자 질의 합성이 없는 저장용 안내를 모델이 재서술하면 검증 가능한 가치는 늘지 않고 외부 호출 경계만 늘어난다.
4. `FactPackage`에는 재귀 깊이, 컨테이너 항목 수, 문자열 길이, 총 문자열 예산을 적용한다. 한도를 넘는 요청은 모델 호출 전에 HTTP 422로 거절한다.
5. 오류 로그에는 request ID, 실행 단계, 오류 분류만 남긴다. 프롬프트·원문 예외·비밀값은 남기지 않는다.

## Non-goals

- 외부 벡터 데이터베이스, MCP 서버, 쓰기 권한 도구, 임의의 재시도 정책을 추가하지 않는다.
- 자동 생성 silver 평가를 실제 사용자 만족도나 운영 성과로 표현하지 않는다.
- 충분한 근거가 없는 요청을 길고 그럴듯한 규칙 기반 문장으로 대체하지 않는다.

## Verification

- claim이 최종 답변에 없거나 도구 인용과 불일치하면 `needs_context`가 되는 회귀 테스트
- 제한을 넘는 `FactPackage`가 모델 호출 전에 거절되는 테스트
- 형식과 무관하게 claim·인용 결속을 지키는 completed 답변과, 결속이 깨진 needs_context 답변을 구분하는 테스트
- Python 장애 시 Spring이 출처 없는 `needs_context`를 반환하는 회귀 테스트
- 레거시 workflow·LangGraph·LangChain·requests의 실행 경로 의존성이 없는지 검색
- Python 전체 테스트, Spring 전체 테스트, 두 health endpoint 및 실제 채팅 요청 확인

## Portfolio claim boundary

이 변경 뒤에는 “검증된 function-calling 경로 실패 시 더 약한 LLM 경로로 우회하던 구조를 제거하고, 인용 포함 응답 블록·입력 예산·claim 결속을 회귀 테스트로 고정했다”라고 서술할 수 있다. 실제 비용·장애율·사용자 만족도 개선은 운영 계측 전에는 주장하지 않는다.
