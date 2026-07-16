/**
 * vite.809-realqa.config.ts — #809 R4 라이브 QA 전용 renderer 설정.
 *
 * 왜 필요한가: `@samhan/design-system` 패키지는 exports 가 `dist/` 를 가리키는데, 이 dist 는
 * 로컬 빌드 산출물이라 브랜치 코드(R3 fix 의 LineRow '거래처 최근단가'/'판매가' 마커,
 * priceRefreshed 강조)보다 오래될 수 있다(R4 실측: dist=07-08, R3 fix=07-15 → 마커 문자열 0건).
 * 공유 산출물(dist) 재빌드 대신 — 동시 리뷰 에이전트와의 빌드 경합을 피하기 위해 —
 * 본 QA 하네스에서만 design-system 을 **소스로 alias** 해 현 브랜치 코드를 그대로 서빙한다.
 * (vitest.config.ts 가 동일한 소스 alias 를 이미 쓰는 선례 있음.)
 *
 * 사용:
 *   cd clients/desktop
 *   VITE_API_BASE_URL=http://localhost:8080 \
 *     node_modules/.bin/vite --config playwright/809-price-memory-real-qa/vite.809-realqa.config.ts \
 *     --port 5217 --strictPort
 */
import { defineConfig, mergeConfig } from 'vite'
import { resolve } from 'node:path'
import baseConfig from '../../vite.web.config'

const designSystemSrc = resolve(__dirname, '../../../web/design-system/src')

export default mergeConfig(
  baseConfig,
  defineConfig({
    resolve: {
      alias: [
        // subpath 를 정규식 exact-match 로 먼저 처리 — bare prefix alias 가 subpath 를 삼키지 않게.
        { find: /^@samhan\/design-system\/tokens\.css$/, replacement: resolve(designSystemSrc, 'tokens/tokens.css') },
        { find: /^@samhan\/design-system\/tokens$/, replacement: resolve(designSystemSrc, 'tokens/index.ts') },
        // style.css 는 소스 대응물이 없어 기존 dist 해석을 유지한다(@font-face 등 side-effect 전용).
        // 컴포넌트 CSS Module 은 소스 경로에서 dev 파이프라인이 직접 주입하므로 stale 영향 없음.
        { find: /^@samhan\/design-system$/, replacement: resolve(designSystemSrc, 'index.ts') },
      ],
      // design-system/node_modules 에 자체 react 사본(devDep)이 있어 이중 React 방지 필수.
      dedupe: ['react', 'react-dom'],
    },
    server: {
      fs: {
        // desktop 루트 밖의 design-system 소스 서빙 허용 (clients/ 전체).
        allow: [resolve(__dirname, '../../..')],
      },
    },
  }),
)
