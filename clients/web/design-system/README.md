# @samhan/design-system

SamhanLogis 디자인 시스템 — Phase 1 기반 패키지.

## 무엇이 들어있는가

- **Design tokens** — `colors`, `typography`, `spacing`, `radii`, `shadows`,
  `durations`, `breakpoints` (TS 상수 + CSS 커스텀 프로퍼티)
- **Base 컴포넌트 7종** — `Button`, `Input`, `Label`, `FormField`, `Card`,
  `Modal`, `Spinner`
- **Storybook** — 모든 컴포넌트별 시나리오 등록
- **Vite library 빌드** — `dist/index.js` (ESM) + `dist/index.d.ts` 타입 번들

스택: React 18 + TypeScript (strict) + Vanilla CSS Modules. Tailwind / MUI 미사용.

## 사용 방법

```ts
// 1) 한 번만 토큰 CSS 임포트 (앱 진입점)
import '@samhan/design-system/tokens.css'

// 2) 필요한 컴포넌트 사용
import { Button, Modal, FormField, Input } from '@samhan/design-system'

function Example() {
  return (
    <div>
      <Button variant="primary">저장</Button>
      <Button variant="secondary" loading>저장 중…</Button>
    </div>
  )
}
```

토큰을 직접 사용하고 싶다면:

```ts
import { colors, spacing } from '@samhan/design-system/tokens'
```

또는 CSS 변수로:

```css
.my-thing {
  color: var(--color-brand-500);
  padding: var(--space-4);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
}
```

## 개발

```bash
# 의존성 설치 (워크스페이스 루트에서)
npm install

# Storybook 실행
npm run storybook --workspace=@samhan/design-system

# 라이브러리 빌드
npm run build --workspace=@samhan/design-system
```

빌드 결과물:

```
dist/
  index.js         # ESM 번들
  index.d.ts       # 타입 정의 (rolled-up)
  tokens.css       # CSS 변수 시트
  ...
```

## 한국어 우선 디자인

- 본문 기본 폰트 크기: **14px** (`--font-size-base`) — 한글 가독성 최적화
- 폰트 패밀리: **Pretendard Variable** (fallback 체인 포함)
- 컴포넌트 텍스트, ARIA 라벨, 에러 메시지는 한국어 기본

## 컴포넌트 추가 가이드 (Phase 2 이후)

새 컴포넌트는 `src/components/<Name>/` 아래 다음 4개 파일로 추가:

```
<Name>/
  <Name>.tsx           # forwardRef + named/default export
  <Name>.module.css    # CSS Module, 전역 클래스 누수 금지
  <Name>.stories.tsx   # Storybook 시나리오 (variant/size/state 커버)
  index.ts             # re-export (named + default + types)
```

이후 `src/index.ts` barrel에 `export * from './components/<Name>'` 추가.

규칙:

- 모든 색상/간격/그림자는 토큰 사용 (raw 값 금지)
- `forwardRef` 적용 — 상위 패키지에서 ref 제어 가능해야 함
- 접근성 우선: `role`, `aria-*`, 키보드 인터랙션 (포커스 트랩, ESC 등) 명시
- 한국어 ARIA 라벨 기본값 (`'닫기'`, `'로딩 중'` 등)

## 환경변수 표준 (Phase 8 / Phase 9 일관)

본 패키지 (Vite library + Storybook) 는 Vite 의 표준 prefix `VITE_*` 만 사용한다.

| 변수                          | 기본값                  | 용도                                              | 사용 위치               |
| ----------------------------- | ----------------------- | ------------------------------------------------- | ----------------------- |
| `VITE_STORYBOOK_API_BASE_URL` | `http://localhost:8080` | Storybook 안 demo 컴포넌트의 API mock target      | `src/stories/*.stories.tsx` (옵션) |
| `VITE_DESIGN_SYSTEM_THEME`    | `light`                 | Storybook 기본 theme (Phase 7 4차 dark-mode 추가) | `.storybook/preview.ts` |

### Phase 8 가드

- **`VITE_*` prefix 의무** — Vite 환경변수 표준. 본 패키지는 라이브러리 publish 대상이므로 환경변수는 Storybook + 빌드 검증 용도로만 사용.
- **컴포넌트 자체는 환경변수 비의존** — design-system 컴포넌트는 순수 prop-driven (no `import.meta.env` 읽기). 환경변수는 Storybook story 의 demo 데이터 mock 만 사용.
- **AWS Route 53 cutover 무관** — 본 패키지는 빌드 산출물 (`dist/`) 이 호스팅 owner 와 무관 (npm workspace local + 다른 client 가 dependency 로 사용).

### Phase 9 영향

신규 service (partner / groupware / notification / dashboard) 의 화면 컴포넌트가 본 design-system 의 Button / Input / Modal / FormField / Card 등을 dependency 로 사용 예정. 추가 컴포넌트 (예: dashboard 의 Chart / Sparkline) 는 W4 진입 시점 신규 작성 + Storybook story 추가 의무.

상세는 `docs/migration/phase9/M-PHASE-9-readiness.md` 참조.

## 라이선스

내부 패키지 (`private: true`). SamhanLogis 전용.
