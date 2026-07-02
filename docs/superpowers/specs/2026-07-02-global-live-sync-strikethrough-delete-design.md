# 전역 라이브 데이터 동기화 + 취소선 삭제 — 설계 (E2)

> 작성 2026-07-02 (회사PC remote-control 세션). 개발책임자 지시 기반. 브레인스토밍 승인 후 spec.
> 관련: [[project_global_collab_epic]](문서내 coedit) 의 **컬렉션(목록) 레벨 확장** + 소프트삭제 취소선 노출.

## 1. 배경 / 목표

개발책임자 지시(2026-07-02):
- **모든 메뉴의 데이터가 실시간 편집상황 반영.** 새 데이터 생성 시 다른 사람이 보는 화면에도 즉시 생성. **수정도 즉시 반영**(반영 시점 = **저장/커밋 순간**). 생성/삭제 = 발생 즉시.
- **삭제 = 하드삭제 금지, 취소선 표시 + 삭제자.** 삭제행 = **영구 취소선 유지**(누가 삭제했는지 확인 목적).
- **데스크탑·모바일 무관 전 메뉴.**

기존 협업(#16 coedit)은 *문서 내부 필드/셀* 라이브 편집이었다. 본 에픽은 **목록/컬렉션 자체**의 라이브 동기화 + 소프트삭제 취소선 노출을 추가한다.

### 성공 기준
- 파일럿(배차) 목록에서 A 세션의 생성/수정(저장)/상태변경이 B 세션 화면에 **즉시** 반영(실 SSE round-trip).
- 소프트삭제된 행이 목록에서 사라지지 않고 **취소선 + 삭제자 이름 + 영구 유지**, 권한자는 **복원** 가능.
- 데스크탑 + 모바일(WebView) 동일 반영.
- 공유 헬퍼로 일반화되어 **각 도메인 배선이 최소(~수십 줄)**.

## 2. 정찰 요약 (재사용 vs 신규)

이미 성숙한 SSE 인프라와 **컬렉션 동기화 완성 선례**가 존재 → 신규 발명이 아니라 수평 확장.
- `shared/realtime-abstraction` `RealtimeBroker`(entityId 단위 pub/sub, SSE, in-memory + Redis 다중노드). 14서비스 공통.
- **레퍼런스 = product 카탈로그 3종**: `BundleComponentService.CATALOG_CHANNEL_ID`(합성 UUID 채널) + `ProductCatalogChangePublisher`(트랜잭션 **afterCommit 발화**) + `ProductCatalogRealtimeController`(SSE, `@RequirePermission products.list VIEW`) + FE `ProductRealtimeClient` → `ProductCatalogPage` 가 `invalidateQueries`.
- 모바일 `clients/mobile` = 순수 WebView 래퍼 → WebView 안 웹 번들의 SSE 가 자동 반영. **RN 네이티브 SSE 신규 불요**(웹 SSE 가 WebView 내 동작하는지 검증만).
- 소프트삭제: `BaseEntity`(`isDeleted`/`deletedAt`/**`deletedBy`**) + `markDeleted(userId)`/`markRestored()` 완비. ⚠️ `@SQLRestriction("is_deleted=false")` 가 삭제행을 전역 차단 → **취소선 노출 = 조회계층/DTO 신규작업**(최대 신규 리스크).

## 3. 아키텍처 — 2기둥

### 기둥1: 라이브 컬렉션 동기화 (공유 헬퍼 일반화)

`shared/realtime-abstraction` 에 재사용 헬퍼 신설:
1. **`CollectionChannel`** — 도메인별 well-known 합성 UUID + 이벤트명 상수를 담는 값 객체/등록 유틸. (product 의 `CATALOG_CHANNEL_ID` + `EVENT_CATALOG_CHANGED` 를 일반화)
2. **`CollectionChangePublisher`** (base) — `TransactionSynchronizationManager` 로 활성 트랜잭션이면 `afterCommit` 지연발화(롤백 시 미발화), 없으면 즉시 발화. `publishChange(channel, changeType, payload)`. (`ProductCatalogChangePublisher` 를 일반화)
   - `changeType` = `CREATED | UPDATED | DELETED | RESTORED | STATUS_CHANGED`. payload = 최소(변경 엔티티 식별자 + 종류) — FE 는 상세를 refetch 하므로 opaque 최소 페이로드.
3. **FE 공통 훅** `useCollectionRealtime(client, queryKey, queryClient)` (`clients/desktop/src/renderer/realtime/`) — SSE 수신 시 `invalidateQueries({ queryKey })`. mock 모드 skip. (product 의 `ProductCatalogPage` 구독 로직 일반화)

각 도메인 배선(파일럿 기준 ~수십 줄):
- 채널 UUID 상수 1개.
- CRUD 서비스 각 mutation 뒤 `publisher.publishChange(...)` 1줄.
- `*-realtime` SSE 컨트롤러(도메인 broker 재사용, `@RequirePermission {목록}.VIEW`).
- FE 목록 페이지에 `useCollectionRealtime` 훅 1줄 + 도메인 realtime client 1파일(`createRealtimeClient` 팩토리).

### 기둥2: 취소선 삭제 (신규)

- **BE 조회**: 대상 목록 쿼리를 **삭제행 포함**으로. `@SQLRestriction` 은 JPQL/Criteria 에만 적용되므로 **native 쿼리 또는 별도 조회 경로**로 우회(선례 `PartnerOrderRepository.findByIdIncludingDeleted`). 목록 DTO 에 `isDeleted`, `deletedAt`, **`deletedByName`** 추가.
  - **`deletedByName`**: `deletedBy`(userId 문자열) → 사용자 이름 resolve. **UUID/식별자 비노출**([[feedback_uuid_no_user_visibility]]). resolve = 기존 user lookup(UserClient/username→이름) 패턴 재사용. batch resolve 로 N+1 회피.
- **FE 렌더**: `isDeleted` 행 = **취소선 + "삭제: {이름}" 배지**. 영구 노출 → 가독성 처리: 삭제행 시각 약화(흐리게/연한 배경)·기본 정렬 하단·페이지네이션 유지.
- **복원**: 권한자에게 삭제행에 **"복원" 액션**(`markRestored()` 호출 BE 엔드포인트) 노출. 복원 = `RESTORED` 컬렉션 이벤트 발화 → 타 화면 라이브 반영.
- **권한**: 목록 VIEW 권한자(내부 직원)면 삭제자 이름·복원 노출. (외부 거래처 화면엔 미적용 — 내부 백오피스 한정)

## 4. 파일럿 = 배차 (slip-service dispatch 도메인)

정찰 확정 진입점:
- 배차 = `slip-service` 내 `domain/dispatch/` (`DispatchTask` 등). 목록 `GET /admin/dispatch-tasks`(page `dispatch.board` VIEW, DTO `DispatchTaskSummaryResponse`). FE `routes/dispatch-board/DispatchHistoryPage.tsx`(`useDispatchTasksQuery`, SSE 없음). broker `SlipRealtimeBroker` 재사용 가능.

### D-E2-01 (결정 — spec 검토 확인 요망): 배차 "삭제" 대상 정의
`dispatch_task` **자체엔 소프트삭제 CRUD 경로가 없음**(Task 종료 = status `CANCELLED`). 실제 soft-delete 는 하위 **차량그룹/전표매핑 제거**(`removeVehicleGroup`/`removeSlipFromGroup` = `markDeleted`).
- **권장 파일럿 스코프**:
  - **기둥1(라이브 동기화)** = 배차현황 **목록**(생성·수정·**status 변경**[DISPATCHED/CANCELLED 등] 실시간 반영).
  - **기둥2(취소선 삭제 + 복원)** = 배차 **보드/상세 내 차량그룹·전표매핑 제거**(실제 soft-delete 지점) → 취소선+제거자+복원. + 목록에서 status=CANCELLED 배차는 숨기지 말고 취소 표시(부가).
- 이 스코프면 두 기둥 모두 배차에서 end-to-end 실증되며, 일반 목록 soft-delete(판매전표 등)는 롤아웃에서 커버.

### 파일럿 배선 표
| 목적 | 위치 | 작업 |
|---|---|---|
| CRUD publish 훅(afterCommit) | `service/dispatch/DispatchTaskService`(createTask/addVehicleGroup/assignSlip/removeVehicleGroup/removeSlipFromGroup) + 완료/취소 서비스 | 공유 `CollectionChangePublisher` 상속 `DispatchChangePublisher` 각 mutation 뒤 호출 |
| BE SSE 컬렉션 컨트롤러 | 신규 `web/dispatch/DispatchTaskRealtimeController` | `SlipRealtimeBroker` 재사용, 채널 `dispatch:board:changed`, `@RequirePermission(dispatch.board, VIEW)` |
| FE 목록 SSE 구독 | `DispatchHistoryPage.tsx` + 신규 `realtime/DispatchTaskRealtimeClient.ts` | `useCollectionRealtime` → `invalidateQueries(['dispatchTasks'])` |
| 취소선 삭제 노출 | 그룹/전표매핑 조회 경로 + 상세 DTO | soft-delete 매핑 포함 조회 + `isDeleted`/`deletedByName` + FE 취소선·복원 |

### task5 동반: 배차현황 상세 전표확인 = 판매전표 미리보기
배차현황 상세(`DispatchTaskDetailModal.tsx`)에 **전표확인→판매전표 미리보기 연결이 없음**(보드엔 `SlipDetailModal` 있음). 상세 모달 전표행에 보드와 동일한 `SlipDetailModal`(`getSlip`→`GET /slips/{id}`) 오픈 핸들러 추가. (E2 파일럿 슬라이스에 동반 or 직후 소형 슬라이스)

## 5. 모바일

`clients/mobile` = WebView 래퍼. 파일럿에서 **웹 SSE 가 WebView 내부에서 실제 연결·수신되는지 검증**(EventSource polyfill 부재 확인 — `createRealtimeClient` 는 fetch+ReadableStream 수동 파서라 RN WebView 웹뷰 내 브라우저 환경에서 동작 예상). 미동작 시 P2 명시 + 대안(폴백 폴링/폴리필) 후속.

## 6. 테스트 / 라이브 QA

- **BE**: `DispatchChangePublisher` afterCommit/rollback 단위, SSE 컨트롤러 권한 IT, 삭제행 포함 조회 + `deletedByName` resolve IT(Testcontainers).
- **FE**: `useCollectionRealtime` 단위(mock skip·invalidate), 취소선/복원 렌더 vitest.
- **라이브 QA(필수·실캡처)**: Docker 스택 기동 → **2세션**(A/B 데스크탑) — A 생성/수정/상태변경 → B 목록 즉시 반영 실캡처. A 가 그룹/전표 제거 → B 화면 취소선+제거자 실캡처. 복원 → 라이브 복구. + 모바일 WebView 반영 1컷.

## 7. 롤아웃 (파일럿 후)

파일럿 검증·수렴 후 고빈도 목록부터 점진(각 캐논 8단계 슬라이스): 판매전표 목록 → 주문 → 견적 → 거래처 → 재고 → 회계 목록 …. 공유 헬퍼라 도메인당 배선 최소. 각 도메인 소프트삭제 목록은 취소선 노출 동반.

## 8. 비목표 (YAGNI)
- 목록 셀의 **글자 단위** 라이브 편집(그건 문서 coedit 소관, 목록은 저장 시점 반영).
- 외부 거래처 화면의 삭제자 노출(내부 백오피스 한정).
- 취소선 삭제행의 자동 만료/아카이빙(영구 유지 지시 — 가독성은 정렬/약화/페이지네이션으로).

## 9. 결정 기록
- **D-E2-01**: 배차 파일럿 "삭제" 대상 = 하위 그룹/전표매핑 soft-delete(취소선+복원) + 목록 status 반영. (spec 검토 확인 요망)
- 삭제행 = 영구 취소선(개발책임자 확정). 수정 반영 = 저장/커밋 시점(확정). 공유 헬퍼 일반화 + 배차 파일럿(확정). 복원 포함(확정).
