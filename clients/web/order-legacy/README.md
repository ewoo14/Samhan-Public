# order-legacy — 거래처 주문서 legacy-v2 (Node.js + Express + EJS)

삼한공조시스템 거래처 주문서 web app, **버전 2 (legacy 100% 보존 + 이카운트 + 노션 + 거래처 인증)**.

- 대상 도메인: `order.samhan-air.com`
- Port: `5185`
- legacy 출처: `migration/source/scripts/partner-order/Code.js` (3,303 라인) + `index.html` (9,427 라인)
- plan: `docs/migration/phase-legacy-v2/M-LEGACY-V2-deployment.md`

## 디렉토리 구조

estimate-legacy 와 동일 구조. `lib/code.js` 는 partner-order 1:1 포팅 + 거래처 인증 흐름 (NOTION_DB_ID_AUTH).

## 기동

```bash
npm install
LEGACY_SRC_ROOT=/path/to/migration/source/scripts \
  node scripts/build-code.mjs && node scripts/convert-template.mjs
cp .env.example .env && nano .env
npm start
# → http://localhost:5185
```

## healthz

```bash
curl http://localhost:5185/healthz
```

## Docker

```bash
docker build -t samhan/order-legacy:v2.0.0 .
docker run --rm -p 5185:5185 --env-file .env samhan/order-legacy:v2.0.0
```

배포는 `infrastructure/cafe24/docker-compose.legacy-v2.yml` 참조.

## 변환 규칙

- `lib/code.js` 는 외부 호출 (UrlFetchApp.fetch) 사용 함수 21개 + 인증 간접 caller 를 async 화 + REDACTED 토큰 process.env 치환.
- 추가 shim: `Utilities.computeDigest` (SHA_256, hashPassword_ 용), `MailApp` / `GmailApp` stub (admin trigger).
