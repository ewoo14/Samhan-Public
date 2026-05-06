# Cloudflare Pages 배포 — GitHub Secrets 등록 가이드

## 1. 필요한 Secrets

| Secret 이름 | 용도 | 출처 |
|---|---|---|
| `CLOUDFLARE_API_TOKEN` | Cloudflare API 호출 (Pages publish) | Cloudflare 대시보드 → My Profile → API Tokens |
| `CLOUDFLARE_ACCOUNT_ID` | Pages project 가 속한 계정 식별 | Cloudflare 대시보드 → 우측 사이드바 Account ID |

`GITHUB_TOKEN` 은 GitHub Actions 가 자동 주입 (별도 등록 불필요).

## 2. Cloudflare API Token 발급

1. Cloudflare 대시보드 로그인 → 우상단 프로필 → **My Profile** → **API Tokens** 탭
2. **Create Token** 클릭
3. **Custom token** 선택 → **Get started**
4. Token name: `samhan-pages-deploy`
5. Permissions:
   - `Account` · `Cloudflare Pages` · **Edit**
   - `User` · `User Details` · **Read** (선택, deployment 식별용)
6. Account Resources: `Include` · 해당 Cloudflare 계정 1개
7. (선택) Client IP Address Filtering / TTL 제한
8. **Continue to summary** → **Create Token**
9. 표시된 토큰 값 즉시 복사 (재표시 X)

## 3. Account ID 확인

Cloudflare 대시보드 → 좌측 도메인 또는 Workers & Pages 진입 → 우측 사이드바 **Account ID** 32자리 hex 복사.

## 4. GitHub Secrets 등록

1. https://github.com/ewoo14/SamhanLogis/settings/secrets/actions
2. **New repository secret** 클릭
3. 두 개 등록:
   - `CLOUDFLARE_API_TOKEN` = (2단계에서 복사한 토큰)
   - `CLOUDFLARE_ACCOUNT_ID` = (3단계에서 복사한 ID)

## 5. Cloudflare Pages 프로젝트 사전 생성

Workflow 첫 실행 전 Cloudflare 콘솔에서 빈 프로젝트 1개 생성 (project name = workflow 의 `projectName` 와 일치).

1. Cloudflare 대시보드 → **Workers & Pages** → **Create** → **Pages** 탭 → **Direct Upload**
2. Project name: `samhan-order-app`
3. Production branch: `main`
4. Empty 상태로 Create (이후 GitHub Actions 가 산출물 push)

## 6. DNS 설정 (카페24 도메인 콘솔)

`samhan-air.com` DNS 가 카페24에 있는 경우:

1. 카페24 도메인 관리 → DNS 관리 → 호스트별 IP 설정
2. CNAME 레코드 추가:
   - Host: `order` → Target: `samhan-order-app.pages.dev`
3. Cloudflare Pages 프로젝트 → Custom domains → `order.samhan-air.com` 추가 → SSL 자동 발급 대기

## 7. 검증

- Workflow 수동 트리거: `gh workflow run deploy-order-app.yml --repo ewoo14/SamhanLogis`
- 또는 `clients/web/order-app/**` 변경 후 main push (paths 필터 자동 매치)
- 배포 후 `https://order.samhan-air.com/` HTML 안에 "주문서" 문자열 검증 (workflow smoke test step)

## 8. 토큰 회전

API Token 6개월마다 회전 권장:

1. Cloudflare → API Tokens → 기존 토큰 **Roll** (새 값 발급, 기존 즉시 무효)
2. GitHub Secrets → `CLOUDFLARE_API_TOKEN` **Update**
