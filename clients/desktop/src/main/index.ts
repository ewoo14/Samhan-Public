/**
 * Electron 메인 프로세스 진입점.
 *
 * 책임:
 * - 단일 BrowserWindow 생성 (1280x800, contextIsolation 활성)
 * - 개발 모드에서는 Vite dev server URL 로딩, DevTools 자동 오픈
 * - 프로덕션 모드에서는 번들된 `out/renderer/index.html` 파일 로딩
 * - IPC 채널 등록 (`auth:*`) — preload 가 contextBridge 로 노출
 *
 * 보안 정책:
 * - `contextIsolation: true` — 렌더러는 Node 직접 접근 불가
 * - `nodeIntegration: false` — 렌더러 프로세스에서 require/process 차단
 * - preload 스크립트만 IPC 게이트웨이 역할 수행
 */
import { app, BrowserWindow } from 'electron'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'
import { registerAuthIpcHandlers } from './ipc/auth-token.js'

const __filename = fileURLToPath(import.meta.url)
const __dirname = dirname(__filename)

/**
 * 메인 윈도우 인스턴스 — 다중 윈도우는 본 슬라이스 범위 외.
 */
let mainWindow: BrowserWindow | null = null

/**
 * 메인 BrowserWindow 를 생성하고 렌더러 컨텐츠를 로드한다.
 *
 * 개발 모드 (`process.env.ELECTRON_RENDERER_URL` 존재) 에서는
 * electron-vite dev server URL 을 로드하여 HMR 을 활용하고,
 * 프로덕션에서는 번들된 정적 HTML 파일을 file:// 로 로드한다.
 */
function createMainWindow(): void {
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 800,
    minWidth: 1024,
    minHeight: 720,
    show: false,
    autoHideMenuBar: true,
    title: '삼한로지스',
    webPreferences: {
      preload: join(__dirname, '../preload/index.mjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
    },
  })

  mainWindow.once('ready-to-show', () => {
    mainWindow?.show()
  })

  const devUrl = process.env['ELECTRON_RENDERER_URL']
  if (devUrl) {
    mainWindow.loadURL(devUrl)
    mainWindow.webContents.openDevTools({ mode: 'detach' })
  } else {
    mainWindow.loadFile(join(__dirname, '../renderer/index.html'))
  }

  mainWindow.on('closed', () => {
    mainWindow = null
  })
}

app.whenReady().then(() => {
  registerAuthIpcHandlers()
  createMainWindow()

  app.on('activate', () => {
    // macOS 호환성 코드 — 본 앱은 Windows 전용이지만 표준 패턴 유지.
    if (BrowserWindow.getAllWindows().length === 0) createMainWindow()
  })
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit()
})
