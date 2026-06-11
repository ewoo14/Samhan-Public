# SPEC — AROLOGIS 완료배차 내역 뷰 (배차담당자 조회 전용)

> 개발책임자 명시 다음 슬라이스(2026-06-11, #462 분리 지시). 배차담당자가 **완료·전송한 배차 내역**(전표 포함)을 **조회 전용**으로 보는 화면. 정찰 보고 기반 spec 접지.

## 0. 슬라이스 해석 (defaults — 조기 PR에서 조정 가능)
- **"완료배차" = `DispatchTaskStatus.DISPATCHED`**(아로로지스 confirm 회신 = 기사매칭 완료). "전송" = 배차 발송(DISPATCHING→DISPATCHED 흐름), **SMS 발송 아님**(근거: "전표 포함" → DispatchTask 도메인, SMS audit 와 데이터모델·키 무관). 목록 기본 필터=DISPATCHED, 상태 칼럼+필터로 종결상태(CANCELLED/FAILED)도 선택 조회 가능.
- **위치 = 메인 `clients/desktop` "배차" 그룹** 신규 라우트(삼한 측 배차담당자 대상). arologis-desktop(vendor 측) 아님.
- **권한 = 기존 `dispatch.board` VIEW 재사용**(조회 전용 → 신규 page-code 불요, seed 부담 회피).
- **전표 표시 = 전표헤더(전표번호·거래처·인수지·기사)** 수준. **품목 라인 depth = 범위 외**(후속 — slip 라인/product join 필요).
- **조회 전용 = mutation 0**(편집/배차/취소 버튼 없음 — DispatchBoardPage 와 구분).

## 1. 핵심 가치 + 기존 갭 동시 해결
- 신규: 완료배차 목록/상세 조회 화면.
- **기존 404 갭 해소**: FE `getDispatchTask(taskId)` → `GET /admin/dispatch-tasks/{taskId}`(`dispatchTask.ts:219-226`)를 `useDispatchTaskQuery`(`useDispatchBoard`)가 호출하나 **BE 컨트롤러에 해당 GET 부재**(DispatchBoardPage 상세모달이 실 BE 대상 404). 본 슬라이스가 detail GET 을 신설하면 **신규 뷰 + 기존 보드 상세모달 둘 다 정상화**. → detail DTO 는 **기존 FE `DispatchTaskResponse`(TS, `dispatchTask.ts:181-193`) 형태와 1:1 정합**해야 한다(vehicleGroups[].slips[].slip + matchedDrivers[]).

## 2. BE (services/slip-service)
slip-service 가 DispatchTask + slip 둘 다 소유 → **서비스 내부 join**(cross-service 호출 없음).

### 2-1. 목록 — `GET /admin/dispatch-tasks`
- 컨트롤러: `web/dispatch/DispatchTaskAdminController` 에 GET 신설(현재 WRITE 전용). `@RequirePermission(page="dispatch.board", action=VIEW)`.
- 쿼리 파라미터: `from`(LocalDate)·`to`(LocalDate, 기본 최근 N일)·`status`(반복, 기본 [DISPATCHED])·`page`·`size`(기본 20).
- 서비스: **기존 리포 쿼리 재사용** `DispatchTaskRepository.findByDispatchDateBetweenAndStatusInAndIsDeletedFalse(from,to,statuses,pageable)`(`DispatchTaskRepository.java:20-21`, 현재 미사용).
- 응답 = 페이지 DTO(요약): `taskCode`·`dispatchDate`·`status`·`vehicleGroupCount`·`slipCount`·`partnerNames`(요약 ≤3 + +N)·`driverCount`·`arologisDispatchId`. UUID 비공개([[feedback_uuid_no_user_visibility]]) — taskCode 등 비즈니스 식별자만.

### 2-2. 상세 — `GET /admin/dispatch-tasks/{taskId}`
- 동일 컨트롤러 GET 신설. `@RequirePermission(dispatch.board, VIEW)`.
- 응답 = **rich detail DTO**(FE `DispatchTaskResponse` TS 정합): `id`(FE 내부용, 화면 미노출)·`taskCode`·`dispatchDate`·`status`·`arologisDispatchId`·`failureReason`·`modification*` + `vehicleGroups[]`(`{id, vehicleType, sequence, slips[]{id, slipId, sequence, slip{slipNo, partnerCode, partnerName, deliveryAddress, recipientPhone, dispatchStatus}}}`) + `matchedDrivers[]`(`{vehicleGroupSequence, driverCode, driverName, driverPhoneNumber, driverSource}`).
- 조립: DispatchTask → DispatchVehicleGroup(N) → DispatchVehicleGroupSlip(N, `slipId`) → **slip 헤더 조회**(slip repo, slipNo/partnerName/deliveryAddress — `SlipBoardResponse.java:19-28` 필드) + MatchedDriver(vehicle_group_id) join. N+1 회피(slipId IN 일괄 조회).
- DTO 정의: 기존 빈약 `DispatchTaskResponse`(record, `dto/dispatch/DispatchTaskResponse.java:25-36`)를 **확장 또는 신규 `DispatchTaskDetailResponse`**. FE 타입과 필드명 **정확 일치**([[feedback_fe_option_type_matches_be_dto]] — 이름+타입+실 매칭처). 기존 board 상세모달(`DispatchTaskDetailModal.tsx`)이 같은 타입 소비하므로 회귀 점검.

### 2-3. 테스트 (BE IT)
- `@SpringBootTest`+Testcontainers(CI Linux): 목록(status 필터·date range·pagination) / 상세(DTO 조립 — vehicleGroups·slips·drivers 채워짐) / 권한(VIEW grant→200, deny→403, [[feedback_enforcement_real_http_test]] 실 HTTP). 시드: DISPATCHED task + vehicleGroup + slip 매핑 + matchedDriver.
- [[feedback_ci_test_filter_false_green]]: 신규 IT 패키지가 ci.yml slip 잡 필터에 포함되는지 확인(slip.it.* 자동 커버 여부).

## 3. FE (clients/desktop)
### 3-1. 라우트 + 메뉴
- 신규 라우트 `/dispatch-board/history`(또는 `/dispatch/completed`) — `routes/index.tsx` 배차 영역. `<PermissionGuard pageCode="dispatch.board" action="view">` 래핑.
- `AppLayout.tsx` 배차 그룹(`SidebarCategory label="배차"`)에 `SidebarLink to="/dispatch-board/history"`("완료 배차 내역") 추가, `show={showDispatchBoard}`(기존 dispatch.board 노출식 재사용), activeTargets 에 경로 추가.
- 메뉴 IA 계약 박제([[menu-ia-contract]] 류) 갱신 필요시 동반.

### 3-2. 목록 페이지
- `routes/dispatch-board/DispatchHistoryPage.tsx`(신규): 날짜범위 필터(기본 최근 N일) + 상태 필터(기본 DISPATCHED) + 테이블(taskCode·날짜·상태 badge·차량그룹수·전표수·거래처요약·기사수). 행 클릭 → 상세. **조회 전용**(편집/배차 버튼 0). DataTable(@samhan/design-system) 사용, UUID 미노출.
- 클라이언트: `dispatchTask.ts` 에 `getDispatchTasks(params)` 목록 client 신설(detail `getDispatchTask` 는 기존). react-query 훅.

### 3-3. 상세
- 기존 `DispatchTaskDetailModal.tsx` 재사용(읽기 전용 표시: 상태·차량그룹·전표·기사) 또는 상세 패널. 완료내역 행 → 모달/상세. detail GET(2-2)이 채우는 rich DTO 소비.

### 3-4. 테스트 (FE mock spec)
- `playwright/dispatch-completed-history/`: ① DISPATCHED 목록 렌더(mock) ② 날짜/상태 필터 ③ **조회 전용 단언**(mutation 버튼 부재) ④ 행→상세(차량/전표/기사 표시) ⑤ 권한(view 없는 역할 redirect) ⑥ UUID 미노출. mock 핸들러 3원칙([[feedback_inprocess_mock_principles]]) — `/admin/dispatch-tasks`(list)·`/{id}`(detail) mock seed 추가. design-system testid forward 주의.

## 4. 검증 + QA
- typecheck(desktop `npm run typecheck`, [[feedback_desktop_typecheck_command]]) · slip-service build+IT · **변경 모듈 전체 mock suite 완주**([[feedback_changed_module_full_test_before_push]]).
- Docker 실서버 QA([[feedback_qa_docker_real_test]]·[[feedback_real_server_check_screenshot]]): 실 게이트웨이 :8080 + 실 로그인(dev_dispatch 또는 dev_master) → DISPATCHED task 1건 이상 생성/존재 시 완료내역 목록+상세 실 캡처(API JSON 아닌 데스크톱 화면). DISPATCHED 데이터 없으면 보드에서 1건 배차완료 처리 후 캡처(또는 캡처불가+사유 정직 보고, [[feedback_no_fake_data_ever]]).

## 5. 범위 외 (명시)
- 전표 **품목 라인** depth(후속 — slip 라인 + product-service). SMS 발송 audit 과의 통합/중복(별 도메인 — `notification.dispatch-sms.send-audit`/`dispatch.sms-save-history` 유지). 신규 page-code. 일체의 mutation(편집/재배차/취소). arologis-desktop 측 화면.

## 6. 결정 박제 후보 (DECISIONS)
- **D-DCH-01**: 완료배차 내역 = DispatchTask `DISPATCHED` 중심 조회 전용(전표헤더·차량·기사 포함, 품목/SMS 범위 외). 위치=메인 desktop 배차 그룹. 권한=`dispatch.board` VIEW 재사용.
- **D-DCH-02**: detail GET 신설로 기존 FE 사전작성 client 404 갭(보드 상세모달) 동시 해소 — DTO 는 FE `DispatchTaskResponse` 타입 1:1 정합.
