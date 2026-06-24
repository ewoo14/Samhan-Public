---
name: project_dispatch_on_inspect_epic
description: 검수완료→배차발송 에픽 — 출고전표 검수인 결재 완료 시 아로로지스/타배송사 발송. 견적 결재 제외(개발책임자). 슬1#590·슬2#591·슬3#592(타배송사 SMS) 머지, 슬4 인쇄 잔여.
metadata:
  type: project
---

# 검수 완료 → 배차 발송 (아로로지스 / 타배송사) 에픽

2026-06-24 brainstorming(superpowers). A2 결재 enforcement 잔여 검토 중 개발책임자가 재정의: **견적은 결재 불필요(스코프 제외)**, **배차는 enforcement 아님 = "출고전표 검수인 결재(OUTBOUND_INSPECT) 완료 → 배차 발송(아로로지스 또는 타배송사)" 워크플로우 연동**. spec=`docs/superpowers/specs/2026-06-24-dispatch-on-inspect-external-carrier-design.md`.

## 확정 결정 (개발책임자 2026-06-24)
- D1 견적 제외, 배차만. D2 검수 완료 → "발송 대기" 목록 → 운영자가 채널 선택 발송(자동 아님). D3 채널=아로로지스(자체)/타배송사(외부기사). D4 타배송사 발송=문자(SMS)+인쇄(배차의뢰서) 둘 다. D5 외부기사/배송사 **마스터** 등록. D6 묶음=아로로지스 기존 차량그룹 / 타배송사 기사별. D7 슬1 UX=**기존 "배차현황"(dispatch.board) 통합**(별도 페이지 아님).
- 비목표: 외부배송사 시스템 REST 연동 / 타배송사 배송완료 회신 추적 / 자동 채널결정 / 자동 발송 / arologis 배차 흐름 재설계.

