# PostgreSQL RAG·Agent 고도화 실행 계획

## 전제

- PostgreSQL은 승인 지식, 청크, 전문 검색, pgvector, 평가와 추적을 보관하는 시스템 오브 레코드다.
- Bedrock Knowledge Bases와 Bedrock Agents Classic은 도입하지 않는다.
- pgvector가 실제 설치된 환경에서만 semantic lane을 활성화한다. 미설치 환경은 lexical-only로 명시하며 하이브리드라고 부르지 않는다.
- 현재 OpenAI API는 답변 생성에 이미 사용 중이다. 임베딩은 같은 API를 선택적으로 사용할 수 있으나, 키·pgvector 중 하나라도 없으면 RAG 자체는 PostgreSQL FTS로 계속 동작한다.

## 작업

1. 검색 계약을 확장한다.
   - OpenAI Embeddings 호출을 격리한 작은 포트를 추가한다.
   - lexical 후보와 semantic 후보를 PostgreSQL에서 각각 구하고 reciprocal-rank fusion으로 결합한다.
   - 결과에 retrieval mode·지연 시간·후보 수를 포함한다.

2. 적재와 운영 진단을 확장한다.
   - 운영자 적재에서 청크 임베딩을 배치 생성해 함께 저장한다.
   - 임베딩 실패는 lexical-only 적재로 남기고, 원인을 적재 실행에 기록한다.
   - 검색 실행은 질문 원문 대신 SHA-256만 저장해 품질·성능을 추적한다.

3. 평가를 실제 실행 가능하게 만든다.
   - 활성 평가 케이스를 읽어 recall@k·citation precision·latency를 저장하는 evaluator와 CLI를 추가한다.
   - lexical/hybrid 경로를 평가 결과에서 구분한다.
   - 경로별 recall@k, citation precision, p50/p95 지연 시간을 집계해 하이브리드 활성화 여부를 수치로 결정한다.

4. Agent의 근거 경계를 강화한다.
   - 도구가 남긴 검색 진단을 Agent trace에 전달한다.
   - 검증 후 응답 포맷팅이 화면 컨텍스트의 새 조언을 덧붙이지 못하게 제거한다.
   - Agent 실패 시 기존 대규모 규칙 기반 채팅으로 우회하지 않고, 근거 부족 상태를 일관되게 반환한다.

5. 핵심 회귀를 검증한다.
   - hybrid SQL·적재 임베딩·평가 측정·근거 보존 응답만 테스트 우선으로 추가한다.
   - Python RAG/Agent 테스트와 Flyway migration 테스트를 실행한다.

## 완료 정의

- pgvector가 설치된 PostgreSQL에서 실제 벡터 검색과 RRF 결합 SQL이 실행 가능하다.
- pgvector 또는 임베딩 키가 없을 때 lexical-only 상태와 원인이 응답/운영 진단에 남는다.
- 하이브리드 검색은 평가 기준을 충족하기 전까지 기본 활성화되지 않는다.
- Agent 완료 응답의 모든 문장은 도구 인용으로 검증된 주장만 사용한다.
- 평가 결과와 검색 추적은 개인정보·원문 질문·비밀 값 없이 PostgreSQL에 저장된다.
