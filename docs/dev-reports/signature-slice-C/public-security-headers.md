# 공개 모바일 페이지 보안 헤더 (Slice C DevOps)

> 본 문서는 Slice C 모바일 공개 페이지 (`/public/**`) 응답에 적용할 보안 헤더 권장값과
> 적용 위치를 정리합니다. 출처: Designer `mobile-spec.md` §6 CSP 호환성 표 + OWASP
> Secure Headers Project (2024-12) 모범 사례.

## 1. 적용 위치

API Gateway (Spring Cloud Gateway, reactive) 의 신규 `WebFilter` 로 등록하여
`/api/public/**` path 응답에만 보안 헤더 부착.

- 신규 파일: `services/api-gateway/src/main/java/com/samhanair/logis/gateway/filter/PublicSecurityHeaderFilter.java`
- 동작: ServerWebExchange 의 request path 가 `/api/public/` 로 시작하면
  응답 헤더 5종 (CSP/X-Frame-Options/X-Content-Type-Options/HSTS/Referrer-Policy)
  + `noindex` (X-Robots-Tag) 추가
- 기존 `CorsConfig.corsWebFilter` 는 변경 없음 (CORS / 보안 헤더 책임 분리)
- BE agent 는 본 필터를 신규 작성, 기존 `JwtAuthenticationGatewayFilterFactory`
  는 변경 없음 (`/api/public/**` 라우트는 이미 Jwt 필터 미적용)

## 2. 권장 헤더 값

| 헤더 | 값 | 근거 |
| --- | --- | --- |
| `Content-Security-Policy` | `default-src 'self'; img-src 'self' data:; script-src 'self'; style-src 'self' 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'` | mobile-spec §6 + Canvas data URI 호환 + clickjacking 차단 |
| `X-Frame-Options` | `DENY` | iframe 임베딩 차단 (CSP `frame-ancestors` 호환 백업) |
| `X-Content-Type-Options` | `nosniff` | MIME sniffing 차단 (PNG bytea 응답 안전) |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` | HTTPS 강제 (Phase 5 nginx 적용 시 효력) |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | shareToken 외부 leakage 최소화 |
| `X-Robots-Tag` | `noindex, nofollow` | 인수자 view 검색엔진 인덱싱 차단 |
| `Permissions-Policy` | `geolocation=(), camera=(), microphone=()` | Canvas 만 허용, 기타 디바이스 권한 차단 |

CSP `style-src 'unsafe-inline'` 는 mobile mini bundle 의 inline `<style>` 한정 허용
(Designer mobile-spec §3 — vanilla JS + inline css for size budget).
별도 nonce/hash 적용은 Phase 5 web app 슬라이스에서 검토.

## 3. noindex 정책

mobile mini bundle 의 `<head>` 에 `<meta name="robots" content="noindex,nofollow">`
삽입 + 응답 헤더 `X-Robots-Tag: noindex, nofollow` 이중 적용.
사유: 인수자 view (`/share/{shareToken}`) 가 search engine 에 인덱스되면
PII (서명자명 + 거래처명) 노출 위험.

## 4. 헤더 미적용 경로

- `/api/auth/**`, `/api/users/**`, `/api/slips/**` 등 — JWT 보호 + 데스크톱 앱
  (Electron) 만 호출 → 브라우저 보안 헤더 의미 약함 (필요 시 Phase 5 web SPA
  슬라이스에서 별도 검토)
- `/actuator/**` — 내부 모니터링 전용

## 5. 검증 (QA 인계)

```bash
curl -sI https://api.samhan-air.com/public/signatures/test-token \
  | grep -E '(Content-Security|X-Frame|X-Content-Type|Strict-Transport|Referrer|X-Robots)'
```

QA 체크리스트:
- [ ] `/api/public/**` 응답에 6종 헤더 모두 존재
- [ ] `/api/slips/...` 응답에는 보안 헤더 미적용 (필터 path scoping 검증)
- [ ] CSP 위반으로 mobile mini bundle 동작 깨짐 없음 (Edge DevTools Console 0건)
- [ ] 인수자 view DOM `<head>` 안 robots noindex meta 존재
