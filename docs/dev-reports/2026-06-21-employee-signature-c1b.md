# 사원 서명 인감 에픽 — 슬라이스 C1b 개발 리포트 (핸드오프 토큰 · 공개 인증우회 표면)

> 2026-06-21 · PR #548 · user-service + api-gateway · 선행 = C1a(#547 머지)

## 1. 개요
모바일 핸드오프(QR 일회용 토큰 → 공개 웹앱 손서명 제출)의 BE/게이트웨이 표면. 관리자 desktop이 토큰을 발급하면 사원 폰이 무인증 공개 엔드포인트로 1회 제출한다. **보안 표면 슬라이스.**

## 2. 산출물
| 항목 | 내용 |
|---|---|
| 토큰 | `EmployeeSignatureHandoffToken`(SecureRandom 48B→base64url 64자, TTL 10분, 1회용) + **V11**(token UNIQUE + open-token partial index) |
| 발급/상태 | `POST /api/v1/admin/users/{id}/signature/handoff-token`(admin.users UPDATE) + `GET .../handoff/{token}/status`(VIEW) — 부서+권한 게이트 |
| 공개 제출 | `POST /api/public/employee-signatures/{token}` NO-AUTH — 만료 410·재사용 409·위조 404·hash 400·50KB 422·base64 90KB 400 |
| 게이트웨이 | `user-service-employee-signatures-public` 라우트(slip catch-all 앞, StripInboundIdentityHeaders + StripPrefix=1, JWT 없음) |
| 보안 | SecurityConfig `/public/**` permitAll + identity fail-CLOSED; shared `ErrorCode.TOKEN_EXPIRED(GONE)` |

## 3. 보안/동시성 설계
- **토큰**: 엔트로피 SecureRandom 48B, TTL 10분, `markUsed` 1회용, 재발급 시 미사용 토큰 soft-delete 무효화
- **공개 경로 fail-CLOSED**: 게이트웨이가 inbound X-User-* strip → downstream은 토큰만 신뢰
- **동시성 race 결정적 차단**: admin 직접 서명(@Transactional, Employee 행 pessimistic lock 보유한 채 register+revoke 원자) vs mobile submit(employeeId projection → Employee 락 → 토큰 FOR UPDATE 재조회) — 락 순서 Employee→Token 통일(데드락-free), 관리자 우선/stale 토큰 404
- **JPA**: submitPublic이 토큰 엔티티를 미리 적재하지 않도록 projection 사용 → FOR UPDATE 재조회가 fresh 엔티티 로드(동시 단일사용 보장)

## 4. 검증
- 테스트 **skipped=0**: HandoffTokenIT 6 · HandoffAdminIT 7 · **PublicEmployeeSignatureIT 9**(동시 단일사용 포함) · **PublicSignatureSecurityGateIT 4**(위조헤더 무시·경합) · UserPermissionIT 83(handoff 권한 매트릭스) · ApiGatewayContextLoadIT(공개 라우트 인벤토리)
- **V11 fresh Postgres probe** + **standalone 라이브 부팅** — [라이브 QA](../qa/employee-signature-c1b/live-qa.md)
- CI 전부 green (shared ErrorCode 변경 → shared+auth+gateway 잡 포함)

## 5. 듀얼 리뷰 (Opus 4.8 → Codex, 순차)
- 🔵Opus 5-lens 45→12 confirmed(P3/NIT, core 보안 정상) + 🟣Codex 독립(P1 CI·**P2 stale-token revoke 단독 적발**·P3 410 문자열매칭)
- fix2: revoke·410 ErrorCode·게이트웨이 가드 / fix3(+3b/3c): 동시성 race 결정적 차단(pessimistic lock + projection + @Transactional)
- 재수렴 사이클 3: 🔵Opus·🟣Codex **양쪽 CONVERGED**(원 P2 해소 확인)
- defer(문서화): 공개 라우트 rate limit(전 공개라우트 공통 플랫폼 과제, 토큰필수+TTL+90KB로 경계)

## 6. 다음
- **C2**: desktop 서명 모달(업로드+QR+폴링) + `clients/mobile-public` 공개 서명 페이지 → 본 PR 발급/상태/공개 제출 엔드포인트 소비
