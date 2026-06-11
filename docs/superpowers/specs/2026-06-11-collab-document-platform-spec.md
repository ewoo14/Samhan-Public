# SPEC — 전역 협업 문서 플랫폼 (실시간 동시조회 · 코멘트 · 수정제안 · 수정이력/회귀)

> 2026-06-11 개발책임자 야간 지시: 배차 슬라이스와 **함께** "전체 전표 및 화면에 대한 협업 플랫폼 기술 슬라이스"까지 적용. 배차 보드 에픽 spec(`2026-06-11-dispatch-board-enhancement-spec.md`) §7 을 **독립 아키텍처 spec 으로 승격**. Google Docs 협업 3요소(실시간 동시편집 · 코멘트 · 수정제안 모드)를 **모든 전표/문서 단위**(입출고전표 · 회계전표 · 배차 · 주문 · 견적 …)가 재사용하는 공통 서브시스템으로 설계.

## 0. 범위·전략
- **범위**: 작성 완료된 모든 전표/문서에 (a) 실시간 동시조회 (b) 코멘트 (c) 수정제안(suggestion) 모드 (d) 수정 카운트·수정자·로그·회귀(revert) 적용.
- **전략(빅뱅 금지)**: ① 공유 서브시스템 설계(본 spec) → ② **레퍼런스 구현 1종 = 배차(dispatch)** → ③ 단계 롤아웃: 입출고전표 → 회계전표 → 주문 → 견적.
- **기존 자산 승격**: slip 도메인 revision/audit-log/restore(입출고전표 회귀) + SSE publish(`*:changed` afterCommit) + (있으면)코멘트 인프라 → 공통 추상으로.

## 1. 도메인 모델 (공유 `collab` 추상)
문서 식별 = `(documentType, documentId)`. `documentType` enum: `SLIP_OUTBOUND` · `SLIP_INBOUND` · `ACCOUNTING_VOUCHER` · `DISPATCH_TASK` · `PARTNER_ORDER` · `ESTIMATE` …(확장).

### 1-1. 코멘트 — `CollabComment`
- 필드: id, documentType, documentId, anchor(nullable — 문서 전체/특정 필드/특정 행), authorUserId, authorName(표시), body, parentId(스레드), status(OPEN/RESOLVED), BaseEntity 7 audit + soft delete.
- 행위: 작성 · 답글 · 해결(resolve) · 삭제(soft).

### 1-2. 수정제안 — `CollabSuggestion`
- **수정 = 기본적으로 "제안"** (즉시 확정 아님). 제안 → 수락/거절.
- 필드: id, documentType, documentId, proposerUserId, proposerName, **changeSet**(JSON: 필드 경로→{before, after}), reason(nullable), status(PROPOSED/ACCEPTED/REJECTED/WITHDRAWN), decidedByUserId, decidedAt, BaseEntity.
- 행위: 제안 · 수락(→ 도메인에 changeSet 적용 + revision 생성) · 거절(사유) · 철회.
- **권한**: 제안 = 편집 권한자. 수락/거절 = 문서 소유 도메인의 승인 권한자(배차=배차담당자, 회계=회계 등).

### 1-3. 수정이력/회귀 — `CollabRevision`
- 필드: id, documentType, documentId, revisionNo(문서별 1씩 증가 = **수정 카운트**), authorUserId, authorName, diff(JSON before/after), sourceSuggestionId(nullable — 제안 유래), createdAt(불변).
- 행위: 적용 시 revision append(카운트 증가) · **revert(특정 revision 으로 회귀 = 역 changeSet 적용 + 새 revision 기록)**.
- 입출고전표 기존 revision/restore 패턴을 본 추상으로 일반화(중복 제거).

### 1-4. 실시간 — `CollabRealtime`(SSE)
- 채널 = `collab:{documentType}:{documentId}`. afterCommit publish 이벤트: `commented` · `suggestion.proposed/accepted/rejected` · `revised` · `reverted` · `presence`.
- **presence**(동시조회자): 페이지 진입 시 heartbeat, 이탈 시 제거. 같은 문서를 보는 사용자 목록 표시.
- 기존 SSE 인프라(`product:catalog:changed` 등) 재사용 — afterCommit publisher + FE EventSource 구독.

