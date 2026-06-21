# C2 mobile-public 배포 origin + nginx 정적 서빙 메모

> 사원 서명 에픽 슬라이스 **C2 (등록 UX + 모바일 공개 웹앱)**. 본 문서 = `clients/web/mobile-public` 번들의 배포 origin·게이트웨이 라우팅·dev proxy 명세.
> 실 게이트웨이 yml 은 **C1b 가 이미 추가**(`application.yml` `user-service-employee-signatures-public`). 실 nginx 정적 서빙 와이어링은 **Phase 11 cutover** 작업.
> 작성: 2026-06-21 · 슬라이스 C2 (qrUrl 정합 C2.0 반영).

## 1. 빌드

- `cd clients/web/mobile-public && npm run build` → `dist/` (정적 SPA, vite + React).

## 2. 운영 웹앱 origin (= qrUrl base)

- 웹앱 origin = `https://sign.samhan-air.com` → 웹앱 페이지 `https://sign.samhan-air.com/s/:token` (사원이 폰으로 여는 SignaturePad 페이지).
- **권장 배치 = nginx 리버스프록시 same-origin**:
  - `/s/**` → mobile-public `dist` 정적 (nginx `try_files $uri /index.html` SPA fallback)
  - `/api/public/employee-signatures/**` → `proxy_pass` → `http://api-gateway:8080`
  - → 웹앱이 **same-origin** 으로 API POST (CORS 0)
- ⚠️ **api-gateway 는 reactive WebFlux(spring-cloud-gateway) 라 정적 파일 서빙 native 불가**(`spring-boot-starter-web` 금지 제약). 정적 서빙 주체는 **게이트웨이가 아니라 nginx**(`nginx-sign-deferred.md` 의 root `/var/www/sign-mobile` + `/public/` proxy_pass). 기존 Phase 5 deferred(DNS만, nginx 404) 해소 = 기존 nginx 404 를 mobile-public dist 로 교체.

## 3. 게이트웨이 공개 라우트 (C1b 머지 완료)

- `/api/public/employee-signatures/**` → user-service (`StripPrefix=1`, JwtAuthentication 미적용, `StripInboundIdentityHeaders`).
- 기존 `/api/public/**` → slip-service catch-all 보다 **더 구체 경로**라 우선순위 확보 (`application.yml` 선언 순서상 user-service 라우트가 먼저, first-match-wins). 경로 충돌 회피 충족.
- user-service SecurityConfig `/public/**` permitAll + identity 헤더 fail-CLOSED.

## 4. qrUrl origin 환경별 주입

- `app.signature.public-base-url` (env `SAMHAN_SIGNATURE_PUBLIC_BASE_URL`) — **Task C2.0 으로 경로 `/s/{token}` 확정**(C1b 초기본은 `/api/public/employee-signatures/{token}` API URL 발급 → 웹앱 페이지로 정합).
- ⚠️ **dev 는 폰이 PC 로 접속하므로 `localhost`/`127.0.0.1` 불가** → `SAMHAN_SIGNATURE_PUBLIC_BASE_URL=http://<PC-LAN-IP>:5185` (vite mobile-public).
- mobile-public 의 axios `baseURL` = 빈값(상대경로) → vite proxy `/api`→8080 이 API 를 게이트웨이로 중계 (same-origin). **`VITE_API_BASE_URL` 절대 주입 금지** — 절대 주입 시 폰(LAN)이 자기 `localhost` 직타 → 제출 실패 (`run-client-local-dev.cjs` 의 `web-mobile-public` env 는 `{}`).

## 5. 재사용

- 동일 앱이 slip 인수자 공개 서명(Phase 5 deferred)도 호스트 가능 → 향후 slip 핸드오프 unblock 자산.

## 환경별 정리

| 환경 | 웹앱 origin (qrUrl base) | API origin | `SAMHAN_SIGNATURE_PUBLIC_BASE_URL` | 정적 서빙 주체 |
|---|---|---|---|---|
| dev | `http://<PC-LAN-IP>:5185` | same-origin (vite proxy `/api`→8080) | `http://<PC-LAN-IP>:5185` | vite dev server |
| staging | `https://sign-stg.samhan-air.com` | same-origin (nginx `proxy_pass`→api-gateway) | `https://sign-stg.samhan-air.com` | nginx |
| prod | `https://sign.samhan-air.com` | same-origin (nginx `proxy_pass`→api-gateway) | `https://sign.samhan-air.com` | nginx |
