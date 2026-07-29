# ADR-002: Agent는 근거 검증 뒤에 새 문장을 추가하지 않는다

- 상태: 채택
- 결정일: 2026-07-29

## 문제

기존 Agent는 도구 호출 결과의 인용을 검증한 뒤, 짧은 답변을 길게 만들기 위해 화면 컨텍스트의 일반 조언을 덧붙였다. 이 단계에서 추가된 문장은 도구가 반환한 인용과 연결되지 않아, 완료 상태의 답변에도 근거 밖 정보가 섞일 수 있었다.

## 결정

Agent의 읽기 전용 흐름을 `도구 선택 → 승인 컨텍스트/지식 검색 → 인용 검증 → 구조화된 응답`으로 제한한다. 완료 답변은 검증된 초안 문자열을 그대로 반환하며, 후처리로 새로운 사실·행동 지침·일반 농업 조언을 추가하지 않는다.

## 선택 근거

- 농업 의사결정 보조에서는 답변의 길이보다 “어떤 현재 분석 또는 승인 지식에 근거했는가”가 더 중요하다.
- 사용자 화면에서 보이는 지역·밭 정보만 읽도록 한 도구와, 승인 지식만 검색하도록 한 도구가 이미 분리돼 있다. 후처리가 이 경계를 우회하면 도구 권한 분리의 의미가 사라진다.
- 모델 응답의 품질은 프롬프트와 JSON 스키마에서 관리하고, 근거가 부족하면 `needs_context`를 반환한다. 길이를 채우기 위한 템플릿 문장은 사용하지 않는다.

## 운영 기록

- 검색 원문 대신 SHA-256, 검색 경로, 후보 수, 결과 청크 ID, 지연 시간만 `rag.retrieval_trace`에 기록한다.
- Agent trace에는 `retrieval:hybrid:…` 또는 `retrieval:lexical:…` 진단을 남긴다. 이 기록은 응답 품질 문제를 검색 경로·근거 부족·도구 실패로 구분하는 데 사용한다.
- `rag.eval_case`를 실행해 recall@k, citation precision, latency를 `rag.eval_result`에 저장한다. LLM 심사 대신 기대 청크·기대 인용과의 결정적 비교를 사용한다.

## 검증 근거

- 인용 검증과 후처리 제거: `python/app/agent/runner.py`
- 도구 권한·검색 진단: `python/app/agent/tools.py`
- 평가 실행기: `python/app/rag/evaluation.py`, `python/app/rag/evaluate_cli.py`
- 회귀 테스트: `python/tests/test_agent_runner.py`, `python/tests/test_agent_tools.py`, `python/tests/test_rag_evaluation.py`
