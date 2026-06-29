# 실시간 필드-레벨 협업 — 설계 proposal (오전 확정 대기)

> 2026-06-30 새벽 자율 세션. 개발책임자 "협업 슬라이스까지 완료 / 권장방향 진행, 오전 확정"에 따른 **권장방향 설계**. §7 협업+presence는 이미 완결 상태라 "실시간 필드-레벨"은 신규 설계 → **major 신규 기능이라 guess-build·머지 회피, 본 proposal로 오전 확정 후 착수.**

## 1. 현 상태 (grounding 정찰 완료)
- **collab-core**(shared): CollabComment(anchor=필드 식별 가능)·CollabRevision(스냅샷)·CollabSuggestion(1-인 수정완료)·CollabRealtimePublisher(@Transactional afterCommit SSE 발행).
- **realtime-abstraction**(shared): `RealtimeBroker`(InMemory/Redis SSE)·`PresenceService`(**문서-단위** "누가 보는 중", in-memory, TTL 5분, 30초 heartbeat)·PresenceEntry(sessionId/displayName/color).
- **배선 6문서**: slip·estimate·dispatch·journal(회계)·partner-order·groupware 결재. 공통 `/collab/{comments,edits,presence,stream}`.
- **FE**(desktop): `usePresence` 훅·`createPresenceClient`(6 variant)·`PresenceIndicator`(보는 사람 아바타).

## 2. 권장방향 (PM 추천)
**필드-레벨 soft-lock + 편집중 인디케이터** — 기존 인프라 100% 재사용, **순수 additive(기존 코드 수정 0)**.
- **soft-lock**(강제 차단 아닌 시각 경고): "OOO가 이 필드 편집 중" 표시 + 낙관적 동시성. hard-lock은 네트워크 장애 시 deadlock 위험이라 비추천.
- **확장 지점**: ① `FieldLockService`(shared/realtime-abstraction/lock, in-memory+TTL, PresenceService 패턴 복제) ② `/collab/field-locks/{acquire,release}` + `GET /collab/field-locks` ③ SSE 이벤트 `presence:field-lock-acquired/released`(같은 stream, payload만 추가 — Option A) ④ FE `useFieldLock` 훅(input focus→acquire, blur→release) + 필드 인디케이터.
- **필드 식별 = JSON path 문자열**(`items.0.quantity`·`supplier.contactName`) — 도메인 무관.

## 3. 슬라이스 분해 (presence MVP 롤아웃 패턴 답습 — slip 먼저 1:1 복제)
- **S1 (MVP)**: shared `FieldLockService` + slip-service `/collab/field-locks/*` + FE `useFieldLock` + slip 상세 1~2 필드 인디케이터 + 라이브 QA(2세션 동시편집 캡처).
- **S2~**: 나머지 5문서 additive 배선(SlipFieldLock 1:1 복제).
- 각 슬라이스 표준 워크플로우(조기 PR→Codex 개발→Opus/Codex 5-agent 듀얼+라이브QA→0수렴→PM 종합→머지).

## 4. ⚠️ 오전 개발책임자 확정 필요 (설계 결정 — 착수 전)
1. **스코프 확인**: "실시간 필드-레벨 협업" = 본 proposal(필드 편집중 표시/soft-lock)이 맞는가? 아니면 **구글 시트식 값 실시간 co-editing**(타 사용자 입력값이 실시간 반영 — OT/CRDT, 훨씬 큰 작업)을 의도하는가? **(가장 중요)**
2. **soft-lock vs hard-lock**: 경고만(권장) vs 실제 입력 차단.
3. **롤아웃 범위**: 6문서 전부 vs 특정 문서(예 slip·estimate)만.
4. **값 스트리밍 여부**: 락/인디케이터만(권장 MVP) vs 입력값 실시간 브로드캐스트(co-editing).
5. **UX**: 인디케이터 형태(필드 테두리 색+이름 툴팁?), 동시 focus 다수 표시 방식.

## 5. 대안 (검토됨)
- **A. 필드 soft-lock + 인디케이터** (← 권장): additive·저위험·기존 인프라 재사용. 동시편집 충돌 "예방 UX".
- **B. hard-lock**: 충돌 원천 차단이나 deadlock/UX 경직 위험.
- **C. 값 실시간 co-editing(OT/CRDT)**: 구글 닥스급. 대규모 신규(충돌 머지 알고리즘·커서·실시간 값 동기화). 현 SSE 단방향+REST 구조 대수술. 비용 큼 → 별도 대형 에픽 필요 시 재설계.

→ **개발책임자 1번(스코프) 확정이 핵심.** A(권장)면 S1부터 즉시 착수 가능(설계 완료). C면 별도 대형 에픽 재기획.
