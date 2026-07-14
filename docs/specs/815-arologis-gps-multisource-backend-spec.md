# #815 — arologis 배차 상세 GPS 멀티소스 백엔드 노출 (FE-2)

- **상태**: 기획 완료(정찰 + 개발책임자 결정 확정) · 구현 대기 (다음 세션 집PC)
- **일자**: 2026-07-14
- **연관**: #804(배차 상세 계약 정합·gpsSources 이연분) · FE `InsungLbsPanel`
- **목표**: `DispatchDetailResponse.VehicleDetail.gpsSources`를 실데이터로 채워 배차 상세 GPS 패널 원복.

## 개발책임자 결정 (2026-07-14 확정)

1. **Insung LBS = 배송시각 스냅샷 노출**: `signatures`(source=EXTERNAL_INSUNG_LBS)의 배송완료 좌표를 GPS 소스로 노출. 라벨 "배송시각 위치", **stale 표시**, 좌표 null이면 미노출. active는 실시간 APP_GPS 우선(아래 active 로직).
2. **MANUAL = 관리자 수동입력 신설**: 관리자가 차량 위치를 수동 보정 입력하는 기능(BE 엔드포인트 + 적재)을 이번 슬라이스에 신설. `driver_locations`에 source=MANUAL로 적재.

## 정찰 결과 (BE 도메인 실측 — 2026-07-14)

- **`gpsSources`는 BE 전면 greenfield**(grep 0). DTO·조립·active 산정 전부 신규.
- **`DriverLocation`**(`domain/DriverLocation.java`, table `driver_locations`): `id·driverId·latitude/longitude(NUMERIC 10,7)·capturedAt·capturedDate·source(DriverLocationSource)`. **BaseEntity 미상속**(대량적재·30일 hard DELETE). "마지막 수신" = `capturedAt`.
- **`DriverLocationSource` enum**: APP_GPS_BACKGROUND·APP_GPS_ACTIVE·EXTERNAL_INSUNG_LBS·MANUAL (FE `GpsSourceKey`와 1:1).
- **`DriverLocationRepository`**: **조회 메서드 0개**(write-only: `deleteOlderThan`·`countByDriverId`). → 신규 read 메서드 필요. 인덱스 `(driver_id, captured_at DESC)` 기존재 → DDL 변경 불필요.
- **적재 실태**: `driver_locations`엔 **APP_GPS_ACTIVE/BACKGROUND만** 적재(`ArologisDriverAppController` POST /driver-app/arologis/locations, X-User-Id=본앱 기사). **EXTERNAL_INSUNG_LBS/MANUAL은 driver_locations 미적재**.
- **Insung 좌표 위치**: `InsungWebhookService.handleDelivered`가 **`signatures`**(source=EXTERNAL_INSUNG_LBS, **stop_id 키**, `captured_latitude/longitude`)에 배송완료 시 1회 저장. 좌표 null 가능(`req.gpsLat()!=null` 가드). `SignatureRepository.findByStopIdAndSource` 기존재.
- **조인 키**: `Vehicle.assignedDriverId` == `DriverLocation.driverId` == `Driver.id`. Insung 매칭 기사(`appUserId=null`)는 driver_locations 항상 비어 있음 → Insung 좌표는 **vehicle→stops→signatures** 경유만.
- **priority/stale config**: `ArologisMatcherProperties.Gps`(priority `insung-lbs,app-gps,manual`·staleThresholdMs 60000) — **#804에서 `ArologisAdminController`에 이미 주입됨**. ⚠️ 토큰 매핑 갭: `app-gps` 1토큰 ↔ enum 2값(ACTIVE·BACKGROUND) → 토큰→enum 매핑기 필요.
- **FE 계약**(`InsungLbsPanel.tsx`): `GpsSource{source:GpsSourceKey, latitude:number|null, longitude:number|null, lastReceivedAt:string|null, active:boolean}`. active는 **BE가 priority 기준 산정**. FE는 렌더/정렬/stale 표시만·`gpsSources` optional(빈 배열 안전).
- **조립 지점**: `ArologisAdminController.findById`(L187~207)가 `driverIdToCode` batch를 만드는 자리에 GPS 수집 병렬 배치 → `DispatchDetailResponse.from` 시그니처 확장. `DispatchAggregate`엔 GPS 미포함(조립 레이어 병합).

