# Agent 통제 평가 기록 (v3)

## 목적과 범위

응답 계약을 완료/보류 상태에 맞게 분리한 `sectioned-citations-v3` Agent가 승인된 PostgreSQL 근거만으로 안전하게 답하는지 확인했다. 이 기록은 로컬 통제 평가이며, 실제 사용자 트래픽이나 상용 운영 성과를 의미하지 않는다. 전체 시간 예산을 실행 프로필에 추가한 뒤의 재측정은 프로젝트 전용 PostgreSQL이 준비된 환경에서 다시 해야 한다. 현재 로컬 저장소 사전 점검과 보류 사유는 [2026-07-30-agent-evaluation-storage-preflight.md](2026-07-30-agent-evaluation-storage-preflight.md)에 기록한다.

## 고정 조건

- 표본: `controlled_cases(30)`의 한국어 농업 질문 30건
- 실행: `python -m app.agent.controlled_evaluation_cli --live --cases 30 --concurrency 3 --output ...`
- 모델과 검색 설정: 실행 프로필 `agent-2f15dc73b221c0ce`로 고정
- 검색 계층: PostgreSQL lexical FTS 기본 경로
- 출력 계약: 완료 시에만 3개 인용 답변 블록을 요구하고, 근거가 부족한 경우 빈 블록의 `needs_context`를 허용
- 기록 위치: `rag.agent_execution_trace`, `measurement_scope=controlled_local`

## 설계 결정

- HTTP 읽기 제한은 기본 20초에서 45초로 조정했다. 실제 단건 검증에서 20초 읽기 초과를 재현했고, 임의 재시도 대신 요청별 상한을 명시해 중복 모델 호출을 피했다.
- 출력은 800 tokens로 제한했다. 세 개의 한국어 답변 블록과 인용을 작성하기에는 충분하면서도, [생성 토큰이 지연시간의 주요 요인이라는 OpenAI 지침](https://developers.openai.com/api/docs/guides/production-best-practices#improving-latencies)에 맞춰 무제한 생성을 막기 위한 값이다. 이 값은 실행 프로필 해시에 포함된다.
- `needs_context`에는 빈 블록을 허용한다. 완료 상태에만 3개 블록·인용을 강제하므로, 근거가 부족한 경우 형식 때문에 일반 오류가 나는 일을 막는다.

## 결과

| 지표 | 결과 |
| --- | ---: |
| 표본 수 | 30 |
| 인용을 포함한 구조적 완료 | 28 (93.33%) |
| 안전 보류 | 2 (6.67%) |
| 실패 | 0 |
| 중앙 지연시간 | 10,544 ms |
| p95 지연시간 | 25,464 ms |

터미널 사유는 `completed` 28건, `citation_contract` 1건, `model_needs_context` 1건이었다. 즉 완료하지 못한 두 건은 근거 인용 계약 또는 모델의 보류 판단에 따라 답변을 멈춘 경우이며, 일반 오류 응답으로 처리하지 않았다.

CLI 결과 JSON과 PostgreSQL 집계는 표본 수·완료/보류/실패 수·실행 프로필이 일치한다. p50/p95는 CLI의 nearest-rank와 PostgreSQL `percentile_cont` 계산 방식 차이로 소수점 수준의 차이가 날 수 있다.

## 사용 가능한 주장과 제한

- 사용 가능: “동일한 로컬 통제 조건의 30건에서 구조적 완료 93.33%, 실패 0건을 확인했고, 근거 부족·인용 계약 위반은 안전 보류로 처리했다.”
- 사용 금지: “상용 환경에서 검증했다”, “실사용 완료율 93.33%”, “이전 대비 응답 속도가 개선됐다.”
- 다음 검증: 동일 프로필의 추가 표본, 별도 사람이 라벨링한 정답·인용 정확도 평가, 사용자 동의가 있는 환경의 관측을 분리해 축적한다.
