# PostgreSQL RAG·Agent 운영과 측정 절차

## 현재 구현 상태

- 기본 검색: PostgreSQL FTS. `RAG_HYBRID_ENABLED=false`인 기본 환경에서도 승인·현재·만료·언어 조건을 적용한다.
- 선택 검색: pgvector + 임베딩 제공자 + RRF. pgvector 활성화, 운영자 opt-in, 출처별 외부 임베딩 반출 허용이 모두 충족될 때만 `hybrid`로 동작한다.
- Agent: 승인된 화면 컨텍스트와 승인 지식 검색 도구만 호출하며, 인용 검증 뒤에 새 조언을 덧붙이지 않는다.
- 품질 기록: 질문 원문 대신 해시, 검색 경로, 후보 수, 지연 시간, 청크 ID를 PostgreSQL에 남긴다.

## 활성화 전 확인

1. Flyway V16~V23가 적용된 PostgreSQL인지 확인한다.
2. 운영 환경에서는 데이터베이스 이미지·패키지에서 PostgreSQL 버전과 같은 pgvector를 설치한 뒤 `CREATE EXTENSION vector`를 실행한다. 애플리케이션 배포물에 DB 바이너리를 넣지 않는다.
3. 로컬 Windows 개발 환경은 PostgreSQL 18.4용으로 빌드한 pgvector 0.8.5을 사용자 쓰기 가능 extension-control/dynamic-library 경로에 둘 수 있다. 이 우회 경로는 로컬 검증용이며 운영 표준이 아니다.
4. 확장 설치가 V17 이후였다면 다음 명령으로 컬럼·제약·HNSW 인덱스를 활성화한다.

```powershell
cd python
python -m app.rag.semantic_activation_cli
```

5. 승인된 실제 농업 문서를 적재하고 `rag.eval_case`에 대표 질의와 기대 청크/인용을 등록한다. 평가 케이스 없이 hybrid를 켜지 않는다.

## 지식 출처 승인 절차

사용자 채팅은 출처 등록·적재를 호출할 수 없다. 운영자가 먼저 `PENDING`으로 등록하고, 검토자가 사유와 함께 명시적으로 승인해야만 적재가 가능하다. 각 결정은 `rag.source_audit_event`에 남는다.

농업 지식의 첫 후보는 농촌진흥청 농사로의 서비스별 OpenAPI다. 범용 웹 검색·블로그는 출처·갱신·이용 조건을 통제할 수 없으므로 자동 적재하지 않는다. 서비스별 이용 조건, 발행일, 출처 표기는 [ADR-004](adr/004-official-agricultural-source-admission.md)를 따른다.

```powershell
cd python
python -m app.rag.source_governance_cli register --url <canonical-url> --publisher <publisher> --name <source-name> --actor <operator> --reason <registration-reason>
python -m app.rag.source_governance_cli review --url <canonical-url> --action APPROVED --actor <reviewer> --reason <approval-reason>
python -m app.rag.source_governance_cli embedding-egress --url <canonical-url> --allowed true --actor <reviewer> --reason <why-external-embedding-is-necessary>
python -m app.rag.operator_cli --source-url <canonical-url> --source-version <version> --content-file <approved-document> --requested-by <operator>
```

`REJECTED`와 `REVOKED`도 같은 `review` 명령으로 기록한다. 철회된 출처의 청크는 현재 문서 조건을 만족하더라도 검색 후보에서 즉시 제외된다. 외부 임베딩 반출은 기본 거부이며, 공개·승인 자료라도 위 명령으로 목적과 검토자를 남긴 경우에만 시도한다. 반출을 허용하지 않은 자료는 PostgreSQL FTS만 사용한다.

농사로 주간농사정보는 공식 API의 PDF 첨부만 읽는다. API 키는 `NONGSARO_API_KEY`로 서버에만 두고, 먼저 위 URL을 `https://api.nongsaro.go.kr/service/weekFarmInfo/weekFarmInfoList`로 승인한 뒤 실행한다.

```powershell
cd python
python -m app.rag.nongsaro_weekly_ingest_cli --requested-by <operator> --weeks 3
```

