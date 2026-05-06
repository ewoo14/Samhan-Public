# @samhan/desktop

삼한로지스 사내 직원용 데스크톱 앱 (Electron + React + Vite).

본 앱은 디자인 시스템 (`@samhan/design-system`) 의 16 개 컴포넌트를 처음으로
실제 도메인 화면에 적용하는 첫 슬라이스입니다. 메인 프로세스는
`electron-store` + `safeStorage` 로 JWT 를 OS 레벨 암호화 저장하며,
렌더러는 IPC 를 통해서만 토큰에 접근합니다.

## 기술 스택

- Electron 33 + electron-vite 2 + electron-builder 25
- React 18 + React Router v6 (HashRouter)
- TanStack Query v5 + axios
- electron-store v10 + safeStorage (DPAPI/Keychain)
- zustand 5 (세션 상태)
- TypeScript 5.6
- 디자인 시스템: `file:../web/design-system` 직접 링크

## 사전 요구사항

- Node.js 20 이상 (권장 22+)
- npm 10 이상
- Windows 10/11 (Q6=A 결정 — 본 슬라이스는 Windows 전용)
- 백엔드 7 마이크로서비스 (api-gateway:8080) 가 로컬에서 부팅되어 있어야
  로그인/창고/전표 화면이 실제 데이터로 렌더링됩니다.

## 빌드 / 실행

```powershell
# 1) 의존성 설치 (workspaces 미사용 — 직접 디렉토리에서 실행)
cd clients/desktop
npm install

# 2) 개발 모드 — Vite HMR + Electron 메인 프로세스 자동 재시작
npm run dev

# 3) 정적 빌드 — out/main, out/preload, out/renderer 산출
npm run build

# 4) Windows .exe 패키지 (electron-builder)
#    DevOps 가 electron-builder.yml 을 작성한 뒤 실행 가능합니다.
npm run build:win
```

## 환경 변수

`src/renderer/.env` 또는 OS 환경변수로 다음 값을 주입할 수 있습니다.

| 변수 | 기본값 | 설명 |
|------|--------|------|
| `VITE_API_BASE_URL` | `http://localhost:8080` | api-gateway URL |

## 화면 구성 (첫 슬라이스 4 화면)

| 경로 | 화면 | 사용 디자인 시스템 컴포넌트 |
|------|------|------|
| `/login` | 로그인 | Card, FormField, Button, Spinner |
| `/` | 대시보드 | Card, Button |
| `/warehouses` | 창고 목록 + 신규 등록 모달 | DataTable, Badge, Button, Modal, FormField |
| `/slips` | 출고전표 목록 | DataTable, Badge, Button, SlipNumberDisplay, SlipStatusBadge |
| `/slips/new` | 새 출고전표 작성 | WarehouseSelector, DeliveryTagSelector, FormField, PriceField, Button, Card |

총 디자인 시스템 컴포넌트 11 / 16 개 첫 실사용
(미사용: TagChip, TagInput, Input wrapper, Label, SlipNumberDisplay 외).

## 보안 모델

- `contextIsolation: true` + `nodeIntegration: false`
- 메인 프로세스만 토큰을 보관 (`src/main/store/auth-store.ts`)
- 렌더러는 `window.samhanAuth.{getToken,setToken,clearToken}` IPC 만 사용
- 401 응답 시 axios 인터셉터가 토큰 클리어 + `/login` 자동 이동

## v4 후속 (Phase 6 / 7)

- Phase 6 PR #51 / #54 — 16 라우트 + 모바일 서명 2-step 흐름 (기사 → 인수자) + 캔버스 fullscreen UX + DispatchView 양측 서명 PNG 자동 통합
- Phase 7 — `qa/playwright/` `electron-desktop` project 가 packaged binary 에 대해
  auth / catalog / confirm 시나리오 자동 검증

## 알려진 제한사항 (후속)

- 전표 작성 시 productId 직접 UUID 입력 → Phase 7 3차 추가된 product-service
  `GET /api/products/by-code/{modelCode}` 로 modelCode autocomplete 전환 후속.
- 전표 상세 화면 일부 placeholder
- 페이지네이션 size=20 고정
- 결재선/메신저/저재고 알림 카드 placeholder
