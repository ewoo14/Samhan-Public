# CI 테스트 필터 = 패키지 allowlist 라 누락 패키지 false-green

**2026-06-01 발견 (S2 #338 회고).**

`.github/workflows/ci.yml` 의 slip 테스트 잡들은 **패키지 allowlist `--tests` 필터**로 실행한다:
- `slip-units`: `--tests "...slip.client.*" --tests "...slip.domain.*" --tests "...slip.delivery.domain.*" --tests "...slip.delivery.service.*" --tests "...slip.service.*"`
- `slip-it-core`: `--tests "...slip.it.*"`, `slip-it-public`: `--tests "...slip.delivery.it.*" --tests "...slip.publish.*"`

→ **이 필터에 없는 패키지(예: `slip.attachment.*`)의 테스트는 CI 에서 아예 실행되지 않는다.** `SlipPhotoAuditAdminControllerTest`(slip.attachment.web)가 #316 권한 enum 마이그레이션 이후 `action()` enum↔String 비교로 **상시 실패 상태였으나 CI green** 이었던 원인이 이것.

**왜**: gradle `--tests` 가 화이트리스트라, 신규 컨트롤러/패키지를 추가해도 CI 필터에 등재 안 하면 그 테스트는 침묵 미실행 → false-green.

**적용**:
- slip-service 에 **새 테스트 패키지를 추가하면 반드시 `ci.yml` 의 해당 잡 `--tests` 필터에도 추가**할 것. 신규 IT 는 `slip.it.*` 안에 두면 `slip-it-core` 가 자동 커버(S2 IT 가 그 경우).
- 차기 작업 시 **CI 필터 보강 별도 PR** 권장(누락 패키지 전수 등재 또는 exclusion 방식 전환). inventory 잡은 모듈 전체(`:inventory-service:test`, 필터 없음)라 안전.
- "CI green ≠ 전 테스트 통과" — 특히 slip-service. [[feedback_enforcement_real_http_test]](false-green 차단) 계열.

관련: date-bomb 테스트(하드코딩 월범위 조회)도 같은 PR 에서 6월 진입 시 노출 — 하드코딩 날짜 테스트 전수점검 필요.
