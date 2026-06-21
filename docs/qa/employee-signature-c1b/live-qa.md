# C1b 라이브 실서버 QA — 핸드오프 토큰 + 공개 인증우회 표면 (user-service + gateway)

> 2026-06-21 · PR #548 · **실 jar standalone 부팅 + 실 Postgres** (가짜·목업 없음)
>
> C1b BE/보안 슬라이스(사용자 UI 없음 — UI는 C2). 라이브 캡처 = 실서버 HTTP. 게이트웨이 라우팅/strip은 `ApiGatewayContextLoadIT`(실 라우트 정의 로드)로 검증.

## 부팅 (실 Postgres + Flyway V1..V11)
```
Flyway: Migrating schema "public" to version "11 - add employee signature handoff token"
Hibernate ddl-auto=validate → 통과 (핸드오프 토큰 엔티티 ↔ 실 스키마 일치)
Tomcat started on port 18084 · Started UserServiceApplication in 14.0s
```

## POST /public/employee-signatures/{token} (NO-AUTH 토큰 게이트, 실 curl)
실 PNG 70 bytes, sha256 `4ff6…80b5`. dev_master(`a0000000-…-0001`)에 토큰 시드.

| 케이스 | 결과 |
|---|---|
| **유효 토큰 정상 제출** | **200** — dev_master 서명 등록(`has_png=true, 70B, channel=MOBILE_CANVAS, hash=4ff6…`) + audit `RECORD/MOBILE_CANVAS/actor=NULL` + 토큰 소진(`used=true`) |
| 재사용(소진 토큰) | **409** CONFLICT |
| 만료 토큰 | **410** GONE — body `{"code":"TOKEN_EXPIRED","message":"토큰이 만료되었습니다"}` (전용 ErrorCode, 문자열 매칭 제거 검증) |
| 위조/미발견 토큰 | **404** NOT_FOUND |
| base64 90000자 초과 | **400** — 디코드 전 `@Size` (무인증 표면 DoS 가드) |
| signatureHash 1자 | **400** — `@Size(min=64,max=64)` |

## TZ 주의 (QA 환경 아티팩트)
초기 시드는 Postgres 컨테이너=UTC, JVM=시스템 KST 불일치로 유효 토큰이 410 오판. 코드 무관 — prod는 PR #479로 DB(`-c timezone`)+JVM(`-Duser.timezone`) 모두 KST 일치. Postgres 세션 `SET timezone='Asia/Seoul'` 재시드 후 200 정상. (핸드오프 TTL/만료가 시스템 TZ 일관성에 의존함을 라이브로 실증.)

## 동시성 (IT)
- `PublicEmployeeSignatureControllerIT.동일_토큰_동시제출은_한번만_성공하고_나머지는_409` — Executor 2-thread 실 Testcontainers Postgres에서 1 success/1 conflict (employee 행 락 직렬화 + projection fresh 재조회)
- `관리자_업로드_커밋_후_기존_토큰_공개제출은_404` — admin 직접 서명 후 stale 토큰 revoke → 404 + hash 유지

## 결론
실 jar + 실 Postgres + Flyway V11 + ddl validate + 실 Tomcat + 공개 토큰 게이트 + 전용 410 ErrorCode + 디코드 전 @Size 가드를 라이브 실증. 위협모델(만료/재사용/위조/크기) 전수 + 정상 등록 확인. BLOCKER 0.
