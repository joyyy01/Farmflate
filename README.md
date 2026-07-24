# 🌾 Farmflate (팜플렛) - 스마트 지역 농사 환경 분석 서비스

[![Vercel Deployment](https://img.shields.io/badge/Vercel-Deployed-000000?style=for-the-badge&logo=vercel&logoColor=white)](https://frontend-smoky-eta-31.vercel.app)
[![React](https://img.shields.io/badge/React-18-61DAFB?style=for-the-badge&logo=react&logoColor=black)](https://react.dev)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.0-3178C6?style=for-the-badge&logo=typescript&logoColor=white)](https://www.typescriptlang.org)
[![Vite](https://img.shields.io/badge/Vite-6.0-646CFF?style=for-the-badge&logo=vite&logoColor=white)](https://vitejs.dev)

> **"내 땅에, 이 작물 심어도 될까요?"**  
> 지역과 작물을 선택하면 토양분석 공공 데이터와 실시간 기상 데이터를 가중치 시뮬레이션 엔진으로 분석해 재배 적합도, 4대 환경 점수 및 농사 위험 요소를 미리 알려주는 모바일 스마트 농업 분석 서비스입니다.

---

## 🌐 라이브 Vercel 서비스 URL

- **Vercel Production Web App:** [https://frontend-smoky-eta-31.vercel.app](https://frontend-smoky-eta-31.vercel.app)

---

## ✨ 핵심 제공 기능

1. **🌾 농사 환경 종합 점수 및 4대 지표 분석 (100점 만점)**
   - 기후 환경, 토양 환경, 자연재해, 재배 환경 등 4대 핵심 분야 가중치 종합 평가
   - SVG 세미-게이지 아크 인터랙션 및 자연스러운 한국어 환경 특징 가이드 제공

2. **📊 추천 작물 TOP 3 적합도 시뮬레이션**
   - 감자, 상추, 사과 등 주요 작물의 토양 pH, 적정 강수량 및 일조시간 부합도 평가

3. **🚨 지역별 핵심 농사 위험요소 안내**
   - 장마철 집중호우, 혹서기 온열 피해 등 맞춤형 주의사항 및 농촌진흥청 공공 농사로 원문 링크 연동

4. **💬 농가 소통 커뮤니티 & 실시간 파일/링크/이미지 첨부**
   - 카테고리별 농가 노하우 공유, 게시글 좋아요, 북마크 저장, 실시간 댓글 및 파일/링크/이미지 첨부 지원

5. **🤖 Farmflate AI 농사 도우미 바텀시트**
   - 작물 재배 및 지역 환경 질의응답이 가능한 글로벌 대화형 AI 도우미 지원

---

## 🛠️ 기술 스택 (Tech Stack)

### Frontend
- **Core**: React 18, TypeScript, Vite
- **Styling**: Vanilla CSS Design System, Framer Motion, Lucide Icons
- **Deployment**: Vercel (SPA Rewrite Support)

### Backend & Database (Optionally Integrated)
- **Primary Backend**: Java 17, Spring Boot 3, Spring Security, JPA/Hibernate
- **AI Engine Backend**: Python 3.11, FastAPI, Uvicorn
- **Database**: PostgreSQL 16

---

## 🚀 로컬 실행 방법 (Local Setup)

### Frontend 실행
```bash
cd frontend
npm install
npm run dev
```
브라우저에서 `http://localhost:5173` 접속

---

## 📄 라이선스
Copyright © 2026 Farmflate. All rights reserved.
