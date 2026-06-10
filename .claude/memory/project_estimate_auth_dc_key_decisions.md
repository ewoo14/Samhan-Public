---
name: estimate-auth-dc-key-decisions
description: 2026-06-10 개발책임자 결정 — P0-B 전표발행 인증=X-Internal-Token, DC 통합키=partnerCode(=사업자번호 '-' 제외 동일값)
metadata:
  type: project
---

2026-06-10 개발책임자 확정 (GAS 정합성 에픽 결정 ②③ 해소):

1. **P0-B 전표발행 인증모델 = X-Internal-Token**: 웹 estimate-app(server-to-server) → slip-service `/api/v1/slips/from-estimate` 도달은 내부 토큰 헤더 검증. permitAll 금지, 로그인 헤더 포워딩 채택 안 함. P0-A snapshots permitAll 도 후속 동일 하드닝 검토 대상.
2. **DC설정 통합키**: **partnerCode = 사업자번호에서 '-' 를 제외한 값과 동일** — 별도 bizno↔partnerCode 매핑 불요. 레거시 bizno(숫자만) 키 = 우리 partnerCode 그대로 조회 가능.

3. **사용자 인증 모델(2026-06-10 추가)**: 일반 플랫폼(데스크톱)=로그인 계정(JWT/세션). **별도 웹(종합견적서·주문서 estimate-app/order-app)=사원등록된 사원의 이메일주소로 인증** — GAS 의 노션 이메일 인증을 우리 Employee(사원) 테이블 이메일 매칭으로 대체. checkUserAuth → user-service `/internal/users/by-email`(#31) 이 이 정책 구현. **갭: estimate-app 이 이메일을 req.query.email/DEFAULT_USER_EMAIL 에서 취득 → 신뢰 경로 아님(사칭 위험). 로그인된 사원에게서 신뢰 이메일 확보 메커니즘 필요(개발책임자 결정 대기).**

**Why**: 무인증 노출 없이 레거시 노션 서비스계정 패턴을 대체 + 거래처 키 이원화 제거. 별도 웹은 자체 로그인 체계가 없어 사원 이메일이 신원의 기준.
**How to apply**: P0-B 구현 시 slip-service 에 X-Internal-Token 필터(env secret) + estimate-app slip-bridge 헤더 주입. #29 DC설정 이식 시 dc-config 키 = partnerCode(=bizno digits), 레거시 13컬럼 수용. 관련 [[project-sheets-to-db-full-migration]], [[feedback_enforcement_real_http_test]] (토큰 게이트 실 HTTP 회귀 테스트 의무).
