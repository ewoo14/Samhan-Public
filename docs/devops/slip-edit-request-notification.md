# 슬립 수정 요청 알림 — Production 적용 가이드 (PR-H3)

> Phase 12 Step 3 (PR-H3) — DevOps 알림 인프라 가이드
>
> slip-service 의 **슬립 수정 요청 (Slip Edit Request)** 라이프사이클
> (요청 / 수락 / 거절 / 만료) 에 따른 운영 환경 알림 발송 가이드.
> 1차 채널은 **Aligo SMS** (기존 운영), 2차 채널은 **Expo Push** (mobile-staff,
> 후속 PR 권고).

## 1. 배경

PR-H3 에서 영업직원은 출고 발행 (DRAFT → ISSUED) 이후 회계 처리 (POSTED) 전
구간에서 **사유와 함께 슬립 수정 요청** 을 관리자에게 발송할 수 있다. 관리자는
요청을 수락 / 거절하며, 24시간 내 무응답 시 자동 EXPIRED 처리된다 (스케줄러).

각 라이프사이클 전환 시점에 다음 대상에게 알림이 발송되어야 한다:

| 이벤트 | 발신자 (action user) | 수신자 (notify target) | 채널 (Phase 12) |
|--------|----------------------|------------------------|------------------|
| `REQUESTED` (요청) | 영업직원 (mobile-staff) | 관리자 그룹 (회계 / 슬립 담당) | SMS + (Expo push) |
| `ACCEPTED` (수락) | 관리자 (desktop) | 요청 영업직원 1명 | SMS + (Expo push) |
| `REJECTED` (거절) | 관리자 (desktop) | 요청 영업직원 1명 | SMS + (Expo push) |
| `EXPIRED` (만료) | 시스템 (스케줄러) | 요청 영업직원 1명 | SMS + (Expo push) |

**관련 환경변수**:

| 변수 | 위치 | 기본값 | 의미 |
|------|------|--------|------|
| `SAMHAN_SLIP_EDIT_REQUEST_EXPIRES_HOURS` | slip-service | `24` | 수정 요청 자동 만료 시간 (시간) |
| `SAMHAN_ALIGO_KEY` | notification-service | `CHANGE_ME_LOCAL_ONLY` | Aligo SMS API 키 (운영 시 환경변수 주입 필수) |
| `SAMHAN_ALIGO_USERID` | notification-service | `CHANGE_ME_LOCAL_ONLY` | Aligo 사용자 ID |
| `SAMHAN_ALIGO_SENDER` | notification-service | `CHANGE_ME_LOCAL_ONLY` | Aligo 발신 번호 (사전 등록 번호만) |

---

## 2. 알림 템플릿 (한국어)

본 템플릿은 notification-service `NotificationTemplate` 엔티티에 사전 등록되며,
SMS 본문은 LMS 90바이트 권고 (한글 ~45자) 를 초과하지 않도록 단문화한다.

### 2-1. 슬립 수정 요청 (REQUESTED)

- **수신자**: 관리자 그룹 (회계 / 슬립 담당, user-service `role IN (MANAGER, MASTER)`)
- **본문**:
  ```
  {slipNo} 슬립 수정 요청 — 사유: {reason}
  ```
- **변수 치환**:
  - `{slipNo}` — 슬립 비즈니스 번호 (예: `2026050001`)
  - `{reason}` — 영업직원 입력 사유 (template 측에서 30자 truncate, 초과 시 말줄임표)

### 2-2. 수정 요청 수락 (ACCEPTED)

- **수신자**: 요청 영업직원 1명
- **본문**:
  ```
  {slipNo} 수정 요청이 수락되었습니다
  ```

### 2-3. 수정 요청 거절 (REJECTED)

- **수신자**: 요청 영업직원 1명
- **본문**:
  ```
  {slipNo} 수정 요청이 거절되었습니다 — {reason}
  ```
- **변수 치환**:
  - `{reason}` — 관리자 입력 거절 사유 (template 측에서 30자 truncate)

### 2-4. 수정 요청 만료 (EXPIRED, 후속 PR 권고)

- **수신자**: 요청 영업직원 1명
- **본문**:
  ```
  {slipNo} 수정 요청이 24시간 무응답으로 만료되었습니다
  ```
- **참고**: PR-H3 본 PR 에서는 status 전환 + DB EXPIRED 만 구현, SMS 발송은
  후속 PR 권고 (스케줄러 cron + notification-service 호출 1건 추가).

---

## 3. SMS 발송 — Aligo (1차 채널, 기존 운영)

slip-service 는 수정 요청 라이프사이클 이벤트를 notification-service 에 위임
(domain event publish 또는 internal REST 호출) 하고, notification-service 가
Aligo API 로 발송한다 (기존 P0-2 / Slice B 패턴 동일).

**필수 사전 작업** (Phase 11 cutover 전):

- [ ] Aligo 발신 번호 사전 등록 (`SAMHAN_ALIGO_SENDER`) — 등록되지 않은 번호는
      Aligo 가 reject (Korean 정책 준수).
- [ ] Aligo 잔여 캐시 모니터링 알람 설정 — 발송 실패 시 retry 정책 (notification-service
      `samhan.notification.retry.max-attempts=5`) 으로 무한 retry 방지.
- [ ] 관리자 그룹 수신 번호 user-service 등록 확인 (휴대폰 번호 NULL → SMS skip + warn log).
- [ ] LMS / SMS 분기 검증 (90바이트 초과 시 자동 LMS 전환, 추가 비용).

**검증 명령** (Aligo 잔여 발송 가능 횟수):

