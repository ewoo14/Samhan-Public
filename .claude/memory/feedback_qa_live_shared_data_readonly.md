---
name: feedback_qa_live_shared_data_readonly
description: 라이브QA가 공유 실 도메인 데이터(템플릿·마스터·설정)에 write 하면 위험 — 읽기전용/throwaway 격리. soft-delete replace-set 모델을 이해 못한 채 DB 수술 시 활성 데이터 파손. 2026-07-18 #825 슬4 실증.
metadata:
  type: feedback
---

**사건(2026-07-18 #825 슬4)**: 실서버 라이브QA에서 결재양식(ApprovalTemplate) 편집 테스트가 **실 공유 템플릿(휴가신청서)에 옵션 저장을 write**. 템플릿 update 는 **soft-delete replace-set**(기존 필드 `is_deleted=true` 마킹 + 신규 필드 행 추가) 설계인데, PM(나)이 `SELECT ... WHERE field_key='leaveType'`로 조회해 [soft-deleted 원본(720X·5옵션)] + [활성 저장본(random-UUID·4옵션)] **2행을 보고 "중복 삽입 버그"로 오진** → **활성 행(random-UUID)을 hard-delete** → LEAVE_REQUEST 활성 필드 0 = **일시 파손**. 뒤늦게 `is_deleted` 컬럼·`ux_..._key_active WHERE is_deleted=false` 유니크 인덱스 확인하고 원본 720X 를 **un-soft-delete(is_deleted=false)** 해 원상복구(API 검증: 4필드·leaveType 5옵션). 잔여 오염 0.

**Why**:
1. **라이브QA가 공유 실데이터를 write** — mock 이 못 잡는 것(실 UUID 미노출·실 delta round-trip)만 실서버로 검증하면 되는데, 편집 테스트가 실 템플릿을 저장해 공유 상태를 변형. 단일 세션이라도 실 문서를 오염시키면 타 사용자/후속 QA 에 영향.
2. **soft-delete replace-set 모델을 이해 못한 채 DB 수술** — "중복처럼 보이는 2행"이 실은 [비활성 원본]+[활성 신규]. 심각도 도메인(회계 아님이나 결재 설정)에서 활성 판별(is_deleted) 없이 DELETE = 활성 파손.

**How to apply**:
1. **공유 실데이터 write 라이브QA = 회피.** ① 읽기전용 검증(렌더·칩·DOM UUID·delta 관측)으로 대체하거나 ② **throwaway 전용 엔티티**(전용 거래처/품목/템플릿 생성→검증→삭제)로 격리. 실 공유 마스터/설정/템플릿에 직접 write 금지. (delta 왕복이 꼭 필요하면 net-zero add+remove 로 최소화하고 대상은 안전한 전용 레코드.)
2. **DB 직접 수술 전 = 도메인의 soft-delete/버전 모델부터 이해.** BaseEntity soft-delete(`is_deleted`/`deleted_at`/`deleted_by`)·replace-set(update=old soft-delete + new insert)·`WHERE is_deleted=false` 유니크 인덱스를 확인. "중복 행"은 대개 [비활성 이력]+[활성 현재]. **hard-delete 전 is_deleted 로 활성 판별 필수.** 서비스 update 로직(예 `replaceFields`)을 읽어 정상 설계인지 먼저 확인(정상 설계를 버그로 오진 금지).
3. **오염 유발 시 = 즉시 완전 복구 + 정직 고지.** 원상 확인(seed/원본 값)→최소 수술로 복구(un-soft-delete 등)→API 로 복원 검증→PR·dev-report·개발책임자께 사건 명시([[feedback_no_fake_data_ever]] 정직). 무결성 인접 도메인은 특히.

→ [[feedback_parallel_agent_gradle_shared_tree_contention]](③ 공유 라이브DB 쓰기 경합)·[[feedback_qa_docker_real_test]]·[[feedback_realqa_run_and_false_red]]·[[feedback_applied_migration_immutable]](soft-delete/불변 모델)·[[feedback_no_fake_data_ever]].