## 2. 모듈 구조
### BE
- **`shared/collab-core`**(신규 공유 모듈): 위 4 엔티티 + 리포지토리 + 서비스(CommentService/SuggestionService/RevisionService/RealtimePublisher) + `DocumentCollaborationPort`(도메인 어댑터 인터페이스: `applyChangeSet(documentId, changeSet)` · `loadSnapshot(documentId)` · `canPropose/canDecide(userId)`).
- **per-service 통합**: 각 도메인 서비스(slip/accounting/dispatch/…)가 `DocumentCollaborationPort` 구현(자기 엔티티에 changeSet 적용·스냅샷·권한) + collab-core 빈 등록. **opt-in**(@ConditionalOnProperty 또는 명시 등록 — [[preauth-migration-lessons]] 무관 서비스 회귀 방지).
- **API**(공통 컨트롤러, gateway no-strip 또는 도메인별 prefix): `/{domain}/{docId}/comments` · `/suggestions`(+accept/reject) · `/revisions`(+revert) · `/stream`(SSE).
- **DB**: collab 전용 테이블(comment/suggestion/revision) — 서비스별 DB-per-service 원칙 상 **각 서비스 스키마에 collab 테이블 생성**(공유 라이브러리가 Flyway 마이그레이션 제공) 또는 collab 전용 서비스. → **결정 후보 A**(아래 미결).

### FE (`@samhan/design-system` 또는 `clients/desktop` 공유 렌더 모듈)
- 공통 컴포넌트: `CommentThread` · `SuggestionOverlay`(제안 diff 표시 + 수락/거절) · `RevisionHistory`(카운트·수정자·revert) · `PresenceBar`(동시조회자) · `useCollabRealtime(documentType, documentId)` 훅(EventSource).
- 각 문서 화면(전표 상세 모달 등)이 이 컴포넌트를 **문서 단위로** 마운트.

## 3. 수정제안 UX (Google Docs 모델)
- 편집 모드 진입 → 필드 수정 시 **즉시 확정 아님**: changeSet 누적 → "제안" 제출 → 제안 목록에 PROPOSED.
- 소유 도메인 승인자: 제안 diff(before→after) 확인 → 수락(적용+revision) / 거절(사유).
- 코멘트로 제안·필드 논의. 실시간으로 다른 조회자에게 제안/코멘트/적용이 반영.

## 4. 레퍼런스 구현 = 배차(dispatch)
- 배차 에픽 E3(수정이력/카운트/회귀) · E5(실시간 협업) · E4(취소 연동)를 **본 collab-core 위에 구현**(중복 인프라 금지).
- DispatchTask = `documentType=DISPATCH_TASK`. 차량그룹/전표/기사 구성 변경 = changeSet. 전송완료 후 수정 = 제안 → 수락 시 arologis 재발송.

## 5. 단계 롤아웃 슬라이스
| # | 슬라이스 | 핵심 |
|---|---|---|
| C0 | **collab-core 설계+골격** | 4 엔티티·포트·서비스·SSE·공유 FE 컴포넌트(미배선). |
| C1 | **레퍼런스=배차** | DispatchTask 통합(E3/E4/E5). 실시간·제안·코멘트·회귀 실동작. |
| C2 | **입출고전표** | slip revision/restore → collab-core 흡수 + 코멘트·제안·실시간. |
| C3 | **회계전표** | accounting voucher 통합. |
| C4 | **주문/견적** | partner-order · estimate 통합. |

## 6. 미결(개발책임자 아침 검토 🚩 — 야간엔 PM 기본값으로 진행)
1. **collab 테이블 배치**: (A) 각 서비스 DB 스키마에 collab 테이블(라이브러리 Flyway) — DB-per-service 유지, 조인 가능. (B) collab 전용 서비스 — 격리되나 cross-service 조회 복잡. → **PM 야간 기본값 = (A)**(기존 DB-per-service·조인 용이·점진 통합 친화). 아침 확정.
2. **changeSet 표현**: JSON path→{before,after} (PM 기본값) vs 도메인 전용 DTO. → 기본값 = 범용 JSON(어댑터가 해석).
3. **권한 모델**: 제안=편집권한 / 수락=도메인 승인권한 매핑(배차=dispatch.board UPDATE, 회계=accounting.* 등) — 도메인별 확정 필요. 야간엔 배차 기준으로 진행.
4. **presence 정밀도**: 단순 "조회 중 사용자 목록"(야간 기본) vs 커서/필드 레벨(후속).

---

> 본 spec = §7 승격본. 배차 에픽 spec 과 상호참조. 레퍼런스(C1=배차) 후 C2~C4 순차. 야간 자율: C0 설계골격 → C1 배차 레퍼런스 우선, 이후 단계 롤아웃. 모든 단계 [[codex-implements-claude-reviews]]·[[no-fake-data-ever]]·[[qa-docker-real-test]]·다모델 리뷰 0-error/0-skip 후 머지.
