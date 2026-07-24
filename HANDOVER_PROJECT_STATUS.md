# 🌾 Farmflate (팜플렛) 프로젝트 완전 통합 인수인계서 (Handover Report)

> **문서 작성 일시:** 2026년 7월 24일  
> **대상:** 후속 작업을 진행할 모든 AI 에이전트 및 개발자  
> **프로젝트 라이브 배포 URL:** [https://frontend-smoky-eta-31.vercel.app](https://frontend-smoky-eta-31.vercel.app)  
> **깃허브 원격 저장소:** [https://github.com/joyyy01/Farmflate.git](https://github.com/joyyy01/Farmflate.git)  

---

## 1. 📌 프로젝트 개요 및 비전

**Farmflate(팜플렛)** 은 "내 땅에, 이 작물 심어도 될까요?"라는 농업인 및 귀농인의 핵심 질문에 답하기 위해 개발된 **스마트 모바일 웹 앱 서비스**입니다.  
농촌진흥청(흙람보) 토양 분석 데이터와 기상청 단기예보 실시간 데이터를 바탕으로 지역별 농사 적합도(100점 만점), 4대 환경 지표(기후, 토양, 자연재해, 재배), 추천 작물 TOP 3 및 핵심 농사 위험 요소를 시뮬레이션해 줍니다.

---

## 2. 🏗️ 전체 시스템 아키텍처 및 연동 현황

```mermaid
graph TD
    User["📱 사용자 브라우저 (Vite React SPA)"] -->|HTTPS| Vercel["⚡ Vercel Cloud (Live Frontend)"]
    User -->|REST API (Port 8080)| SpringBoot["🍃 Spring Boot 3 Backend"]
    User -->|FastAPI (Port 8000)| PythonAI["🐍 Python FastAPI (AI Chat)"]
    SpringBoot -->|JPA / Hibernate| Postgres["🐘 PostgreSQL 16 DB"]
    SpringBoot -->|Public Data API| Nongsaro["🌾 농촌진흥청 & 기상청 API"]
```

### 1) 프론트엔드 (Frontend)
- **위치:** `frontend/`
- **기술 스택:** React 18, TypeScript, Vite, Vanilla CSS, Framer Motion, Lucide Icons
- **배포:** Vercel Production (`https://frontend-smoky-eta-31.vercel.app`)

### 2) 메인 백엔드 (Spring Boot)
- **위치:** `backend/`
- **기술 스택:** Java 17, Spring Boot 3.4.1, Spring Security, Spring Data JPA
- **데이터베이스:** PostgreSQL 16 (`jdbc:postgresql://localhost:5432/farmflate`)
- **주요 기능:** 카카오 OAuth2 인증, 지역 리포트 저장/조회, 커뮤니티 게시글/댓글 REST API, 마이 밭 저장 API

### 3) AI 보조 백엔드 (FastAPI)
- **위치:** `app/`
- **기술 스택:** Python 3.11, FastAPI, Uvicorn
- **주요 기능:** AI 농사 대화형 바텀시트 질의응답 (`POST /api/v1/chat/`)

---

## 3. 🔗 연동 완료 기능 및 정상 동작 현황 (Real Data vs Fallback)

### ✅ 1. 실 백엔드 API 100% 연동 완료 항목
| 기능 영역 | API 엔드포인트 | 수신/발신 데이터 및 동작 |
| :--- | :--- | :--- |
| **회원 정보 Sync** | `GET /api/users/me` | 카카오 OAuth 인증 사용자 프로필(`displayName`, `email`, `role`) 반환 |
| **메인 홈 대시보드** | `GET /api/home` | 날씨 데이터, 오늘 해야할 조치사항, 최근 지역 분석 상태 수신 |
| **지역 분석 생성** | `POST /api/regions/analysis` | 시도/시군구 코드 기반 지역 환경 가중치 분석 수행 및 `analysisId` 반환 |
| **지역 리포트 조회** | `GET /api/regions/reports/{id}`| 4대 지표 점수, 추천 작물 TOP 3, 자연재해 위험요인 수신 |
| **시도/시군구 조회** | `GET /api/regions/sidos` | 전국 17개 시도 및 하위 시군구 목록 수신 (`@JsonInclude(NON_NULL)` 적용으로 null 필드 제거) |
| **커뮤니티 게시글 CRUD**| `GET/POST /api/community/posts` | 실제 PostgreSQL DB 게시글 연동, 3종 첨부파일(파일/링크/이미지) 처리 |
| **게시글 좋아요 & 댓글**| `POST /api/community/posts/{id}/like`<br>`POST /api/community/posts/{id}/comments` | 게시글 좋아요 및 댓글 DB 저장 |
| **마이 밭 등록/조회** | `GET/POST /api/farms` | 등록된 내 밭 목록 DB 저장 및 조회 |

### 🛡️ 2. 오프라인 & 개발 모드 안전 폴백 (Stale-While-Revalidate)
* **0ms 지연 새로고침 (Flash-Free Refresh):**
  * `App.tsx`에서 `localStorage` 기반 Stale-While-Revalidate 패턴을 적용하여, F5 새로고침 시 1초 빈 화면 깜빡임 없이 즉시 기존 사용자 상태 렌더링.
* **백엔드 오프라인 시 클라이언트 엔진 자동 가동:**
  * 백엔드 서버(Port 8080)가 켜져 있지 않을 경우, 프론트엔드의 `farmEngine.ts`가 가동되어 15개 모든 화면 간 자유로운 라우팅과 시뮬레이션 가능.
* **로그아웃 이중 완충 리셋 (`handleLogout`):**
  * 로그아웃 시 `localStorage.clear()`와 동시에 리액트 메모리 상태(`userName = '사용자님'`, `homeData = null`, `posts = []`)를 완전히 리셋하여 이전 계정 정보 노출 차단.

---

## 4. 📁 핵심 코드 파일별 수정 및 구현 상세

### 1) Frontend (`frontend/src/`)
* **[App.tsx](file:///C:/Users/4987k/Downloads/ai-workspace/frontend/src/App.tsx)**
  * 15개 화면 라우팅 관리, `safeSetViewStep` 라우팅 가드, `handleLogout` 완벽 초기화, 비동기 백엔드 API 연동.
  * 하드코딩된 개인 실명 폴백 제거 및 중립적 기본값(`'사용자님'`) 적용.
* **[MainDashboardView.tsx](file:///C:/Users/4987k/Downloads/ai-workspace/frontend/src/components/farmflate/MainDashboardView.tsx)**
  * 디자인 보완: 기상 카드 줄바꿈 방지(`whiteSpace: 'nowrap'`), 물방울 마스코트 텍스트 겹침 방지 여백(`paddingRight: 80px`), 단일 행 기상 지표 flex 배치.
* **[CommunityListView.tsx](file:///C:/Users/4987k/Downloads/ai-workspace/frontend/src/components/farmflate/CommunityListView.tsx)** & **[CommunityCreatePostView.tsx](file:///C:/Users/4987k/Downloads/ai-workspace/frontend/src/components/farmflate/CommunityCreatePostView.tsx)**
  * 로그인 사용자용 목데이터(Mock Posts) 전면 제거 ➔ PostgreSQL DB 실 데이터 노출.
  * 툴바 3종 첨부 기능 구현: **[📁 파일]** 로컬 파일 업로드, **[🔗 링크]** 웹 URL 링크 등록, **[🖼️ 이미지]** 썸네일 미리보기 태그 지원.
* **[services/api.ts](file:///C:/Users/4987k/Downloads/ai-workspace/frontend/src/services/api.ts)**
  * `handleAuthError` 수정: 비참명 API 에러 시 `window.location.href = '/?view=landing'`로 강제 튕기던 버그 제거 (댓글/좋아요 작성 시 홈 이동 현상 해결).
* **[vercel.json](file:///C:/Users/4987k/Downloads/ai-workspace/frontend/vercel.json)**
  * Vercel 프로덕션 배포용 SPA Rewrite 라우팅 규칙 및 Vite output Directory (`dist`) 설정.

### 2) Backend (`backend/src/main/java/com/example/aiworkspace/`)
* **[application.yml](file:///C:/Users/4987k/Downloads/ai-workspace/backend/src/main/resources/application.yml)**
  * `spring.jackson.default-property-inclusion: non_null` 글로벌 설정으로 JSON 응답 시 불필요한 `null` 필드 전면 예방.
* **[SecurityConfig.java](file:///C:/Users/4987k/Downloads/ai-workspace/backend/src/main/java/com/example/aiworkspace/security/SecurityConfig.java)**
  * Public REST API 경로 (`/api/home`, `/api/community/**`, `/api/farms/**`, `/api/users/**`) `permitAll` 허용.
* **DTO Models** (`RegionDto.java`, `HomeResponseDto.java`, `ApiResponseDto.java`, `RegionReportResponseDto.java`)
  * `@JsonInclude(JsonInclude.Include.NON_NULL)` 어노테이션 적용으로 깔끔한 JSON 응답 보장.

---

## 5. 🚨 주의사항 및 후속 LLM 에이전트를 위한 지침

1. **개발 모드 주석 제거 가이드 (`// [TEMP DEV FALLBACK - REMOVE IN PRODUCTION]`)**
   - 코드 곳곳에 `// [TEMP DEV FALLBACK - REMOVE IN PRODUCTION]` 표시가 붙어 있습니다.
   - 추후 정식 운영 서비스를 출시할 때는 해당 태그가 달린 가드 코드를 주석 해제하여 엄격한 토큰 인증 리다이렉트를 가동하면 됩니다.

2. **개인 정보 보존 규칙**
   - 소스코드 및 커밋 메시지에 실명이나 특정 사용자의 개인정보를 하드코딩하지 않도록 주의해 주세요.

3. **로컬 서버 실행 명령어 모음**
   - **프론트엔드 (Vite):** `cd frontend && npm run dev` (Port 5173)
   - **스프링 부트 백엔드:** `cd backend && .\gradlew.bat bootRun` (Port 8080)
   - **FastAPI AI 서버:** `python -m uvicorn app.main:app --host 0.0.0.0 --port 8000` (Port 8000)
   - **PostgreSQL 서버:** `& "C:\Program Files\Parakeet\PostgreSQL\pgsql\bin\postgres.exe" -D "C:\Users\4987k\postgres_data"` (Port 5432)

---
*이 인수인계 문서는 후속 작업자가 맥락 손실 없이 바로 프로젝트를 파악하고 발전시킬 수 있도록 완벽하게 작성되었습니다.*
