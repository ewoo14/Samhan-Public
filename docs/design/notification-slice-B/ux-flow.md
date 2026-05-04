# UX Flow — Notification Slice B 사용자 흐름 시나리오

본 문서는 4가지 사용자 시나리오를 순차 액션 / 화면 전환 / 시스템 응답 단위로 명세합니다. FE / BE / QA agent 는 본 흐름의 각 step 에서의 응답을 인용하여 구현·검증합니다.

---

## 시나리오 1 — 관리자 출고전표 발급 → SMS 발송 (정상 happy-path)

### 등장인물

- **관리자** (홍지수, MANAGER 권한)
- **시스템** (slip-service + Solapi SmsGateway)
- **기사** (김기사, 010-1234-5678) — 본 시나리오에서는 SMS 수신만

### 흐름

| step | actor | 액션 / 응답 |
| ---- | ----- | ----------- |
| 1 | 관리자 | SlipFormPage 진입 → 거래처 "한국전력" 선택, 라인 3건 입력 |
| 2 | 관리자 | 헤더 운송 정보 섹션 → 기사명 "김기사" / 기사 연락처 "010-1234-5678" 입력 |
| 3 | 시스템 | PhoneInput 자동 하이픈 (`01012345678` → `010-1234-5678`) |
| 4 | 관리자 | [저장] 클릭 → POST `/slips` (status=SAVED, driverName/driverPhone 포함) |
| 5 | 관리자 | 추가 슬립 2건도 같은 driverPhone 으로 저장 |
| 6 | 관리자 | 사이드바 "링크발송" 클릭 → LinkDispatchListPage 이동 |
| 7 | 시스템 | GET `/delivery-batches?date=오늘` → 빈 배열 (자동 그룹 미실행 상태) |
| 8 | FE | 빈 표 + 안내 메시지 "슬립 발급 후 [날짜 자동 그룹] 클릭" |
| 9 | 관리자 | 상단 [날짜 자동 그룹] 버튼 클릭 |
| 10 | 시스템 | POST `/delivery-batches/auto-group?date=2026-05-05` |
| 11 | BE | driverPhone="010-1234-5678" + date="2026-05-05" 의 미배치 슬립 3건 발견 → DeliveryBatch 1건 생성 (batchToken 발급, tokenExpiresAt = 2026-05-06 23:59) |
| 12 | BE | 응답: `{ created: 1, updated: 0, batches: [{ id, batchToken, slipCount: 3 }] }` |
| 13 | FE | 토스트 "1건 그룹화 완료" + 표 갱신 (1행 추가, unsent 상태 #FFFFFF 배경) |
| 14 | 관리자 | 행 우측 [SMS 발송] 버튼 클릭 |
| 15 | FE | BatchStatusCell loading state (spinner) + 버튼 disabled |
| 16 | 시스템 | POST `/delivery-batches/{id}/send-sms` |
| 17 | BE | SmsGateway.sendSms("010-1234-5678", "오늘 배송 3건: https://sign.samhan-air.com/d/aB3kL...") |
| 18 | BE | Solapi 응답 200 OK → DeliveryBatch.markSmsSent() → smsSentAt = 14:32:18 |
| 19 | BE | 응답: `{ smsSentAt: "2026-05-05T14:32:18+09:00" }` |
| 20 | FE | BatchStatusCell sent state 전환 (☑ 14:32 [재발송]) + 행 배경 #F0F9FF 옅은 파랑 + 토스트 "SMS 발송 완료" |
| 21 | 기사 | 휴대폰 SMS 수신: "오늘 배송 3건: https://sign.samhan-air.com/d/aB3kL..." |

### 디자인 검증 포인트

- step 3: PhoneInput 자동 하이픈 동작 (1.6의 입력 인터랙션)
- step 8: 빈 표 안내 메시지 표시 (empty state)
- step 13: 그룹화 후 행 추가 + unsent 시각 (`--batch-list-row-unsent-bg`)
- step 15: BatchStatusCell loading spinner + 버튼 disabled
- step 20: sent state 전환 visual diff (행 배경 색상 변화 — `120ms ease-out` motion)

---

## 시나리오 2 — 관리자 잘못 그룹된 슬립을 다른 배치로 이동

### 상황

자동 그룹 후, 김기사 배치에 잘못 매핑된 슬립 1건을 박기사 배치로 이동.

### 흐름

| step | actor | 액션 / 응답 |
| ---- | ----- | ----------- |
| 1 | 관리자 | LinkDispatchListPage 표 — 김기사 행 클릭 |
| 2 | FE | BatchDetailModal 오픈 — 슬립 3건 리스트 표시 |
| 3 | 관리자 | 슬립 "2026/05/05-3 (삼성전자)" 우측 [제거] 클릭 |
| 4 | FE | ConfirmDialog "이 슬립을 다른 배치로 옮기시겠습니까? 슬립의 배치 매핑이 해제됩니다." |
| 5 | 관리자 | [확인] 클릭 |
| 6 | 시스템 | DELETE `/delivery-batches/{kimId}/slips/{slipId}` |
| 7 | BE | DeliveryBatch.removeSlip(slip) → slip.deliveryBatchId = null. 김기사 배치 슬립 카운트 3 → 2 |
| 8 | FE | 모달 표 갱신 (2건), 토스트 "슬립이 제거되었습니다" |
| 9 | 관리자 | 모달 [닫기] → 박기사 행 클릭 |
| 10 | FE | BatchDetailModal 오픈 — 박기사 슬립 1건 |
| 11 | 관리자 | [+ 슬립 추가] 클릭 |
| 12 | FE | inline search 입력 "2026/05/05-3" → autocomplete 드롭다운 |
| 13 | 시스템 | GET `/slips?driverPhone=null&date=2026-05-05&search=2026/05/05-3` |
| 14 | BE | 미배치 슬립 1건 응답 |
| 15 | 관리자 | 드롭다운에서 선택 |
| 16 | 시스템 | POST `/delivery-batches/{parkId}/slips` body `{ slipId }` |
| 17 | BE | DeliveryBatch.addSlip(slip) → slip.deliveryBatchId = parkId. 박기사 배치 슬립 카운트 1 → 2 |
| 18 | FE | 모달 표 갱신 (2건) + 표 행 갱신 + 토스트 "슬립이 추가되었습니다" |
| 19 | 관리자 | [닫기] |

### 디자인 검증 포인트

- step 4: ConfirmDialog 텍스트 명확성 (slip → batch 분리/병합 의미)
- step 12: autocomplete 드롭다운 — 미배치 슬립 만 표시 (이미 배치된 슬립은 excluded)
- step 18: 모달 + 표 동시 갱신 (state sync — React Query invalidation 권장)

---

## 시나리오 3 — 기사 (모바일) SMS 링크 클릭 → 배치 슬립 리스트 확인

### 등장인물

- **기사** (김기사, 010-1234-5678) — iPhone Safari
- **시스템** (slip-service public endpoint)

### 흐름

| step | actor | 액션 / 응답 |
| ---- | ----- | ----------- |
| 1 | 기사 | 휴대폰 SMS 수신: "오늘 배송 3건: https://sign.samhan-air.com/d/aB3kL..." |
| 2 | 기사 | SMS 안 링크 탭 → Safari 자동 오픈 |
| 3 | 시스템 | GET `/public/batches/aB3kL...` |
| 4 | BE | 토큰 검증 — UNIQUE constraint 매칭 + tokenExpiresAt 비교 |
| 5 | BE | 토큰 유효 → 응답: `{ batch: { driverName, batchDate, slipCount }, slips: [{ slipNo, partnerName, address, totalAmount }] }` |
| 6 | FE (mobile) | 자체 mini bundle (mobile.css/js) 로드 |
| 7 | FE (mobile) | 배치 헤더 카드 렌더 ("오늘 배송 / 김기사 — 3건 / 2026/05/05 (목)") |
| 8 | FE (mobile) | 슬립 카드 3건 렌더 (각각: 거래처명 + 주소 + 슬립번호 + 합계 + [상세보기 →]) |
| 9 | 기사 | 첫 번째 슬립 카드 [상세보기 →] 탭 |
| 10 | 시스템 | GET `/public/batches/aB3kL.../slips/{slipId}` |
| 11 | BE | 슬립 단건 응답 (라인 N건 포함) |
| 12 | FE (mobile) | (Slice C) 서명 캡처 페이지 진입. **본 Slice B 에서는 read-only 상세 만 표시** |
| 13 | 기사 | (Slice C) 인수자 서명 받음 → [인수자에게 공유] 탭 → Web Share API |

### 디자인 검증 포인트

- step 6: mini bundle 사이즈 ≤ 12KB
- step 7: 배치 헤더 카드 — `--m-font-h-card` 18px, driverName + 슬립수 강조
- step 8: 슬립 카드 — 한 카드 안에 거래처/주소/슬립번호/합계 (UUID 노출 X)
- step 8: [상세보기 →] 우측 정렬, tap target ≥ 44px (`--m-tap-min`)
- step 12: (Slice C) 본 Slice B 에서는 placeholder — "Slice C 에서 활성"

---

## 시나리오 4 — 만료된 토큰 접근 시 410 GONE 페이지

### 상황

배송일이 2026-05-05 였던 배치의 토큰이 +1일 후 (2026-05-07) 만료. 기사가 다음날에 SMS 링크 재클릭.

### 흐름

| step | actor | 액션 / 응답 |
| ---- | ----- | ----------- |
| 1 | 기사 | SMS 안 링크 재탭 (배송일 +2일 후) |
| 2 | 시스템 | GET `/public/batches/aB3kL...` |
| 3 | BE | 토큰 매칭 OK + tokenExpiresAt < now → 410 GONE 응답 |
| 4 | BE | 응답: `{ error: "TOKEN_EXPIRED", expiredAt: "2026-05-06T23:59:00+09:00" }` |
| 5 | FE (mobile) | 410 GONE 화면 렌더 (mobile.css `.m-error-page` class) |
| 6 | FE (mobile) | "링크가 만료되었습니다. / 배송일이 지나 더 이상 접근할 수 없습니다. / 문의가 필요한 경우 관리자에게 연락해 주세요." |
| 7 | FE (mobile) | tap-to-call 버튼 "📞 02-XXXX-XXXX" (`<a href="tel:...">`) |
| 8 | 기사 | tap → iPhone 통화 앱 자동 오픈 |

### 만약 관리자가 토큰 재발급

| step | actor | 액션 / 응답 |
| ---- | ----- | ----------- |
| A | 관리자 | LinkDispatchListPage 행 클릭 → BatchDetailModal |
| B | 관리자 | "토큰 만료" 옆 [재발급] 버튼 클릭 |
| C | FE | ConfirmDialog "새 링크를 발급하시겠습니까? 기존 링크는 무효 처리됩니다." |
| D | 시스템 | POST `/delivery-batches/{id}/regenerate-token` |
| E | BE | 새 batchToken 생성 + tokenExpiresAt 갱신 (now + 1일) |
| F | FE | 토스트 "새 링크가 발급되었습니다" + 모달 갱신 |
| G | 관리자 | 새 [SMS 발송] 클릭 → 시나리오 1의 step 14 ~ 21 동일 |

### 디자인 검증 포인트

- step 5: 410 GONE 화면 — info icon + 명확한 한국어 안내 + tap-to-call
- step 7: `tel:` link — iOS / Android 모두 통화 앱 호출
- step C: confirm dialog 명시적 (토큰 무효화 의미)

---

## 5. 권한 / 에러 매트릭스

| 상황 | HTTP | 화면 |
| --- | --- | --- |
| 비인증 사용자가 `/sales/link-dispatch` 접근 | 401 → 로그인 페이지 redirect | 기존 AuthGuard 동작 |
| OPERATOR 사용자가 `/sales/link-dispatch` 접근 | 403 | "권한이 없습니다" 화면 |
| 잘못된 토큰 `/public/batches/INVALID` | 404 | 410 GONE 과 동일 화면 (보안: 토큰 존재 여부 노출 X) |
| 만료 토큰 | 410 | 410 GONE 화면 |
| Solapi SMS 발송 실패 | 500 + smsLastError | 토스트 danger + ⚠ icon 표시 |
| 자동 그룹 결과 0건 | 200 + `{ created: 0 }` | 토스트 info "그룹화할 슬립이 없습니다" |
| 슬립 추가 시 이미 다른 배치에 매핑 | 409 | 토스트 danger "이미 다른 배치에 속한 슬립입니다" |

---

## 6. 모션 / 트랜지션

| 화면 | 트랜지션 | duration |
| --- | --- | --- |
| BatchStatusCell unsent → sent | 행 배경 색 변화 | `--motion-hover` 120ms ease-out |
| CopyButton 성공 flash | 배경 초록 → 원래 | 200ms |
| BatchDetailModal 오픈 | fade-in + slide-up 8px | `--motion-modal` 180ms |
| ConfirmDialog 오픈 | fade-in | 120ms |
| 모바일 페이지 진입 | 즉시 (no transition) | — |
| 모바일 [상세보기] 탭 | 페이지 transition (Slice C) | TBD |

본 슬라이스에서는 모션 신규 정의 없음 — 기존 Slice A 토큰 (`--motion-hover`, `--motion-modal`) 재사용.
