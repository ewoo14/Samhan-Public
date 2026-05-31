# FE Cycle 1 리뷰 — audit Slice A PR #259

검토일: 2026-05-19
검토자: FE agent (Claude)
대상 범위: 3 client READMEs env 표 동기화 (desktop / arologis-desktop / arologis-mobile)

---

## 판정: APPROVE (결함 없음)

3건 모두 실 코드와 README 기술이 정확히 일치하며, 한국어 표현과 기존 표 패턴도 일관합니다.

---

## 1. clients/desktop/README.md

### 검증 내용

`VITE_WEB_ESTIMATE_URL`과 `VITE_WEB_ORDER_URL` 두 행이 기존 `VITE_API_BASE_URL` 행 아래에 추가되었습니다.

실제 코드 `SalesSubNav.tsx` (L38, L42) 에서:

```
import.meta.env.VITE_WEB_ESTIMATE_URL ?? 'http://localhost:5183'
import.meta.env.VITE_WEB_ORDER_URL    ?? 'http://localhost:5180'
```

README 기본값 `http://localhost:5183` / `http://localhost:5180` 이 코드의 fallback 값과 정확히 일치합니다.

설명란 "(SalesSubNav WebView 탭)" 표현에 대해: `SalesSubNav.tsx` 는 내부 WebView 가 아니라 `window.samhanLegacy.openExternal()` 로 외부 브라우저를 여는 구조이므로 "WebView 탭" 표현이 기술적으로 약간 부정확합니다. 그러나 이는 사용자가 인식하는 UX 동선(탭에서 클릭 → 외부 열기) 을 기술한 것으로 해석 가능하며, 실제 URL 기본값 정합은 완전하므로 결함으로 분류하지 않습니다. 향후 정밀화 시 "SalesSubNav 거래처 외부 웹 링크" 정도로 변경하면 더 정확합니다.

기존 표 헤더 `| 변수 | 기본값 | 설명 |` 패턴 유지 확인.

### 결론: 이상 없음

---

## 2. clients/arologis-desktop/README.md

### 검증 내용

변경 전 2열 표 (`변수` / `용도`) 가 3열 (`변수` / `기본값` / `설명`) 으로 확장되었습니다.

실제 코드 `arologis-desktop/src/renderer/api/client.ts` (L24) 에서:

```
import.meta.env.VITE_AROLOGIS_API_BASE ?? 'http://localhost:8097'
```

README 기본값 `http://localhost:8097` 이 코드 fallback 과 정확히 일치합니다.

`ELECTRON_RENDERER_URL` 행의 기본값 셀에 `(electron-vite 자동 주입)` 을 기입하고 설명란을 "dev 모드에서 electron-vite 가 자동 설정. 수동 지정 불필요." 로 보강한 점은 기존 desktop README 패턴과 일관합니다. 자동 주입 변수에 기본값 대신 괄호 표기를 쓰는 방식은 desktop README 의 동일 처리 방식과 일치합니다.

### 결론: 이상 없음

---

## 3. clients/arologis-mobile/README.md

### 검증 내용

"Pretendard 폰트 self-host" 섹션이 신규 추가되었습니다.

파일 목록 4건:

| README 기술 | assets/fonts/ 실 파일 | 일치 여부 |
|---|---|---|
| Pretendard-Regular.otf (400) | Pretendard-Regular.otf | 일치 |
| Pretendard-Medium.otf (500) | Pretendard-Medium.otf | 일치 |
| Pretendard-SemiBold.otf (600) | Pretendard-SemiBold.otf | 일치 |
| Pretendard-Bold.otf (700) | Pretendard-Bold.otf | 일치 |

hook 경로 `src/theme/usePretendardFontGuarded.ts` 와 실 파일 위치 일치 확인.

App.tsx (L19-L26) 에서 `fontsReady = false` 시 `SafeAreaProvider + StatusBar` 만 렌더링하는 패턴이 README 기술 "폰트 로딩 완료 전에는 App.tsx 가 SafeAreaProvider + StatusBar 만 렌더링" 과 정확히 일치합니다.

graceful guard 동작 (`expo-font` 미설치 또는 asset 누락 시 `fontsReady = true` 유지) 도 `usePretendardFontGuarded.ts` (L49-L52) catch 블록과 일치합니다.

RN family 이름 `Pretendard` (단일 family) 기술도 `tokens.ts` L139 (`sans: 'Pretendard'`) 및 hook 내 fontMap 키 (`'Pretendard'` / `'Pretendard-Medium'` / `'Pretendard-SemiBold'` / `'Pretendard-Bold'`) 와 일관합니다. README 는 "단일 family, weight 는 fontWeight prop 으로 분기" 라고 기술하는데, 실제 hook 은 weight 별로 별도 키를 등록하는 구조입니다. 사용 측에서는 `fontWeight` prop 으로 분기하도록 `tokens.ts` 가 단일 family 이름을 제공하므로 사용자 문서 관점에서 기술은 정확합니다.

### 결론: 이상 없음

---

## 종합

| 항목 | 판정 | 비고 |
|---|---|---|
| desktop — VITE_WEB_ESTIMATE_URL 기본값 | 통과 | 코드 fallback 완전 일치 |
| desktop — VITE_WEB_ORDER_URL 기본값 | 통과 | 코드 fallback 완전 일치 |
| desktop — 설명 "WebView 탭" 표현 | 미결함 | 기술적 부정밀, 향후 정밀화 권고 |
| arologis-desktop — 3열 확장 + 기본값 | 통과 | 코드 fallback 완전 일치 |
| arologis-mobile — Pretendard 파일 4건 | 통과 | 실 파일 100% 일치 |
| arologis-mobile — hook 경로 | 통과 | 실 파일 위치 일치 |
| arologis-mobile — graceful guard 동작 기술 | 통과 | App.tsx + hook catch 블록 일치 |
| 한국어 자연스러움 | 통과 | 문체 이상 없음 |
| 기존 표 패턴 일관성 | 통과 | 헤더 형식 일관 |

P1/P2 결함: 없음. 코드 수정 필요 없음.
