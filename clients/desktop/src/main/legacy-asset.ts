/**
 * legacy estimate webview 자산 경로 해석.
 *
 * <p>build 단계에서 `clients/desktop/scripts/build-legacy-estimate.cjs` 가
 * `migration/source/scripts/estimate/index.html` (18614 라인) 의 Apps Script
 * `<?!= include() ?>` 디렉티브를 inline 해소한 결과 (`index.built.html`) 와
 * fallback 을 `clients/desktop/legacy-assets/estimate/` 에 출력한다.</p>
 *
 * <p>본 모듈은 dev / production 환경 모두에서 webview 가 로드할 file:// URL 을 반환한다.</p>
 *
 * <h2>경로 해석</h2>
 * <ul>
 *   <li>dev (electron-vite dev): `<repoRoot>/clients/desktop/legacy-assets/estimate/index.built.html`</li>
 *   <li>production: `<resourcesPath>/legacy-assets/estimate/index.built.html` 또는
 *       packaged out 디렉토리 내의 동일 경로</li>
 * </ul>
 *
 * <p>Electron `webview` tag 는 file:// URL 을 직접 src 로 사용 가능하므로 별도
 * `protocol.registerFileProtocol` 등록은 불필요. (필요 시 후속 단계에서 `legacy://`
 * custom scheme 추가 가능.)</p>
 */
import { app } from 'electron'
import { existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const __filename = fileURLToPath(import.meta.url)
const __dirname = dirname(__filename)

/** legacy estimate index.built.html 의 절대 file:// URL. */
export function getLegacyEstimateUrl(): string {
  // dev 환경: __dirname = clients/desktop/out/main → 두 단계 위 = clients/desktop
  // packaged: __dirname = .../resources/app.asar/out/main 또는 unpacked 와 유사
  const candidates = [
    // dev / built but not packaged
    resolve(__dirname, '..', '..', 'legacy-assets', 'estimate', 'index.built.html'),
    // packaged (resources/app/legacy-assets)
    resolve(process.resourcesPath || '.', 'legacy-assets', 'estimate', 'index.built.html'),
    // electron-builder extraResources 패턴
    resolve(app.getAppPath(), 'legacy-assets', 'estimate', 'index.built.html'),
  ]
  for (const c of candidates) {
    if (existsSync(c)) {
      // file:// URL 변환 (Windows 백슬래시 → 슬래시)
      const url = 'file:///' + c.replace(/\\/g, '/')
      return url
    }
  }
  // fallback — 어떤 경로도 없으면 빈 about:blank (UI 가 placeholder 표시)
  console.warn('[legacy-asset] 자산 미발견 — about:blank fallback. 후보:', candidates)
  return 'about:blank'
}
