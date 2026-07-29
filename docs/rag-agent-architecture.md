# PostgreSQL 기반 RAG·Agent 구현 기준

Farmflate AI 도우미는 화면에 이미 권한이 부여된 지역 분석·밭 정보를 우선 사용하고, 운영자가 승인한 농업 지식만 보조 근거로 검색한다. PostgreSQL은 소스 승인 상태, 문서 버전, 검색 인덱스, 품질 평가, 검색 추적을 보관하는 시스템 오브 레코드다.

설계 선택의 배경과 수치 기반 활성화 기준은 [ADR-001](adr/001-postgresql-owned-rag.md), [ADR-002](adr/002-evidence-bounded-agent.md), [ADR-003](adr/003-metric-gated-hybrid-retrieval.md)에 기록한다.

## 검색

- 모든 후보에는 승인됨, 현재 버전, 만료되지 않음, 언어 일치 조건을 동일하게 적용한다.
- 출처는 운영자 등록(`PENDING`)과 별도 검토(`APPROVED`/`REJECTED`/`REVOKED`)를 거치며, 이유와 행위자는 `rag.source_audit_event`에 남긴다.
- pgvector와 임베딩이 모두 준비된 경우 PostgreSQL FTS와 벡터 검색의 후보를 reciprocal-rank fusion으로 결합한다.
- 한 경로만 가능하면 해당 경로를 명시한다. 벡터 컬럼만 존재하는 상태는 hybrid retrieval이 아니다.
- 모델에는 출처·청크 식별자·본문만 제공한다. 검색 점수는 근거 강도로 해석하지 않는다.

## Agent

`계획 → 권한 있는 컨텍스트/지식 검색 → 인용 검증 → 구조화 응답`의 읽기 전용 흐름을 사용한다. 완료 응답의 각 판단은 도구가 반환한 인용 식별자와 연결돼야 한다. 섹션·행동 목록은 초기 출력 계약으로 유도하지만, 형식만 고치기 위해 별도 LLM 호출을 추가하지 않는다. 완료 여부는 표시 모양이 아니라 근거·인용 결속으로 판단한다.

## 운영·평가

질문 원문이 아니라 SHA-256, 검색 경로, 후보 수, 지연 시간, 결과 청크 식별자만 PostgreSQL에 기록한다. 활성 평가 케이스에 대해 recall@k, citation precision, latency를 저장한다. 평가 결과는 품질 판단의 근거이며, 현재 구현되지 않은 기능을 주장하는 근거로 사용하지 않는다.

pgvector capability는 짧은 TTL로 다시 확인한다. 따라서 확장과 HNSW를 운영 중 활성화했을 때도 장기 실행 Python 프로세스가 이전의 lexical-only 상태에 고정되지 않는다.
