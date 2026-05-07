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

#### W4 신규 — 3 channel badge 토큰 (W3 Designer backlog #2 채택)

`b-channel-push` / `b-channel-email` / `b-channel-sms` 3종 토큰 신설 (Google Material — Blue / Red / Green). dashboard QA `3-api-endpoints-summary.html` 에서 1차 사용.

#### W4 후속 fix — `<ChannelBadge>` 정식 컴포넌트 (Designer D-W4-2 + FE-W4-1/2/3 통합)

| 컴포넌트 | 설명 | 도입 슬라이스 |
| --- | --- | --- |
| `ChannelBadge` | 알림 채널 (`PUSH` / `EMAIL` / `SMS`) badge — Google Material 컬러 + size (`md` / `sm`) variant + 커스텀 `label` prop. CSS Module + `--color-channel-*` CSS variable 토큰화. | W4 후속 fix (PR #94) |

- `src/components/ChannelBadge/ChannelBadge.tsx` — `forwardRef` + `HTMLSpanElement` 호환 + `data-channel/size` 속성.
- `src/components/ChannelBadge/ChannelBadge.module.css` — `--color-channel-{push,email,sms}` / `--badge-channel-font-size{,-sm}` 인용. `tokens.css` utility class (`.b-channel-*`) 와 색상 일관.
- `src/components/ChannelBadge/ChannelBadge.stories.tsx` — Storybook 7 story (3 channel × 2 size grid + Push/Email/Sms 단건 + SmallSize + KoreanLabel).
- `src/index.ts` 에 `ChannelBadge` / `ChannelType` / `ChannelBadgeSize` / `ChannelBadgeProps` 정식 export.

#### slice 명 정정 (W3 FE backlog #5 채택)

- 기존 `notification-slice-B` → `link-dispatch-slice` 일괄 정정.
- 신규 `notification-service` (backend) 와 단어 충돌 회피.
- 영향 file: `src/components/CopyButton/CopyButton.tsx` 외 4개 (DataTable / PhoneInput / index.ts).

상세는 `docs/migration/phase9/M-PHASE-9-readiness.md` 참조.

#### post-W5 backlog cleanup — slice accent 3색 토큰 (Designer D-W5-2 채택)

post-W5 backlog cleanup PR (D-P9-21) 에서 slice accent 3색 토큰 신설 — Phase 10 W1 흡수 예정 항목 본 PR 선반영.

| Token / Class | 용도 | Hex |
| --- | --- | --- |
| `--color-slice-success` / `.slice-accent-success` | 완료 / 통과 | `#34a853` (Google Material Green) |
| `--color-slice-pending` / `.slice-accent-pending` | 진행 / 대기 | `#f9ab00` (Google Material Yellow) |
| `--color-slice-deferred` / `.slice-accent-deferred` | 위임 / backlog | `#5f6368` (Google Material Gray) |

본 PR 시점에는 utility class + CSS variable 만 export 하며, 정식 React `<SliceAccent>` 컴포넌트 (Storybook 포함) 는 W6 client 통합 슬라이스 시점에 `<ChannelBadge>` 패턴과 동일하게 도입한다.

사용:
```html
<span class="slice-accent-success">완료</span>
<span class="slice-accent-pending">진행</span>
<span class="slice-accent-deferred">위임</span>
```

## 라이선스

내부 패키지 (`private: true`). SamhanLogis 전용.