`--weeks`는 1~12개 공식 발행물을 수집한다. PDF 텍스트 추출 실패·API 오류·미승인 출처에서는 적재하지 않는다. 각 발행물은 `cntntsNo` 기반 `external_id`로 구분되므로, 새 주차는 이전 주차를 지우지 않고 함께 검색된다. 같은 발행물을 재실행하면 그 발행물만 멱등 갱신하고, 이미 승인된 외부 임베딩을 현재 청크에 갱신한다.

## 측정 순서

1. `RAG_HYBRID_ENABLED=false`로 적재·평가를 실행해 lexical 기준선을 기록한다.

```powershell
cd python
python -m app.rag.evaluate_cli --dataset-key <fixed-question-set> --dataset-version <document-and-question-version> --mode lexical --requested-by <operator>
python -m app.rag.metrics_cli --days 30
```

2. 평가 케이스와 문서 버전을 고정한 뒤 `RAG_HYBRID_ENABLED=true`로 같은 절차를 반복한다. 두 실행 ID를 보관한다.

```powershell
python -m app.rag.evaluate_cli --dataset-key <fixed-question-set> --dataset-version <document-and-question-version> --mode hybrid --requested-by <operator>
python -m app.rag.compare_evaluation_runs_cli --baseline-run <lexical-run-id> --candidate-run <hybrid-run-id>
```

3. 비교기는 동일 세트·버전·평가 origin·코퍼스 SHA-256 지문, 최소 20건, 사례 완료, 오류 없음, recall@k·precision@k·MRR·p95 측정을 검사한다. `MANUAL_REVIEW`는 사람이 환경 변경을 검토하라는 뜻이며 자동 활성화가 아니다.

### 자동 실버 세트

공개·승인된 출처이면서 외부 임베딩 반출이 명시 허용된 경우에만, 현재 청크에서 회귀 감시용 질문을 자동 생성할 수 있다.

```powershell
cd python
python -m app.rag.auto_evaluation_seed_cli --source-url <approved-source-url> --dataset-key <silver-set> --dataset-version <corpus-version> --questions-per-chunk 5
```

생성 질의는 DB에서 `AUTO_GENERATED`로 표기된다. 이는 사람이 검토하지 않아도 lexical/hybrid의 상대 회귀를 빠르게 찾는 실버 평가이며, 실제 사용자 품질·도메인 정답·승격 근거를 대신하지 않는다. 생성 또는 비교 결과가 좋아도 `RAG_HYBRID_ENABLED`를 자동 변경하지 않는다.

## 이력서·포트폴리오 수치 기록 양식

다음 네 값과 측정 조건을 함께 기록한다. 숫자만 떼어 쓰면 재현성과 신뢰성이 떨어진다.

| 항목 | 기록할 조건 |
| --- | --- |
| recall@k | 평가 케이스 수, k, 문서 버전, lexical/hybrid 모드 |
| citation precision | 기대 인용 정의, 평가 케이스 수 |
| MRR | 첫 관련 근거 순위, 평가 케이스 수 |
| p50/p95 지연 시간 | 측정 기간, 요청 수, 검색 모드 |
| 적재 상태 | 문서 수, 청크 수, `READY`/`UNAVAILABLE` 비율, 코퍼스 지문 |

예: “대표 질의 N건과 문서 버전 V에서 lexical/hybrid를 비교해 recall@k와 p95를 측정했고, 인용 정확도를 유지한 경우에만 hybrid를 활성화했다.” 실제 N·V·결과 값은 `metrics_cli` 출력과 평가 결과에서 확인한 뒤 채운다.

## 의도적으로 처리한 예외

- pgvector 미설치: FTS 유지, semantic 상태는 `UNAVAILABLE`, 임베딩 API 호출은 생략.
- 출처별 외부 임베딩 반출 미승인: 제공자 호출과 vector 저장을 모두 생략하고 `POLICY_DENIED`를 적재 이력에 남김.
- 임베딩 API 실패: 전체 문서 적재를 중단하지 않고 FTS 적재와 실패 사유 기록.
- 만료·미승인·과거 문서: 검색 후보에서 제외.
- 인용 없는 완료 답변: `needs_context`로 차단.
- 검색 추적: 질문 원문·비밀 값 대신 해시와 식별자만 저장.
- hybrid가 기준선을 이기지 못함: 기능을 끄고 FTS를 유지.
- pgvector를 런타임에 설치·활성화한 뒤: capability cache는 최대 60초 뒤 다시 확인하므로 Python 서버를 재기동하지 않아도 새 검색 경로를 감지한다.
