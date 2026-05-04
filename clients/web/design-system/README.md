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

## 라이선스

내부 패키지 (`private: true`). SamhanLogis 전용.
