---
name: project_live_coediting_epic
description: 라이브 코-에디팅(#16) 에픽 — Yjs 기반 구글독스식 실시간 동시편집. slip·주문 머지 완료, 견적·회계·결재·배차 잔여. §7 collab(presence/comment)과 별개 에픽.
metadata:
  type: project
---

🚨 2026-06-30 신설(에픽 추적 부재 정정 — 재조사 발견). 개발책임자 지시 **라이브 코-에디팅 에픽(#16, 내부 태그·GH 이슈 아님)**: 구글 독스/시트식 **실시간 동시편집**(Yjs CRDT + SSE relay + awareness 커서/셀렉트). §7 전역 협업(presence 보기 + comment + 1인 수정완료, [[project_global_collab_epic]])과 **별개 에픽**(혼동 금지 — soft-lock #672는 파킹/피벗).

## 목표
각 전표/문서에 라이브 커서·셀 셀렉트·실시간 편집 + A~D(①단일색상 presence=coedit=audit, BE PresenceColor 단일소스 ②상태의존 카운트 ③로그=첫 작성 이후 항상 ④레드라인 재귀). **6문서 롤아웃**: slip·견적·배차·회계전표·주문·그룹웨어결재.

## 진행 (2026-06-30 기준)
✅ S1 토대(#673)·S2a slip 전체폼(#674)·S2b 버전로그(#675)·S2c 상태의존카운트(#676)·S2d-1 헤더 레드라인(#677)·S2d-1b 라인 레드라인(#678)·S2d-2 라이브 변경 하이라이트(#679)·S3-0 relay/provider 공용화(#680, shared CollabCoeditService)·**S3-1 주문 메모 coedit(#681, squash 04e2ff205)**.
🔄 잔여: **S3-2 견적 → S3-3 회계전표 → S3-4 그룹웨어 결재 → S3-5 배차** (각 1차=단일 '협업 메모' 필드 저위험, 2차=폼 전체 셀 후속). 6문서 중 slip·주문 ✅ / 4문서 잔여. 완결=#16 종료 → 그 후 #17 단가변동.

## S3-1 롤아웃 패턴 (S3-2~ 템플릿)
- **BE**: `{Doc}CollabController`(`/api/v1/{도메인}/{id}/collab`)에 coedit 3엔드포인트(`GET /coedit`·`POST /coedit/update`·`POST /coedit/awareness`) — `resolve{Id}` UUID 키(기존 stream broker 채널과 일치), shared `CollabCoeditService` 자동주입(`@ConditionalOnBean(RealtimeBroker)`+`:shared:collab-core` 의존), `@RequirePermission` read/write page-code, DTO 3종 로컬미러. **Flyway 0**(in-memory relay).
- **FE**: `{Doc}CollaborationPanel`에 `CollaborativeTextField`('협업 메모', basePath=`/{도메인}/{encodeURIComponent(id)}`, fieldName="memo", readOnly=!canWrite). 공용 `CollaborativeTextField`는 providerStatus(loading/ready/failed) 입력잠금+실패 role=alert 내장(S3-1 Codex 라운드).
- **워크플로**: 정찰→spec→plan→조기PR→codex(danger-full-access) 구현→순차 듀얼리뷰 0수렴(Opus 5-agent↔Codex)→라이브QA(mock 회귀+실 relay round-trip)→PM종합→CI→squash 머지+dev-report.

## 리스크/주의 (S3-1 회귀 박제)
- 라우트 `:id`가 'new' 매칭 → 미저장 문서 coedit 오마운트(S3-1 T04 pageerror) → 패널 마운트 게이트(`query.data && id 유효`) 필수.
- `mock.ts` coedit 핸들러 누락 시 Playwright `**/{도메인}**` 글롭 fall-through → undefined → pageerror. 도메인별 mock coedit 3핸들러 추가 필수.
- redline 일반화(취소선+레드라인 재귀)=revision 보유 문서(주문·견적) 독립 하위트랙.
- **estimate-app(EJS/Node)는 React 미적용** → 견적 coedit 대상은 데스크톱 EstimateForm/QuoteView일 수 있음(S3-2 정찰 결론 따름).

설계 spec: `docs/superpowers/specs/2026-06-30-live-coediting-design.md`. 관련: [[project_global_collab_epic]](§7, 별개).
