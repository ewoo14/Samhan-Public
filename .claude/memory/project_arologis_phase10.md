---
name: Phase 10 = arologis-service (배차 마이크로서비스), Phase 11 = AWS migration cutover (renumber)
description: Phase 10/11 번호 변경 사용자 결정 (2026-05-07) — arologis-service 도입이 AWS migration 보다 우선. 모바일 어플 = RN Expo (mobile-staff 패턴 일관). 외부 vendor = 인성데이타 퀵프로그램 (5만 기사 풀, 협약 단계 사용자 결정 대기)
type: project
originSessionId: 78cac99d-5dee-47ca-8254-3834a088f393
---
# Phase 10/11 renumber (2026-05-07 사용자 결정)

## 배경

기존 Phase 10 = AWS migration cutover plan. 사용자 명시 (2026-05-07) — AWS account 발급 추후 + 신규 배차 service "**아로로지스 (arologis)**" 우선 도입.

→ **Phase 10 = arologis-service** / **Phase 11 = AWS migration cutover** renumber.

## Phase 10 = arologis-service spec

| 항목 | 값 |
|---|---|
| service 명 | **arologis-service** (사용자 동의 2026-05-07) |
| 포트 | **8097** (기존 14 service 8081~8095 + 8096 migration 예약 다음) |
| DB | **arologis_db** (service-per-DB) |
| ServiceDiscoveryClient 소비자 | 5번째 |
| 모바일 어플 | RN Expo, **`clients/mobile-staff` 패턴 일관** (사용자 명시 2026-05-07) |
| 외부 vendor | **인성데이타 퀵프로그램** (5만 프리랜서 기사 풀, LBS 기반, 양방향 동기화 사례 존재) |

## 도메인 모델 (5 entity)

```
Dispatch (배차 1건 = 카톡 1 메시지)
 ├── dispatchDate, dispatchType (야상/주간)
 └── vehicles[]

Vehicle (차량 1대 = 카톡 "1." "2." 그룹)
 ├── sequence, tonnage (1톤/2.5톤)
 ├── stops[], assignedDriver
 └── status (PENDING / MATCHING / ASSIGNED / DEPARTED / DELIVERED / CANCELLED)

VehicleStop (정차 1건 = 카톡 라인)
 ├── sequence, rawText (원본 카톡)
 ├── parsedAddress, parsedPartnerCode (사업자명-전표번호)
 ├── notes (오전일찍 / 9시 / 9시까지배송요망)
 └── status (PENDING / ARRIVED / DELIVERED / FAILED)

Driver (배송기사)
 ├── driverCode (사용자 노출, UUID 비공개 가드)
 ├── phoneNumber, vehicleType
 ├── source (INTERNAL / EXTERNAL_insung_quick / EXTERNAL_sms 등)
 └── appInstalled, appUserId

Signature (전자서명, slip-service 연동 통합)
 ├── stopId, source (LINK / APP)
 ├── imageRef, capturedAt
 └── capturedLocation (GPS — 어플인 경우)
```

추가: **DriverLocation** (GPS 추적 — partition by date + 30일 자동 cleanup)

## 카톡 파싱 포맷

### 헤더
```
8일착 야상입니다
```
- `(\d+)일착\s*(.+)입니다` → dispatchDate (입력 시 컨텍스트 + 월 추정) + dispatchType

### 차량 그룹 (1, 2, 3, ...)
```
1. 상일+초월
상일상차
-인천남동구논현동755-1(하늘시스템-218)9시하차
초월상차
-인천 남동구 구월동(에스엠하나공조-214)아침8시
...
1톤
```
- 차량 시작: `^\d+\.\s` (숫자 + 점 + 공백)
- 차량 톤수: `(\d+(\.\d+)?)톤\s*$` (마지막 라인)

### 정차 라인
```
-인천 남동구 구월동(에스엠하나공조-214)아침8시
```
- `[-]\s*(.+?)\((.+?)-(\d+)\)(.*)` → address / partnerName / 전표번호 / notes

**파싱 정확도 80% 목표** + 수동 보정 UI 의무 (실제 사용 시 사용자 정정 가능)

## 5 슬라이스 plan (W10-1 ~ W10-5)

| W | 산출 | 진행 시점 |
|---|---|---|
| **W10-1** | arologis-service skeleton + 카톡 파싱 + 5 entity + DriverMatcher interface 추상화 + Mock vendor + 다른 service Internal API 통합 (partner / user / slip / notification) | 즉시 (사용자 trigger 받음 2026-05-07) |
| **W10-2** | 인성데이타 퀵프로그램 vendor 통합 (`InsungQuickDriverMatcher` impl) + 양방향 동기화 (배차 등록 / 기사 매칭 webhook / 배송 완료) | 사용자가 인성데이타 API 문서 + 인증 정보 확보 후 |
| **W10-3** | 모바일 어플 (RN Expo, **mobile-staff 패턴 일관**) — GPS 권한 의무 (거부 시 사용 불가) + 전자서명 캡처 + 본인 배차 목록 + 어플 설치 invite 흐름 | W10-1 머지 후 |
| **W10-4** | slip-service 전자서명 통합 — `signatureSource` 컬럼 추가 + Signature 통합 endpoint (LINK + APP 양쪽) | W10-3 머지 후 |
| **W10-5** | Phase 10 회고 + Phase 11 (AWS migration cutover) 진입 plan + 누적 backlog 정리 | W10-4 머지 후 |

