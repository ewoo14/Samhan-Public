# E2 기둥2 — 취소선 삭제 + 복원 (배차 파일럿, Plan B) 구현 계획

> **For agentic workers:** 구현=Codex(PM 직접구현 금지 [[feedback_pm_no_direct_implementation]]). 매 리뷰 라운드 full 5-agent·순차·PR OPEN·라이브QA(Electron 구동 제약 시 정직 disposition+GUI스샷 owed 명시). 캐논 8단계 [[feedback_canonical_workflow]].

**Goal:** 배차 하위 차량그룹/전표매핑 soft-delete 행을 목록에서 숨기지 말고 **취소선 + 삭제자 이름 + 영구 표시 + 권한자 복원**(전 메뉴 일반화 전 배차 파일럿). 기둥1(#699) 라이브 동기화 위에 얹음.

**Architecture:** BE=native `...IncludingDeleted` 조회로 삭제행 포함 + DTO 삭제메타(isDeleted/deletedAt/deletedByName) + 복원 서비스/엔드포인트(markRestored + RESTORED 발화). FE=삭제행 취소선+삭제자 배지+복원 버튼. 선례=PartnerOrderRevision 복원.

**Tech Stack:** Java 17/Spring Boot 3(slip-service), Flyway, React+TanStack Query(clients/desktop).

## Global Constraints
- BaseEntity soft-delete only·하드삭제 금지. UUID 비노출(deletedByName=이름, userId 화면 노출 금지). 한국어 Javadoc·PR OPEN. 적용 마이그 불변(신규 V만)·**fresh Postgres probe 검증** [[feedback_migration_fresh_postgres_probe]]. 변경모듈 전체 test 후 push. slip IT는 ci.yml slip-it-core `slip.it.dispatch.*` 자동커버.
- **결정(개발책임자 2026-07-02)**: ①그룹 복원=**cascade**(그룹+해당 삭제시점 함께 삭제된 하위 매핑 복원) ②deletedByName=**신규 컬럼 deleted_by_name**(deletedBy=userId 감사 유지) + X-User-Name(resolveActorName non-UUID) 저장.

---

### Task 1: Flyway — deleted_by_name 컬럼 (2 테이블)
**Files:** Create `services/slip-service/src/main/resources/db/migration/V##__dispatch_deleted_by_name.sql` (다음 번호 확인).
- `ALTER TABLE dispatch_vehicle_group ADD COLUMN deleted_by_name VARCHAR(100);` + `dispatch_vehicle_group_slip` 동일. NULL 허용(기존 삭제행=NULL 정직 fallback).
- **Step**: 다음 V 번호 grep(`ls db/migration | tail`) → 작성 → **fresh Postgres probe**(DROP/CREATE + 대상테이블 생성 + `psql ON_ERROR_STOP -f V##`)로 적용 검증 → commit.
- BaseEntity 매핑: 엔티티에 `deletedByName` 필드는 Task2에서 추가(엔티티 컬럼 매핑 `@Column(name="deleted_by_name")`).

### Task 2: 삭제 시 deletedByName capture
**Files:** Modify `DispatchVehicleGroup.java`·`DispatchVehicleGroupSlip.java`(필드 `deletedByName` + `markDeletedWithName(userId, name)` 헬퍼 or 기존 markDeleted 후 setter), `DispatchTaskService.java`(removeVehicleGroup/removeSlipFromGroup 에 name 전달), `DispatchTaskAdminController.java`(삭제 endpoint 에 `@RequestHeader(value="X-User-Name",required=false)` 추가 + resolveActorName 로 non-UUID 정제).
- name resolve = `SlipService.resolveActorName`/`PartnerOrderRevisionService.displayNameOrNull` 패턴(UUID면 null) 재사용/복제. deletedBy=userId(actor) 유지, deletedByName=정제된 이름.
- **Test**: removeVehicleGroup 후 deletedByName 저장 단위/IT.

### Task 3: 삭제행 포함 조회 + DTO 삭제메타
**Files:** Modify `DispatchVehicleGroupRepository.java`·`DispatchVehicleGroupSlipRepository.java`(native `@Query(nativeQuery=true) ...IncludingDeleted` 신규 — 선례 `PartnerOrderRepository.findByIdIncludingDeleted:32`), `DispatchTaskHistoryQueryService.java`(detail/loadSnapshot 를 IncludingDeleted 로 전환 + 삭제행 하단 정렬), `DispatchTaskDetailResponse.java`(VehicleGroup/VehicleGroupSlip record 에 `boolean isDeleted, Instant deletedAt, String deletedByName` 추가·`of(...)` 매핑).
- 정렬: 활성 먼저(sequence) + 삭제행 뒤(deletedAt). 삭제 그룹 안 삭제 매핑도 포함.
- **Test**: 삭제 그룹/매핑 포함 조회 IT(삭제행 노출 + 메타 정확), 활성/삭제 정렬.

### Task 4: 복원 서비스 + 엔드포인트 (cascade)
**Files:** Modify `DispatchTaskService.java`(신규 `restoreVehicleGroup(taskId, groupId, actor, name)` = native 로드→group.markRestored()→**같은 deletedAt(±윈도우) 로 cascade 삭제됐던 하위 매핑 native 로드→markRestored** [결정①] →`publishBoardChanged("RESTORED")`; `restoreSlipFromGroup(...)` = 매핑 단건 복원), `DispatchTaskAdminController.java`(신규 `POST .../vehicle-groups/{groupId}/restore`·`POST .../vehicle-groups/{groupId}/slips/{slipId}/restore`, `@RequirePermission(page="dispatch.board", action=RESTORE)` + checkEditPermission).
- 선례 `PartnerOrderRevisionService.restore:181-285`(native 로드 후 markRestored·@SQLRestriction 컬렉션 함정 주석 준수). @Transactional → RESTORED afterCommit 발화.
- **cascade 복원 매칭**: 그룹 삭제 시 매핑도 같은 actor·근접 deletedAt 로 markDeleted됨 → 그룹 복원 시 `vehicleGroupId=그룹 AND is_deleted AND deletedAt≈그룹.deletedAt` 매핑 복원(정밀 매칭 = deletedAt 동일 트랜잭션 근사, 구현 시 removeVehicleGroup 이 그룹/매핑에 동일 timestamp 부여하는지 확인 후 조건 확정).
- **권한 시드**: dispatch.board 에 RESTORE action 시드 존재 확인, 없으면 V## 멱등 시드 추가(선례 partner-order revisions RESTORE). 확인 필수.
- **Test**: restore IT(cascade 매핑 복원·RESTORED 발화 verify·권한 403), rollback 미발화.

### Task 5: FE — 취소선/삭제자 배지/복원
**Files:** Modify `clients/desktop/src/renderer/routes/dispatch-board/api/dispatchTask.ts`(타입 `DispatchVehicleGroupResponse`/`...SlipResponse`+`DispatchTaskResponse` 에 `isDeleted?/deletedAt?/deletedByName?` + 신규 `restoreVehicleGroup`/`restoreSlipFromGroup` 함수), `hooks/useDispatchTask.ts`(신규 restore mutation, invalidate 3키 `dispatchTaskQueryKey(taskId)`+`DISPATCH_BOARD_QUERY_KEY`+`['dispatchTasks']`), `components/VehicleGroupCard.tsx`·`DispatchTaskDetailModal.tsx`(삭제행=취소선 스타일+"삭제: {deletedByName}" 배지+복원 버튼, 권한 canAccess(dispatch.board,restore) 게이트·readOnly 상세는 표시만).
- design-system 기존 컴포넌트·취소선 CSS. 영구 노출 → 삭제행 시각 약화(흐리게).
- **Test**: vitest(삭제행 취소선 렌더·복원 버튼 권한·복원 mutation invalidate).

### Task 6: 라이브 QA + dev-report
- **라이브 QA**: Docker(slip 재빌드)+게이트웨이 :8080. 배차에서 그룹/전표 제거→취소선+삭제자 표시→복원→라이브 반영. **GUI 스샷**: Electron 구동 제약 시 정직 disposition + 개발책임자 실캡처 안내(기둥1 owed 와 동일), SSE/API round-trip 보조 캡처. docs/qa/e2-strikethrough-dispatch/.
- dev-report `docs/dev-reports/2026-07-02-e2-strikethrough-delete-dispatch.md`.

## Self-Review
- Spec 커버리지: E2 spec 기둥2(§3·D-E2-01) 전부 Task1-6 커버. 결정①②(cascade·신규컬럼) 반영. ✅
- 주의: 마이그 fresh-probe·복원 시 native 선로드(@SQLRestriction 함정)·cascade 매칭 정밀도(deletedAt 근사)·RESTORE 권한 시드 존부. 

## 캐논 워크플로우
조기 PR(OPEN·base=main) → Codex 개발+게시 → Opus 5-agent+fix+게시 ↔ Codex 5-agent+fix+게시 0수렴 → PM 종합 → CI green(slip-it-core dispatch IT+마이그 probe+desktop vitest) → 라이브QA → squash 머지.