## 구현 작업 목록

1. **BE DTO**: `GpsSource`(record: source/latitude/longitude/lastReceivedAt/active) + `VehicleDetail`에 `List<GpsSource> gpsSources` 추가.
2. **Repo read (driver_locations)**: 소스별 최신 조회 — 예 `findFirstByDriverIdAndSourceOrderByCapturedAtDesc(driverId, source)` (또는 driver당 소스별 최신 group). DDL 변경 없음.
3. **Insung 좌표 수집**: vehicle→stops→`SignatureRepository.findByStopIdAndSource(stopId, EXTERNAL_INSUNG_LBS)` 중 최신 capturedAt·non-null 좌표 1건 → GpsSource(라벨 배송시각·거의 항상 stale).
4. **토큰↔enum 매핑기**: `insung-lbs→EXTERNAL_INSUNG_LBS`, `app-gps→{APP_GPS_ACTIVE, APP_GPS_BACKGROUND}`, `manual→MANUAL`.
5. **active 산정기**: 소스별 최신 좌표를 config priority 순 정렬 → `now - lastReceivedAt > staleThresholdMs(60s)` 미초과 최상위 1건만 `active=true`. (Insung 스냅샷은 배송시각이라 대개 stale → 실시간 APP_GPS가 active 획득. `matcherProperties.getGps()` 사용.)
6. **MANUAL 관리자 수동입력 (신규·결정 2)**: `POST /admin/arologis/vehicles/{vehicleId}/manual-location {latitude, longitude}` → 차량의 assignedDriverId로 `DriverLocation.of(driverId, lat, lng, now(KST), MANUAL)` 적재. 권한=관리자. FE 게이트(수동 위치 입력 UI)는 이 슬라이스 or 후속(개발책임자 확인) — 최소 BE 엔드포인트 우선.
7. **조립 배선**: `ArologisAdminController.findById`(또는 신규 `GpsSourceAssembler`)에서 vehicle별 driverId·stops로 2·3 batch 수집 + 5로 active 산정 → `DispatchDetailResponse.from` 확장 주입. FE 게이트(gpsSources 소비) 원복.

## QA 계획 (실서버·라이브)

- driver_locations 시드(APP_GPS_ACTIVE 최근 + 오래된 BACKGROUND) + signatures(EXTERNAL_INSUNG_LBS 배송 좌표) → 배차 상세 GPS 패널: APP_GPS active·Insung stale 배송시각·MANUAL(수동입력 후) 노출 검증.
- HTTP IT: `ArologisAdminControllerIT` 확장(gpsSources jsonPath·active 산정·stale 경계 60s).
- 관리자 수동입력 IT: POST manual-location → 재조회 시 MANUAL 소스 등장.
- 실 GUI(arologis-desktop): 배차 상세 GPS 패널 스샷(소스별 뱃지·active·stale).

## 캐논 워크플로우

Opus 기획(본 spec·완료) → 조기 PR → Codex 개발 → Opus 5-agent+fix+라이브QA+게시 ↔ Codex 5-agent 적대+fix+게시 → 0수렴 → PM 종합 9-게이트 → CI → 머지.

## 잔여 확인 (착수 시)

- MANUAL 관리자 수동입력의 **FE UI 범위**(이 슬라이스 포함 여부) — BE 엔드포인트는 확정, FE 폼은 착수 시 개발책임자 재확인 권장.
- KST 시각 표준([[project_kst_timezone_standard]]) — capturedAt/now 적재 KST 정합.