## 슬라이스
- **슬1 ✅ 머지(PR #590, main bc52cbda)**: 배차현황 미배차 목록에 **검수 완료 게이트** + 검수자/검수일시 노출. predicate=`slipType=OUTBOUND AND status=COMPLETED AND inspectorUserId/inspectorSignedAt NOT NULL AND dispatchStatus=UNDISPATCHED`(SlipRepository.findDispatchReadyOutboundSlips). DispatchTaskBoardQueryService N+1 dedup(distinct inspectorUserId→Map)+graceful. SlipBoardResponse inspectorName/inspectorSignedAt(UUID 미노출). FE UnDispatchedSlipList 검수자/검수일시/배송지/수령자(KST 직접포맷·null '-'). arologis/Flyway/page-code/enum 무변경.
- **슬2 ✅ 머지(PR #591, main 1ecb5617)**: external_carrier 마스터(CRUD + 배차메뉴 SidebarLink + FE ExternalCarriersPage + V49 단일테이블 + V69 권한시드). **PM 정찰 보정 3건**: ①단일 page-code `dispatch.external-carriers` + 7-action 그래뉼러(dual `.manage` 폐기) — slip-service=**account-mode**(PermissionAspect→client.check(accountId,page,action)→account_page_permissions.can_<action>; inventory.dps/accounting.receivables 형제 패턴) ②**PageCode enum 등록 필수**(auth domain/PageCode.java — /my allPageActions가 PageCode.values() 순회, 미등록 시 MASTER 메뉴조차 미노출) ③**auth V69=V66 4-table seed**(role+template+group+account; role 단독 cross-join은 role-mode 전용이라 account-mode enforcement 미해결). gateway slip-dispatch-admin-noprefix Path에 /admin/external-carriers 추가(no-strip). 듀얼리뷰 0수렴(Opus 4-agent+fix→Codex 5-agent+fix→Opus 2-agent 재검), 라이브 QA 7/7 PASS. restore UI(후속 슬)·audit actor 비대칭(전역 관행) scope-out.
- **슬3 ✅ 머지(PR #592, main c738e2e2)**: 타배송사 SMS 발송 — external_dispatch(1)→external_dispatch_slip(N) **V50**(FK carrier→external_carrier·slip→slips) + ExternalDispatchService(발송대기 검수완료·UNDISPATCHED 전표를 external_carrier별 묶어 SMS) + `Slip.markDispatchedExternally`(UNDISPATCHED→DISPATCHED) + NotificationClient.sendExternalSmsWithResult(boolean, /internal/notifications/send 재사용) + FE 발송대기 채널분기(아로로지스/타배송사)+기사선택 모달. 권한=dispatch.board(발송)+dispatch.external-carriers(기사조회) **재사용, 신규 시드 0**. 듀얼리뷰 0수렴 + 라이브 QA 7/7(SMS=Aligo placeholder stub·정직). 슬3 교훈 ↓.
- **슬4 잔여**: 타배송사 인쇄 배차의뢰서(A4 PrintLayout, channel enum PRINT/BOTH 이미 V50 정의).

## 슬1 교훈
- 검수 상태머신: complete()=PROCESSING→INSPECTING, inspect()=INSPECTING→COMPLETED(inspectorUserId/SignedAt 기록). "검수 완료"=COMPLETED+inspector 필드.
- 기존 "배차현황"(DispatchBoardPage)에 미배차 목록(GET /admin/dispatch-board/undispatched-slips)+차량그룹+arologis 발송(DispatchTaskCompletionService) 이미 구현 → 슬1=게이트+노출만 추가.
- 타배송사 채널 전무(arologis 단일). 배차안내 SMS 인프라(notification-service DispatchBatchSendService/Aligo) 재사용 가능(슬3).
- 검수자명 resolve=UserInternalClient.resolveFullName 단건만(batch 없음)→페이지 내 distinct dedup. graceful=catch(Exception)(SlipService 패턴).

## 슬2 교훈 (재사용 가치 — 권한/PATCH/race/GitGuardian)
- **account-mode vs role-mode 구분**: slip-service 등 일반 서비스=account-mode(@RequirePermission→PermissionAspect→client.check(accountId,page,action)→`account_page_permissions.can_<action>` 그래뉼러; enforcement 소스=AccountPermissionService, materialized). arologis=role-mode(`role_page_permissions` canView/canEdit 2-bool). **신규 page-code 설계 시 서비스 모드 먼저 확인** — account-mode면 단일 page-code+7-action(inventory.dps 패턴) + **V66 4-table seed**(role+template+group+account, system-master NOT EXISTS 가드) 필수. role 단독 cross-join(V36)은 account-mode 미해결. **PageCode enum 등록 필수**(auth domain/PageCode.java — /my allPageActions가 values() 순회). 게이트웨이 /admin/** 신규 경로는 라우트 Path allowlist 추가(no-strip).
- **PATCH 부분수정 클리어 시맨틱**: 선택필드 null=미변경 / ""=클리어 / 값=설정. FE 편집은 빈문자열 전송(formToUpdateRequest 분리), BE blankToNull, mock PATCH 동일. **3곳(FE/BE/mock) 정합 + 회귀 테스트** 필수 — null 정규화하면 클리어 silent 무시(mock도 동일하면 false-green, Opus 라운드 P1).
- **create save+flush 동일 try**: `repository.save()`만 try/catch면 INSERT가 commit 시점(try 밖)으로 지연→unique 위반 DataIntegrityViolation이 catch 우회→500. `save()+flush()`를 같은 try에 넣어 race를 409로 변환(update/restore도 flushOr409 헬퍼). Codex 라운드 발견.
- **GitGuardian**: real-qa 스펙 테스트 비번(`dev_p05_pass!`=V5 P0-5 seed 공개 dev 계정)=false positive(우리 Credential Plaintext Guard SP-08-8는 pass). 기존 컨벤션 `process.env['DEV_PASSWORD'] ?? 'dev_p05_pass!'` 폴백 통일 + PM 자동 판정 후 머지([[feedback_gitguardian_false_positive]]). 슬3는 처음부터 폴백 컨벤션 사용→GitGuardian pass.

## 슬3 교훈 (재사용 가치 — 부팅/마이그/동시성/외부발송)
- **🚨 다중 생성자 부팅 실패(IT가 가림 → 라이브 단독 적발)**: 클래스에 **테스트 전용 생성자**를 추가하면 생성자 2개가 되어 Spring 빈 생성 모호 → `No default constructor found`로 **서비스 부팅 실패**. 실 빈 생성자에 `@Autowired` 명시로 해소. SpringBootTest IT(@MockBean으로 실 빈 미생성) + 단위 계약테스트(직접 `new`)가 둘 다 실 빈 생성을 우회 → false-green, **라이브 Docker 재빌드/부팅이 단독 적발**([[restclient-contract-test-false-green]] / "리뷰=실QA 동반"). slip-service NotificationClient(PR #592). 정적 4-agent 리뷰도 놓침(BE agent miss) → **라이브 재빌드가 머지 전 필수**.
- **미머지 신규 Flyway 수정 + 로컬 재정합**: 미머지 브랜치 마이그(V50 등)는 PR 내 수정 가능(머지 시 fresh 1회 적용 — [[feedback_applied_migration_immutable]]는 머지/prod 적용분 대상). 단 PM probe/라이브로 **로컬 DB에 이미 적용**됐으면 checksum mismatch → 재기동 crash. 해법: 대상 테이블 DROP + `DELETE FROM flyway_schema_history WHERE version='NN'` → 재기동 시 재적용(기존 DB의 참조 테이블 실재 → FK 생성 성공 = fresh probe 갈음).
- **동시 발송 race**: 비잠금 검증 후 상태전이는 동시 요청 중복 처리 가능 → `@Lock(PESSIMISTIC_WRITE)` row lock 조회로 검증~flush까지 같은 @Transactional 락 유지. 데드락 회피=동일 IN 술어 동일 스캔순서 + carrier는 무락 findById(락순서 역전 없음). `saveAndFlush`로 DB 예외 조기 노출.
- **graceful 외부발송 결과 = 거짓양성 주의**: notification SMS는 실패해도 HTTP 200 + status=FAILED(graceful, 재시도). FE가 res.status 미검사 시 미발송인데 '발송 완료' 거짓양성 → **res.status 분기 필수**(mock도 FAILED 시뮬 + 회귀가드). SMS 자체는 트랜잭션 비가역 side effect라 완전 원자화 불가(saveAndFlush로 불일치 창 축소).
- desktop 컴포넌트 **렌더 테스트 인프라 부재**(@testing-library/react 미설치 + vitest node-env) → 분기 로직 순수 함수 추출(resolveDispatchFeedback)로 회귀 가드(인프라 도입은 별도 에픽).
