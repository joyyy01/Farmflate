# Agent 통제 평가 저장소 사전 점검

## 목적

통제 평가의 완료율과 지연 시간은 PostgreSQL 실행 지표까지 함께 남아야만 재현 가능한 근거가 된다. 응답을 생성했다는 사실만으로는 포트폴리오용 평가 결과를 만들지 않는다.

## 2026-07-30 로컬 확인

- 프로젝트 설정은 `127.0.0.1:5432/interview_db`의 전용 `farmflate` 역할을 가리킨다.
- 해당 포트에는 프로젝트 PostgreSQL 리스너가 없었다.
- 같은 PC에서 발견된 별도 제품 소유 PostgreSQL은 다른 포트에서 실행 중이지만 `farmflate` 역할이 없었다. 데이터 경계와 역할이 다르므로 설정을 그 인스턴스로 바꾸거나 역할·DB를 만들지 않았다.
- 따라서 RAG 검색은 근거를 확보하지 못해 안전한 `needs_context`로 끝났고, 실행 지표도 저장할 수 없었다. 이 실행에서 만들어진 30건 요약 파일은 근거로 사용하지 않고 폐기했다.

## 보완

`PostgresRagRetriever.record_agent_execution()`은 관측 기록의 저장 성공 여부를 반환한다. 일반 사용자 요청에서는 기록 실패가 답변 자체를 실패시키지 않는다. 반면 `controlled_evaluation_cli --live`는 텔레메트리를 저장하지 못하거나 텔레메트리가 없으면 비정상 종료하며 결과 JSON을 쓰지 않는다.

이는 사용자 경로의 가용성과, 수치 근거의 재현성을 서로 다른 경계로 관리하기 위한 선택이다. 관측 저장 실패를 사용자 오류로 전파하지 않되, 저장되지 않은 결과를 완료율·p95의 증거로 제시하지 않는다.

## 재현 확인

- 핵심 회귀: `python -m pytest python/tests/test_rag_retrieval.py python/tests/test_controlled_agent_evaluation_cli.py -q`
- 현재 로컬 구성에서 `python -m app.agent.controlled_evaluation_cli --live --cases 30 --concurrency 3 --output <path>`는 `controlled evaluation telemetry could not be persisted`로 종료하고 결과 파일을 만들지 않는다.

## 다음 조건

프로젝트 전용 PostgreSQL을 5432에서 정상 기동하고, Flyway `V1`~`V30`, `farmflate` 역할, 승인 코퍼스를 확인한 뒤 동일한 30건 통제 평가를 다시 실행한다. 새 실행 프로필은 전체 시간 예산을 해시에 포함하므로 이전 v3 수치와 직접 비교하지 않는다. 이 문서는 로컬 사전 점검 기록이며 운영 성과나 사용자 품질을 의미하지 않는다.

프로젝트 DB를 대체하지 않는 별도 임시 PostgreSQL에서 공식 공개 문서·현재 실행 프로필을 사용한 통제 벤치마크는 [2026-07-30-current-profile-controlled-benchmark.md](2026-07-30-current-profile-controlled-benchmark.md)에 남겼다. 임시 DB는 측정 후 제거했으며, 이 결과도 `controlled_local` 범위를 벗어나지 않는다.
