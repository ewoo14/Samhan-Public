# BE Cycle 1 리뷰 — audit-slice-c-new-infra (PR #260)

**검토일**: 2026-05-19
**리뷰어**: Claude BE Agent (cycle 1)
**대상 범위**: `services/` + `shared/` diff (`git diff origin/main..HEAD`)

---

## 1. BE 변경 0 확인

`git diff origin/main..HEAD -- services/ shared/` 출력 없음. 본 PR 의 9개 변경 파일은 모두 QA/Detox/FE 정책 영역(`.github/`, `clients/`, `docs/`, `qa/`, `playwright/`)에 국한된다. slip-service 및 shared/common 코드 수정 없음.

---

## 2. signature-slice-C BE 구현 점검 결과

### 2-1. PublicSlipController
`/public/batches/{token}/slips/{slipNo}/signature` (POST), `/public/batches/{token}/slips/{slipNo}/driver-signature` (POST), `/public/signatures/{shareToken}` (GET) 3개 endpoint 정상 선언. 만료 토큰은 BusinessException(CONFLICT) → 410 GONE 매핑, 미발견은 NOT_FOUND → 404 위임 패턴 일관. UUID 비공개 가드 준수 — 응답 DTO 는 shareToken/slipNo/signedAt 만 노출.

### 2-2. SlipSignatureService
핵심 검증 5단계 완전 구현:
- PNG base64 디코드 + SHA-256 서버 재계산 + clientHash 비교 (hash mismatch → INVALID_INPUT)
- PNG 크기 ≤ 50KB 가드 (초과 → INVALID_INPUT)
- batch token 만료 가드 (CONFLICT → Controller 410 매핑)
- Slip 단계 가드 — INSPECTING/COMPLETED/SHIPPING 이상만 허용 (IllegalStateException → CONFLICT)
- audit INSERT — actorUserId=NULL(공개), SignatureSource.LINK/APP 분기 정확

### 2-3. PublicSignatureControllerIT (8 시나리오)
| 시나리오 | 기대 응답 | 구현 |
|---|---|---|
| 서명 happy path | 200 + shareToken | 정상 |
| hash mismatch | 400 | 정상 |
| PNG 60KB > 50KB | 400 | 정상 |
| 만료 batch token | 410 GONE | ReflectionTestUtils 강제 만료 |
| PROCESSING 단계 서명 시도 | 409 CONFLICT | 정상 |
| 인수자 view 정상 (UUID 미노출) | 200 | jsonPath id/slipId 부재 검증 |
| 미서명 share token | 404 | 정상 |
| 만료 share token | 410 GONE | ReflectionTestUtils 강제 만료 |

외부 client 전체 @MockBean 격리 (`feedback_it_mockbean_external_clients.md` 준수): InventoryClient, ProductClient, SmsGateway, UserInternalClient, WarehouseInternalClient.

---

## 3. 회귀 가능성

본 PR 은 BE 코드 무변경이므로 기존 IT 회귀 가능성 0. slipNo slug 정규화(`/` ↔ `-`) 로직은 canonicalSlipNo helper 에 격리되어 있으며 encodeSlipNo 테스트 helper 와 대칭 확인.

---

## 4. 결론

**결함 없음. BE 변경 0 확인 완료.** signature-slice-C BE 구현은 Plan §2/§5 명세를 완전 충족하며, 8 시나리오 IT 커버리지도 정상이다. 코드 수정 불필요.
