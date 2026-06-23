# PR #585 라이브 QA 증빙 — RestClient 계약테스트 배치5

> 2026-06-23. 워크플로우(리뷰마다 fix 후 라이브 QA) 보완. 백엔드 내부 계약 PR(UI 0).

## 1. 실 스택 기동 (Docker, mock OFF)

내 변경(SlipServiceClient 409→CONFLICT 등) 반영해 **재빌드·기동**. 13개 서비스 전부 healthy:
postgres·redis·rabbitmq·eureka·gateway·auth·partner·**product·inventory·slip-service·partner-order-service**(재빌드)·accounting·arologis. (slip=18086, partner-order=18088 호스트포트 override — 내부 포트·서비스간 URL 불변.)

## 2. 실행 중 slip-service 계약 = PR 계약테스트와 정확 일치 ✅

실 OpenAPI(`GET http://localhost:18086/v3/api-docs`) — `POST /api/v1/slips/from-partner-order` 응답코드:
```
responses: ['200', '201', '400', '409']
```
스크린샷 `01-slip-from-partner-order-contract.png`(실행 중 Swagger UI): **200 멱등재반환 / 201 신규발행 / 409 / 400** + Idempotency-Key·X-User-Id 헤더. → PR 의 SlipServiceClientTest(201 신규·200 멱등replay·409 CONFLICT·400) 인코딩과 1:1.

## 3. 실 엔드포인트 enforcement 응답 (live, 실 서비스)

| 호출 | 실 응답 | 의미 |
|---|---|---|
| 본문 깨짐(한글 Git Bash UTF-8) | `400 INVALID_INPUT "요청 본문 형식이 올바르지 않습니다"` | 실 body 검증 작동 |
| ASCII 본문 + 시스템 호출자 | `403 FORBIDDEN "동적 권한 deny — page=slip.publish.from-partner-order action=CREATE role=UNKNOWN reason=account permission missing"` | 실 권한 게이트 작동 |
| + X-User-Role: MASTER | `403 ... role=MASTER reason=account permission missing` | role 인식, account 권한 해석 |

→ 실 엔드포인트가 live 상태로 인증·검증을 enforce 함을 실증.

## 4. ✅ 성공 경로 실 발행 round-trip (개발책임자 승인 dev 시드 후)

내부 엔드포인트의 시스템 호출자(0000…) page 권한이 dev 스택에 미시드(프로덕션엔 시드됨)라 초기 403. **개발책임자 승인** 하에 dev-only 권한 시드(프로덕션 시드 복제) 후 실 발행 round-trip 측정 — 스크린샷 `02-real-publish-roundtrip-201-200-409.png`:

| 호출 | 실 응답 | 검증 |
|---|---|---|
| (1) 신규 발행(새 키+본문) | **201** · `slipNo:"2026/06/23-1"` · `idempotentReplay:false` | 201 신규 → published ✅ |
| (2) 멱등 replay(동일 키+본문) | **200** · 동일 `slipNo:"2026/06/23-1"` · `idempotentReplay:true` | 200 replay → published(동일 slipNo) ✅ |
| (3) 충돌(동일 키+다른 본문) | **409 CONFLICT** · **`data:null`** · "동일 Idempotency-Key 다른 본문(slipNo=2026/06/23-1)" | **409 → data=null → CONFLICT (BLOCKING fix 정확)** ✅ |

→ 실 DB 확인: `slips` 에 `slip_no=2026/06/23-1, source_type=PARTNER_ORDER, status=SENT` 실제 생성됨.
→ **이것이 처음 BLOCKING 을 잡았어야 할 라이브 QA**: 실 slip-service 가 409 에서 `data=null` 반환(slipNo 는 message 텍스트만)을 실증 → 원 테스트의 허구 계약(`409→data.slipNo→duplicate`)은 발생 불가, fix(409→CONFLICT)가 정확.
→ (정리) 시드한 dev-only 권한 행은 dev 스택 한정. 직접호출 권한 해석이 게이트웨이 JWT identity 부재로 fail-closed 되는 점은 [[local-stack-qa-gotchas]] 연장.

## 5. 계약 검증 종합

1. **실 발행 round-trip 201/200/409** (§4, 실 slip 생성)
2. 실행 중 slip-service **OpenAPI/Swagger** = 201/200/409/400 (§2, 스크린샷 01)
3. 실 slip-service **소스**: SlipPublishController Javadoc L44-46 + GlobalExceptionHandler→ApiResponse.fail→data=null
4. **계약테스트 24건** 통과(SlipServiceClient 13: 201/200replay/409 CONFLICT/401/403/…) + partner-order 모듈 **전체 351 tests 그린**(회귀 0)
