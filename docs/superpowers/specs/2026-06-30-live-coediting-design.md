# 라이브 코-에디팅 (구글 독스식) — 설계

> 2026-06-30 개발책임자 정정: 협업 = **라이브 커서·라이브 셀렉트·실시간 편집**(구글 독스식). 기존 §7 collab(댓글·수정완료·presence) + 필드 soft-lock(#672 파킹)과는 별개의 **동시 편집(concurrent editing)** 에픽. 권장방향 자율 설계.

## 1. 권장 아키텍처 (PM 결정)
- **CRDT = Yjs**: 동시 편집 충돌을 자동 병합(OT 대비 커스텀 변환 로직 불요·성숙). `Y.Text` 가 문서 모델.
- **awareness = y-protocols/awareness**: 라이브 **커서 위치 + 셀렉트 범위 + 사용자 색/이름** 을 Yjs 가 기본 제공 → 별도 모델 불요.
- **전송 = 기존 RealtimeBroker(SSE) + REST relay** (신규 WebSocket 서버 도입 회피):
  - 로컬 변경 → Yjs update(Uint8Array)→base64 → `POST /collab/coedit/{docId}/update`.
  - BE = update 를 in-memory 병합 상태에 적용 + `RealtimeBroker.publish(docId, "coedit:update", base64)` 로 SSE 브로드캐스트.
  - 원격 클라이언트 = 기존 `/collab/stream` SSE 로 update 수신 → `Y.applyUpdate`.
  - awareness 동일 채널(`coedit:awareness`).
  - 초기 상태 = `GET /collab/coedit/{docId}` → 현재 병합 상태(base64) → `Y.applyUpdate`.
  - ⚠️ SSE 단방향 + REST 라 WebSocket 대비 지연 있으나 폼 텍스트 필드엔 충분. 고빈도 입력은 클라이언트 debounce(예: 200ms) + Yjs update 병합.
- **surface = 협업 텍스트 필드**: 폼의 자유 텍스트(메모/비고/노트)부터. 구조적 필드(수량/단가)는 비대상(별 도메인 검증).

## 2. 슬라이스
- **S1 (본 슬라이스)**: Yjs 협업 텍스트 필드 컴포넌트(`CollaborativeTextField`) + BE coedit relay(in-memory 병합·SSE 브로드캐스트·초기상태) + 1개 surface 통합(예: 슬립 협업 메모). 라이브 커서·셀렉트·실시간 병합 동작. 라이브 QA = 2세션 동시 타이핑 캡처.
- **S2+**: 다surface 롤아웃 · 서버 persist(재접속/이력 복원) · 대량 동시성·GC · presence 통합(누가 편집 중 + 커서).

## 3. 기술 결정/주의
- 의존성: `yjs` + `y-protocols`(awareness). 에디터 바인딩 = textarea ↔ `Y.Text` 수동 바인딩(커서 offset 보정) 또는 경량 라이브러리. design-system 입력 컴포넌트와 정합.
- BE relay = stateless 하게 시작(in-memory Map<docId, Y.Doc 병합상태(byte[])>) — 노드-로컬(presence 와 동일 제약, 다중노드 후속). 권한 = 기존 collab page-code 가드 재사용.
- 보안: docId=문서 UUID(경로), update=opaque base64(사용자 식별자 미포함). awareness 의 user 필드는 displayName+color 만(UUID 비노출).
- 재사용: #672 의 RealtimeBroker·SSE stream·presence 색상(PresenceColor) 재사용. soft-lock(#672)은 별개로 파킹.

## 4. ⚠️ 규모/정직 기록
구글 독스급 완성도(오프라인 편집·복잡 충돌·리치텍스트)는 대형 다슬라이스 에픽. 본 설계는 **텍스트 필드 동시편집 MVP(Yjs over SSE relay)** 로 시작해 커서·셀렉트·실시간 병합을 우선 제공. 리치텍스트/대규모/persist 는 후속 슬라이스. 야간 자율은 S1(MVP) 완주 목표, 진척은 오전 보고.
