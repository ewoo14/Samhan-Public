---
name: identity 헤더 인가 안티패턴 — 게이트웨이 단일 권위 주입
description: 게이트웨이가 더는 주입 안 하는(C5-4) identity 헤더를 downstream 이 신뢰하면 fail-open(미주입→null→가드 skip) 또는 client-forge(위조 통과). 게이트웨이 strip+claim 재주입이 유일 신뢰원
metadata:
  type: feedback
---
2026-06-12 PR #466/#468 다모델 보안 리뷰 연쇄 발견. identity 헤더(X-User-*·X-Is-*·X-Partner-Code) 인가의 반복 결함 계열.

## 안티패턴 (4종 동형)
1. **게이트웨이 무strip 공개 라우트**(#465): JwtAuthentication 미적용 라우트가 위조 `X-Is-System-Master:true` 통과 → `PermissionAspect.isMasterBypass` 우회. → 공개 라우트 `StripInboundIdentityHeaders`.
2. **stale 헤더 fail-open**(#467 EmployeeController): 게이트웨이가 C5-4 에서 X-User-Role 주입 폐지했는데 컨트롤러가 여전히 `checkView(X-User-Role)` 만 쓰고 null→skip(fail-open) → 정상 요청 role=null → 무권한 PII 노출. → `@RequirePermission`(동적 권한) 일원화, fail-open 가드 제거.
3. **client-trusted self-scope 키**(#467 X-Partner-Code): self-scope 비교축을 클라이언트 주입 헤더로 신뢰 → 유효 JWT 보유자가 위조로 cross-tenant. → 게이트웨이가 JWT claim 으로 remove-then-set 강제 주입 + strip 목록 편입.
4. **/internal/ 토큰 가드 계층 부재**(#522 accounting · #525 inventory P1 — 2026-06-20 전수 sweep): 공유 `InternalTokenFilter` 가 `allow-missing-token=true`(default) 또는 `path-prefix` 가 해당 경로 미덮음(accounting=prometheus 전용) 이면 토큰무 통과 → `HeaderAuthenticationFilter` X-User-Id 단독 인증 → SecurityConfig `/internal` 가드 없고 메서드 `@PreAuthorize` 도 누락된 EP = **게이트웨이 우회 직접호출로 위조 X-User-Id 접근(fail-open)**. → 정답 = slip-service **P0-B 가드** `.requestMatchers("/internal/**").access((a,ctx)->INTERNAL_PRINCIPAL.equals(a.get().getName()))`(X-Internal-Token 으로 공유필터가 설정한 system-internal principal 만 통과). 🔑 **HeaderAuthenticationFilter 는 X-User-Role 무시**(위조 헤더는 `GROUP_` authority 만, ROLE_MASTER 획득 불가) → `@PreAuthorize("hasRole('MASTER')")` 보유 EP 는 위조 role 이미 차단(그래서 #525 에서 inventory by-code[가드 무]만 실 exploit, user/partner/notification/groupware/dashboard 5종은 defense-in-depth 격상).

## 규칙
- **identity/scope 헤더의 유일 신뢰원 = 게이트웨이의 서명검증 JWT claim 기반 remove-then-set**. 공개(무-JWT) 라우트는 strip-only. 클라이언트/서비스가 보낸 값 신뢰 금지.
- 신규 identity 헤더 추가 시 **반드시 `HttpHeaderConstants.INBOUND_IDENTITY_HEADERS`(단일 목록)에 편입** → JwtAuthentication remove + 공개 라우트 StripInboundIdentityHeaders 자동 적용.
- downstream 인가는 **fail-CLOSED**: 헤더 null/blank → deny(또는 권위 동적권한). null→skip(fail-open) 금지.
- **/internal/ 엔드포인트는 SecurityConfig 계층 가드(P0-B `.requestMatchers("/internal/**").access(INTERNAL_PRINCIPAL)`) 필수** — 메서드 `@PreAuthorize` 단일 의존 금지(신규 EP 1개 누락=즉시 fail-open, inventory by-code 가 그 사례). 신규 internal EP/서비스 추가 시 P0-B 가드 + `allow-missing-token:false` 확인. (잔존 점검: auth/product/dc-config 는 `allow-missing-token:false` YAML 단일 의존 — 계층 가드 추가 권장.)
- 보안 리뷰는 **결함 계열 전수 sweep**([[defect-family-sweep-fix]]) — 한 endpoint 게이팅 시 같은 PII/스코프 surface 의 형제 endpoint(list/getOne 외 lookup/org-chart 등) 동시 점검.

## How to apply
auth 헤더/게이트웨이 라우트/권한 가드 작업 시 위 3종 점검. 게이트웨이 = 단일 권위. 관련: [[x-user-name-header-charset-mockmvc]] [[pm-permission-autonomy]] [[fe-canaccess-pagecode-be-match]] [[defect-family-sweep-fix]] [[preauth-migration-lessons]].
