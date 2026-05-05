# @samhan/order-app — v4 (legacy 임베드)

> 거래처 주문서 web app. v4 는 legacy `migration/source/scripts/partner-order/index.html` (9427 라인) 을 그대로 임베드 + shim + PWA 보존.

## 결정 출처
- `migration/decisions/DECISIONS.md` Phase 6 v4 (`b15fa12`) §"frontend 방향 최종 확정 — legacy 코드 임베드 (v4)"

## 핵심 구조
```
clients/web/order-app/
├─ index.html              ← legacy partner-order/index.html (9427 라인) 그대로 +
│                            <script type="module" src="/src/main.ts"> 1줄 +
│                            Apps Script 템플릿 → __SAMHAN_BOOTSTRAP__ 변환
├─ src/
│  ├─ main.ts              ← Vite entry — shim 설치 + 부트스트랩 prefetch + PWA SW 등록
│  ├─ legacyShim.ts        ← window.google.script.run Proxy + UrlFetchApp noop
│  ├─ samhanApi.ts         ← axios + RPC_MAP (legacy fnName → SamhanLogis MS endpoint)
│  └─ vite-env.d.ts        ← vite/client + vite-plugin-pwa/client 타입
├─ public/
│  ├─ manifest.webmanifest ← PWA manifest (보존)
│  └─ icons/               ← icon-192.png / icon-512.png placeholder (DESIGN team 후속)
├─ vite.config.ts          ← VitePWA + alias `@` → src + dev port 5180
├─ tsconfig.json / tsconfig.node.json
├─ eslint.config.js
└─ scripts/qa-capture.mjs  ← Edge 헤드리스 → docs/qa/migration-fe-order-app-v4/*.png 6장
```

## RPC 매핑
- 표: `docs/dev-reports/legacy-rpc-mapping-partner-order.md`
- 매핑 변경 시 본 표 + `samhanApi.ts` RPC_MAP 동시 보강 의무

## 외부 호출 폐기
- e-Count `UrlFetchApp.fetch` → slip-service 자동 출고전표
- Notion API 9 토큰 → SamhanLogis MS DB 직접
- shim 의 `window.UrlFetchApp.fetch` 는 noop + warn

## 명령
```bash
npm install
npm run dev          # http://localhost:5180
npm run typecheck
npm run lint
npm run build        # → dist/ (Vite + workbox SW + manifest)
npm run preview      # http://localhost:5181 (build 결과 미리보기)
node scripts/qa-capture.mjs   # → docs/qa/migration-fe-order-app-v4/*.png 6장
```

## 제한 (TODO M4 backend)
- `/api/v1/partner-orders/bootstrap` 미구현 → 부트스트랩 빈 객체. legacy 카탈로그 (홈멀티/싱글/상업) 는 비어있는 상태로 진입. BizGate / 로그인 / mobile-gate 는 정상 동작.
- M2 partner-service / M4 partner-order-service 머지 후 RPC 응답 정상화 + bootstrap endpoint 추가.

## v3 → v4 변경
- React 18 / react-router / @tanstack/react-query / @dnd-kit / zustand 의존성 모두 폐기
- `@samhan/design-system` 의존성 폐기 (legacy 가 자체 CSS 보유)
- 약 30개 React 컴포넌트 / 페이지 / store / api 폐기
- shim + axios + 매핑 표 만 유지
