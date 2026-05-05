# estimate-legacy — 종합견적서 legacy-v2 (Node.js + Express + EJS)

삼한공조시스템 종합견적서 web app, **버전 2 (legacy 100% 보존 + 이카운트 + 노션)**.

- 대상 도메인: `quote.samhan-air.com`
- Port: `5184`
- legacy 출처: `migration/source/scripts/estimate/Code.js` (2,837 라인) + `index.html` (18,614 라인)
- plan: `docs/migration/phase-legacy-v2/M-LEGACY-V2-deployment.md`

## 디렉토리 구조

```
estimate-legacy/
├── Dockerfile                       node:20-alpine, healthcheck
├── .dockerignore
├── .env.example                     placeholder 시크릿
├── package.json                     express + ejs + googleapis + axios + @notionhq/client
├── server.js                        Express bootstrap (port 5184)
├── routes/
│   ├── index.js                     GET / — bootstrap → views/index.ejs
│   ├── healthz.js                   GET /healthz — Sheets/eCount/Notion 상태
│   └── rpc.js                       POST /rpc/:fn — google.script.run dispatch
├── lib/
│   ├── apps-script-shim.js          Apps Script global API 호환층 (SpreadsheetApp/UrlFetchApp/...)
│   ├── google-sheets-client.js      Service Account JWT + 5분 cache
│   ├── ecount-client.js             /proxy/ecount/{zone,login,sale,saleorder,inventory}
│   ├── notion-client.js             /v1/pages, /v1/databases/{id}/query
│   └── code.js                      legacy estimate Code.js 1:1 (자동 생성)
├── views/
│   └── index.ejs                    legacy estimate index.html 1:1 (자동 생성)
├── public/
│   └── assets/                      logo / stamp / samhan / NanumGothic 등 (자동 복사)
└── scripts/
    ├── build-code.mjs               Code.js → lib/code.js 변환
    └── convert-template.mjs         index.html → views/index.ejs 변환
```

## 기동

```bash
# 의존성 설치
npm install

# (옵션) legacy 원본이 변경되었다면 재변환
LEGACY_SRC_ROOT=/path/to/migration/source/scripts \
  node scripts/build-code.mjs && node scripts/convert-template.mjs

# .env 작성 (placeholder 모두 실 값으로)
cp .env.example .env
nano .env

# 기동
npm start
# → http://localhost:5184
```

## healthz

```bash
curl http://localhost:5184/healthz
# {
#   "ok": true,
#   "app": "estimate-legacy",
#   "sheets":  { "ok": true, "cacheSize": 0, "ttlMs": 300000 },
#   "ecount":  { "ok": true, "endpoint": "http://152.69.228.109:3000" },
#   "notion":  { "ok": true, "tokensConfigured": ["DC","ORDER","AUTH",...] }
# }
```

## 시트 캐시 무효화

```bash
curl -X POST http://localhost:5184/rpc/clearSheetCache \
  -H 'Content-Type: application/json' -d '{"args":[]}'
```

## Docker

```bash
docker build -t samhan/estimate-legacy:v2.0.0 .
docker run --rm -p 5184:5184 --env-file .env samhan/estimate-legacy:v2.0.0
```

배포는 `infrastructure/cafe24/docker-compose.legacy-v2.yml` 참조.

## 변환 규칙 (legacy 1:1 보존 가드)

- `lib/code.js` 는 `scripts/build-code.mjs` 가 `lib/_legacy-code-raw.js` (legacy estimate Code.js 그대로 복사) 로부터 자동 생성. 수동 편집 금지.
- 변환 규칙: 외부 호출 (UrlFetchApp.fetch) 사용 함수 14개를 async + await 추가, REDACTED 토큰을 process.env 로 치환. logic 0% 변경.
- `views/index.ejs` 는 `scripts/convert-template.mjs` 가 legacy index.html 로부터 자동 생성. `<?!= include('X') ?>` 인라인 + `<?!= var ?>` → `<%- var %>` + `<?= var ?>` → `<%= var %>` + google.script.run shim 주입.
