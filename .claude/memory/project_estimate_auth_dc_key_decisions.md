---
name: estimate-auth-dc-key-decisions
description: 2026-06-10 개발책임자 결정 — P0-B 전표발행 인증=X-Internal-Token, DC 통합키=partnerCode(=사업자번호 '-' 제외 동일값)
metadata:
  type: project
---

2026-06-10 개발책임자 확정 (GAS 정합성 에픽 결정 ②③ 해소):

1. **P0-B 전표발행 인증모델 = X-Internal-Token**: 웹 estimate-app(server-to-server) → slip-service `/api/v1/slips/from-estimate` 도달은 내부 토큰 헤더 검증. permitAll 금지, 로그인 헤더 포워딩 채택 안 함. P0-A snapshots permitAll 도 후속 동일 하드닝 검토 대상.
2. **DC설정 통합키**: **partnerCode = 사업자번호에서 '-' 를 제외한 값과 동일** — 별도 bizno↔partnerCode 매핑 불요. 레거시 bizno(숫자만) 키 = 우리 partnerCode 그대로 조회 가능.

3. **사용자 인증 모델(2026-06-10 확정)**: 일반 플랫폼(데스크톱)=로그인 계정(JWT/세션).
   - **종합견적서(estimate-app)=사원 인증**:
     - **삼한 퍼블릭 데스크톱/모바일에서 접속 시 = 로그인 생략**(플랫폼 세션 passthrough — 이미 로그인된 사원 신원/이메일을 estimate-app 에 전달, 별도 로그인 X).
     - 플랫폼 밖 standalone 접속 시 = 사원 자체 로그인(사원 계정 → user-service 인증).
     - 어느 경우든 신원=사원 이메일 → `/internal/users/by-email`(Employee 매칭, #31) 사원등록 확인. **현재 갭: req.query.email/DEFAULT_USER_EMAIL 신뢰경로 아님 → (a)플랫폼 passthrough 토큰 (b)standalone 로그인 으로 교체 필요.**
   - **주문서(order-app)=외부 거래처 인증**: 외부 거래처가 접속하는 페이지 → 우리 DB 에 등록된 거래처(사원 아님) **+ '사용 승인된 상태'인 거래처만 접속 가능**(단순 등록만으론 불가 — 승인 status 게이트). partner-auth-service(:8091, 휴대번호 passwordless 등) / partner 테이블 status 기준. 사원 by-email 과 별개 경로.

**Why**: 무인증 노출 없이 레거시 노션 서비스계정 패턴을 대체 + 거래처 키 이원화 제거. 별도 웹은 자체 로그인 체계가 없어 사원 이메일이 신원의 기준.
**How to apply**: P0-B 구현 시 slip-service 에 X-Internal-Token 필터(env secret) + estimate-app slip-bridge 헤더 주입. #29 DC설정 이식 시 dc-config 키 = partnerCode(=bizno digits), 레거시 13컬럼 수용. 관련 [[project-sheets-to-db-full-migration]], [[feedback_enforcement_real_http_test]] (토큰 게이트 실 HTTP 회귀 테스트 의무).