```bash
curl -X POST 'https://apis.aligo.in/remain/' \
  -d "key=${SAMHAN_ALIGO_KEY}&user_id=${SAMHAN_ALIGO_USERID}"
```

---

## 4. Expo Push — mobile-staff (2차 채널, 후속 PR 권고)

mobile-staff (RN Expo) 는 영업직원 native 앱이며, 향후 **Expo Push Token** 등록
+ notification-service `ExpoPushAdapter` 신규 구현으로 SMS 와 병행 발송 권고.

### 4-1. 사전 검토 — Expo Push Token 발급 흐름

1. **Expo Project 생성** (mobile-staff 1회):
   - `expo-notifications` + `expo-device` 패키지 추가.
   - `app.json` 에 `expo.notification.icon` + `expo.android.googleServicesFile` 설정.
   - Android 는 FCM credentials (`google-services.json`) 필요 → notification-service
     기존 `SAMHAN_FCM_*` 환경변수 재활용 가능.
2. **앱 설치 후 token 발급** (영업직원 단말 1대당 1회):
   - 로그인 직후 `Notifications.getExpoPushTokenAsync()` 호출.
   - 응답 token (`ExponentPushToken[xxxxxxxx]`) 을 user-service `user_devices` 테이블에
     `(user_id, expo_push_token, platform)` upsert.
3. **로그아웃 / 단말 변경 시**:
   - 로그아웃 시 token 삭제 (다른 사용자에게 push 발송 방지).
   - 단말 OS 업데이트 / 앱 재설치 시 token 변경 가능 → 매 로그인 시 재등록.

### 4-2. notification-service 측 신규 구현 (후속 PR 범위)

- `ExpoPushAdapter` 신규 (Aligo / SMTP / FCM 와 동일 패턴, `NotificationChannel.PUSH`).
- Expo Push API endpoint: `https://exp.host/--/api/v2/push/send`
- 인증: Expo accessToken (Expo dashboard 발급) — notification-service 환경변수
  `SAMHAN_EXPO_ACCESS_TOKEN` 신규 추가 권고.
- Rate limit: Expo 기본 600 token / batch / 초 — bulk 발송 시 chunk 분할 필요.
- 발송 결과 receipt 조회 (비동기) → notification_attempt 테이블에 receipt_id 저장,
  스케줄러로 receipt 결과 polling.

### 4-3. PR-H3 본 PR 의 범위 외

PR-H3 본 PR 은 SMS (Aligo) 발송 가이드 + 만료 시간 환경변수만 포함하며, Expo Push
신규 adapter 구현은 후속 PR 의무로 분리한다. 본 가이드의 §4 는 후속 PR 사전 검토
참조용 사양 정리이다.

---

## 5. Phase 11 cutover 시 적용 체크리스트

- [ ] `SAMHAN_SLIP_EDIT_REQUEST_EXPIRES_HOURS=24` slip-service EC2 systemd unit 또는
      Docker env 에 주입 (default 24h 그대로 사용 시 생략 가능).
- [ ] `SAMHAN_ALIGO_KEY` / `SAMHAN_ALIGO_USERID` / `SAMHAN_ALIGO_SENDER` notification-service
      환경변수에 운영 값 주입 (AWS Secrets Manager 권장, Phase 11 표준).
- [ ] `samhan.notification.retry.max-attempts=5` 기존 default 유지 (Aligo 일시 장애 보호).
- [ ] notification-service `NotificationTemplate` seed 4건 (REQUESTED / ACCEPTED /
      REJECTED / EXPIRED) Flyway migration 등록 — 후속 PR.
- [ ] 관리자 그룹 (MANAGER / MASTER) 휴대폰 번호 user-service 등록 검증.
- [ ] CloudWatch 또는 Prometheus 로 slip 수정 요청 발송 성공/실패 비율 alarm 설정
      (성공률 < 95% 시 알람).
- [ ] (후속 PR) Expo Push adapter + `SAMHAN_EXPO_ACCESS_TOKEN` 환경변수 추가.

---

## 6. 트러블슈팅

| 증상 | 원인 후보 | 조치 |
|------|----------|------|
| SMS 발송 실패 (Aligo error 1101) | 발신 번호 미등록 | Aligo 콘솔에서 sender 사전 등록 |
| SMS 발송 실패 (Aligo error 1100) | API 키 오류 | `SAMHAN_ALIGO_KEY` 환경변수 재확인 |
| 본문 자동 LMS 전환 (추가 비용) | 본문 90바이트 초과 | 템플릿 `{reason}` 30자 truncate 검증 |
| 일부 영업직원만 알림 미수신 | user-service 휴대폰 번호 NULL | user-service 프로필 휴대폰 번호 등록 |
| 만료 알림 미발송 | EXPIRED 스케줄러 미구현 (PR-H3 범위 외) | 후속 PR 에서 `@Scheduled` cron + notification 호출 추가 |
| 관리자 그룹에 중복 발송 | 동일 사용자 다중 휴대폰 번호 등록 | user-service primary phone 단일화 |

---

## 관련 문서

- [`services/slip-service/src/main/resources/application.yml`](../../services/slip-service/src/main/resources/application.yml)
- [`services/notification-service/src/main/resources/application.yml`](../../services/notification-service/src/main/resources/application.yml)
- [`docs/devops/realtime-sse-production.md`](realtime-sse-production.md) — Phase 12 Step 1 SSE 가이드
- [`docs/devops/redis-realtime-broker.md`](redis-realtime-broker.md) — Phase 12 Step 2 Redis 가이드
- Phase 11 AWS 단일 환경 결정 (DECISIONS — Phase 11 entry)
