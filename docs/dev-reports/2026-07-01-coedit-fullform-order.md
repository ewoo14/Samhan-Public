# 협업 full-form — 주문(partner-order) 전체폼 coedit (트랙B 슬1)

## 목적
개발책임자 정정(원 지시=전표 폼 전체 동시편집, slip처럼)에 따른 5문서 full-form 롤아웃 1번째. 주문 수정모달을 slip full-form 패턴으로 coedit화(메모 단일필드→전체폼).

## 구현 (FE 전용·BE 0·공유 provider 무수정)
- `SalesPartnerOrderDetailPage` 수정모달: `createDocCoeditProvider`(Y.Map header[memo char-CRDT] + Y.Array items) open/close 생성·파괴 + seed + `subscribeDoc` 역동기화 + provider 실패 평문 폴백. 헤더 partnerCode/dueDate/memo + 라인 productName/modelCode/quantity/deliveryPrice/remark → `CollaborativeSlipInput`. categoryKey(Select)=평문(슬1). 저장=기존 `updatePartnerOrder` PUT 재사용. `canCollabEdit`(COLLAB_LOCKED) 게이트. 기존 '협업 메모' 패널 별개 유지.

## 듀얼리뷰 (0수렴 — 양방향 상호 정정)
- **Opus 5-agent**: BLOCKING 2 — ① awareness 블리드(CollaborativeTextField editFieldPath `header.`→`field.` 네임스페이스, 공유: 패널 메모 presence가 모달 header.memo 셀에 블리드 차단, slip+전 패널 동시 해소) ② stale 스냅샷 corruption(provider 라인수≠서버 시 server-wins re-seed → categoryKey 'homemulti' 오염·라인 유실 차단). + HIGH(remark null 보존·coeditPending 표시)·MED(categoryKey disabled·test).
- **Codex 라운드 ↔ Opus Round-C ↔ Codex Round D**: re-seed 가드 `!==` vs `<` — Codex가 동시 라인추가 클로버 우려(`<` 제안) → Opus Round-C가 슬1 라인추가 UI 부재로 `<`는 provider>server stale BLOCKING 재오픈 역지적(`!==` 복원) → Codex Round D 0수렴 확정.

## 검증
typecheck PASS · collab vitest 33/33(5 files, awareness editFieldPath 회귀 0) · QA 2세션 실 스샷 8컷(A/B 모달·A편집→B; testid 변경 자동단언 일부 막힘 정직) · CI green. PR #689 squash `75a967d15`.

## 후속
- **5문서 full-form 롤아웃 진행**: slip ✅·주문 ✅ / 견적·회계·결재·배차 잔여. + 트랙A slip 하드닝(라인 add/reorder CRDT[lineId·Y.Array·fractional order-key, Y.Array.move 부재]·셀 char-CRDT[applyDelta]).
- defer: 저장 충돌 정합(낙관락↔라이브, 후속 슬라이스)·채널분리 리렌더(BE)·BE CONFIRMING guard(pre-existing)·badge·토큰.
- ⚠️ full-form 5문서 충족 전 에픽 종결 선언 금지([[feedback_epic_scope_no_narrowing]]).
