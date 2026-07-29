# ADR-001: PostgreSQL이 RAG의 소유자다

- 상태: 채택
- 결정일: 2026-07-29

## 문제

기존 구현은 승인된 지식을 PostgreSQL 전문 검색으로 조회했지만, `pgvector` 컬럼과 HNSW 인덱스는 준비만 되어 있었다. 이를 하이브리드 RAG라고 설명하면 실제 런타임과 포트폴리오 설명이 어긋난다. 반대로 Bedrock Knowledge Bases로 모두 이전하면 현재의 소스 승인, 만료, 문서 버전, 지역 분석과의 인용 정책을 별도 관리형 계층에 다시 구현해야 한다.

## 검토한 선택지

1. Bedrock Knowledge Bases로 검색·적재를 이전한다.
2. PostgreSQL FTS만 유지한다.
3. PostgreSQL이 소스·청크·FTS·pgvector·평가·운영 기록을 소유하고, 임베딩 생성만 교체 가능한 외부 실행기로 둔다.

세 번째를 선택했다.

## 선택 근거

- Farmflate의 신뢰 경계는 단순 문서 검색보다 `승인됨`, `현재 문서`, `만료되지 않음`, `언어 일치` 조건에 있다. 이 조건은 이미 `rag.source`, `rag.document`, `rag.chunk`의 관계 모델에 존재한다.
- PostgreSQL FTS는 임베딩 키가 없어도 동작해 로컬 개발과 장애 상황에서 근거 검색을 중단하지 않는다.
- pgvector가 있을 때 FTS 후보와 벡터 후보를 같은 SQL에서 reciprocal-rank fusion(RRF)으로 결합하면 키워드 일치와 의미 유사도를 함께 활용할 수 있다. 후보 점수의 절대값을 섞지 않아 모델·검색 경로별 점수 범위 차이에도 덜 취약하다.
- Bedrock은 AWS 배포가 확정된 뒤 모델 실행기를 교체하는 선택지로 남긴다. 현 단계에서 저장·검색 계층까지 이전하면 비용, 운영 권한, 로컬 재현성의 부담이 커진다.

## 구현 경계

- 실제 hybrid mode의 전제: pgvector 확장과 V17 스키마, OpenAI 임베딩 키가 모두 준비돼야 한다.
- `RAG_HYBRID_ENABLED`의 기본값은 `false`다. lexical 기준선보다 개선된 평가 결과가 확인된 경우에만 명시적으로 `true`로 바꾼다.
- V17이 pgvector 설치 전 실행됐다면 확장 설치 후 `python -m app.rag.semantic_activation_cli`를 실행한다. 이 명령은 PostgreSQL 함수 `rag.enable_semantic_retrieval()`로 누락된 컬럼·제약·HNSW 인덱스를 멱등적으로 활성화한다.
- 전제가 하나라도 없으면 `lexical` mode로 명시한다. 벡터 컬럼만 있는 상태를 hybrid라고 부르지 않는다.
- 현재 임베딩 어댑터는 이미 답변 생성에 쓰는 OpenAI HTTP API를 사용한다. PostgreSQL은 벡터 생성기가 아니라 저장·인덱싱·검색 엔진이므로, 임베딩 모델 자체는 별도의 실행 주체가 필요하다.
- 임베딩 호출 실패와 pgvector 부재는 전체 적재 실패가 아니라 `rag.ingestion_run.embedding_status`의 `UNAVAILABLE`로 남기고 FTS 적재를 계속한다.
- pgvector가 없는 것이 먼저 확인되면 임베딩 API를 호출하지 않는다. 사용할 수 없는 벡터를 생성하는 비용과 외부 의존성을 피하기 위한 순서다.

## 검증 근거

- 검색 결합 SQL: `python/app/rag/repository.py`의 `search_hybrid`
- 임베딩 적재: `python/app/rag/ingest.py`, `python/app/rag/embeddings.py`
- 운영 스키마: `backend/src/main/resources/db/migration/V20__rag_retrieval_operations.sql`
- 회귀 테스트: `python/tests/test_rag_repository.py`, `python/tests/test_rag_retrieval.py`, `python/tests/test_rag_lifecycle.py`

## 결과와 트레이드오프

- 장점: 현재 데이터 모델의 승인·인용 정책을 그대로 유지하고, 로컬에서 FTS RAG를 재현할 수 있다.
- 비용: pgvector 확장과 임베딩 API의 운영 준비가 있어야 hybrid mode가 활성화된다.
- 의도적으로 하지 않은 일: Bedrock Knowledge Bases 이관, 무단 웹 크롤링, 벡터 검색 실패를 숨기는 다단계 공급자 폴백.