## 외부 vendor — 인성데이타 퀵프로그램

### 정보 (web 조사 2026-05-07)

- 회사: 인성데이타(주), 2001년 4월 창립, LBS 기반 물류 솔루션
- 사업: 퀵서비스 / 대리운전 / **화물** / 레져 + 정부·공공기관 RFID·위험물 수송
- 기사 풀: 전국 약 **5만여 명 프리랜서** 배송 기사
- 외부 통합 사례: 프로브원 등 퀵서비스 창업 솔루션이 인성데이타 + 퀵프로그램 연동 → API 통합 가능
- 양방향 동기화: 본 시스템 → 인성 (배차 등록), 인성 → 본 시스템 (기사 매칭 / 상태 / 완료)
- 알림톡: 인성 자체 알림톡 (SMS 보다 저렴)
- 공식 홈페이지: www.insungdata.com

### 사용자 결정 (2026-05-07)

| # | 항목 | 결정 |
|---|---|---|
| 1 | 인성데이타 비즈니스 협약 단계 | **인성과 협의 필요** (W10-2 진입 전 사용자 협상 의무) |
| 2 | API 문서 / 인증 / 비용 정보 | **인성과 협의 필요** (W10-2 시작 trigger) |
| 3 | 알림톡 채널 분담 | **분리** — 배차 단계 알림 = 인성 알림톡 / 본 시스템 알림 (어플 설치 invite / 일반 사용자) = notification-service Aligo |
| 4 | GPS 분담 | **하이브리드** — 인성 LBS 우선 (어플 미설치 기사 추적) + 본 어플 GPS 보강 (어플 설치 기사 추적) |
| 5 | 통합 방식 (REST / SOAP / 파일 교환) | **인성과 협의 필요** (W10-2 시작 trigger) |

### W10-1 영향 (본 PR scope)

W10-1 = skeleton + Mock vendor 단계라 1/2/5번 협의 미완료 상태에도 시작 가능. 단:

- **결정 3 (알림톡 분리)** = `services/arologis-service/README.md` § 알림 분담 섹션 + dev-report 에 명시. W10-2 시점 인성 알림톡 호출 추가 (notification-service 우회 — 배차 단계는 인성 직접 호출).
- **결정 4 (GPS 하이브리드)** = `DriverLocationSource` enum 에 다음 값 의무:
  - `APP_GPS_BACKGROUND` (본 어플, 백그라운드)
  - `APP_GPS_ACTIVE` (본 어플, 활성 사용 중)
  - `EXTERNAL_INSUNG_LBS` (인성 LBS, W10-2 통합 시점 활성)
  - `MANUAL` (수동 입력 fallback)
  - 우선순위 정책 (`samhan.arologis.gps.priority=insung-lbs,app-gps,manual` 환경변수 토글) — W10-2 시점 활성 의무 약속

### W10-2 시작 trigger

1번 + 2번 + 5번 결정 완료 시점:
- 인성과 비즈니스 협약 체결
- API 문서 / 인증 키 / 비용 협상 완료
- 통합 방식 (REST API / SOAP / 파일 교환) 확정

→ 사용자가 위 정보 제공 시 W10-2 통합 TM spawn 진행

## 충돌 회피 가드

- 포트 8097 (8081~8095 14 service + 8096 migration 예약 다음)
- DB `arologis_db` 격리 (service-per-DB)
- BaseEntity 7 audit + Soft Delete + partial unique index 일관
- chained-default 환경변수 (`SAMHAN_AROLOGIS_*`)
- shared:user-client-abstraction 5번째 소비자
- ShedLock multi-instance 가드 (W4 패턴 일관)
- Phase 9 회귀 0 (slip-service signatureSource 추가 시점 회귀 검증)
- 사용자 가드 (`feedback_integrated_pr_pattern.md` § "fix 후속 PR/Phase 위임 금지") 일관 적용

## 관련 문서 갱신 의무 (W10-1 통합 TM 시점)

- 루트 `README.md` — Phase 10 = arologis-service 갱신
- `ROADMAP.md` — Phase 10 = arologis (W10-1~W10-5) + Phase 11 = AWS migration cutover (renumber)
- `docs/migration/phase10/M-PHASE-10-readiness.md` — **재작성** (기존 AWS plan → arologis plan)
- `docs/migration/phase11/M-PHASE-11-readiness.md` — **신규** (기존 phase10 AWS plan 이동)
- `migration/decisions/DECISIONS.md` — D-P10-01 ~ D-P10-XX (arologis 도입 결정)
