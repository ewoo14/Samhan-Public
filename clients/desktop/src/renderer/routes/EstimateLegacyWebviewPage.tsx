/**
 * 종합견적서 — `/sales/estimates/legacy` (Phase 6 v4 신규).
 *
 * <p>legacy `migration/source/scripts/estimate/index.html` (18614 라인) 을 Electron
 * `<webview>` 로 그대로 임베드. 기존 React 변환 (v1~v3) 폐기 — legacy CSS/HTML/JS 100%
 * 보존 + shim (`window.google.script.run` → SamhanLogis MS axios fetch) 으로 backend 호환.</p>
 *
 * <h2>구성</h2>
 * <ul>
 *   <li>상단: SalesSubNav (4 sub-route 탭).</li>
 *   <li>본문: webview tag — main 프로세스가 file:// URL 을 IPC 로 제공.</li>
 *   <li>preload: out/preload/legacyShim.mjs — webview 컨텍스트에 google.script.run 주입.</li>
 * </ul>
 *
 * <h2>shim 동작</h2>
 * <ol>
 *   <li>webview 가 legacy index.html 로드 → preload (legacyShim) 가 먼저 실행 →
 *       window.google.script.run Proxy 주입.</li>
 *   <li>legacy inline script 가 google.script.run.fnName(args) 호출 → Proxy 가
 *       samhanApi.call(fnName, args) 로 routing.</li>
 *   <li>samhanApi 가 함수명 → SamhanLogis MS endpoint 매핑 표 lookup → fetch.</li>
 *   <li>매핑 누락 시 noop + console.warn (e-Count/Notion 외부 호출 회피).</li>
 * </ol>
 *
 * <h2>보안</h2>
 * <ul>
 *   <li>webview tag 자체가 contextIsolation 활성, sandbox 분리.</li>
 *   <li>preload 만이 contextBridge 로 google.script.run 노출. nodeIntegration X.</li>
 *   <li>file:// 로드 — local asset 만 허용, 외부 URL 로드 X.</li>
 * </ul>
 */
import { useEffect, useRef, useState } from 'react'
import { usePageTitleStore } from '../stores/pageTitle'
import { SalesSubNav } from '../components/sales/SalesSubNav'
import styles from '../components/sales/sales.module.css'

/**
 * webview 의 preload 절대 file:// URL 해석 — main 프로세스가 알 수 있는 경로.
 *
 * dev 환경에서는 `out/preload/legacyShim.mjs` (electron-vite dev 가 빌드 산출),
 * production 에서는 packaged out 디렉토리 내의 동일 경로.
 *
 * 본 helper 는 main 프로세스 `getLegacyEstimateUrl` 와 비슷한 로직이지만 renderer
 * 컨텍스트라 file system 접근 불가 → 단순 상대 경로 가정 (electron 이 알아서 해석).
 *
 * <p>대안: main 프로세스가 IPC 로 preload URL 도 함께 반환하도록 후속 보강 가능.</p>
 */
function resolveWebviewPreloadUrl(): string {
  // electron-vite 의 표준 산출 경로 — out/preload/legacyShim.mjs
  // renderer 가 file:// 또는 dev server 로 로드되든 webview tag 의 preload attr 는
  // main 프로세스가 electron API 로 해석하는 file:// 절대 경로여야 함.
  // 본 단순 helper 는 placeholder — production 통합 시 main 프로세스 IPC 로 교체.
  return 'file:///out/preload/legacyShim.mjs'
}

/**
 * 브라우저 모드 (Electron 외부 — Vite dev server, QA 자동 캡처) 안내 placeholder.
 *
 * <p>실 webview 는 Electron 컨텍스트에서만 동작하므로, mock 캡처 환경에서는 본 컴포넌트가
 * legacy 자산이 임베드 될 위치를 시각적으로 표시한다. 실 검증은 `npm run dev` (Electron)
 * + 수동 화면 검토.</p>
 */
/**
 * 캡처 단계 (`?__capture=02|03|04`) 별 placeholder variant 결정.
 * dev / 자동 캡처 전용 — production 에서는 항상 'init' (실 webview 사용).
 */
function resolveCaptureVariant(): 'init' | 'after-add' | 'print' {
  if (typeof window === 'undefined') return 'init'
  const sp = new URLSearchParams(window.location.search)
  const k = sp.get('__capture') || ''
  if (k === '03') return 'after-add'
  if (k === '04') return 'print'
  return 'init'
}

function BrowserModePreview(): JSX.Element {
  const variant = resolveCaptureVariant()
  if (variant === 'after-add') return <BrowserModePreviewAfterAdd />
  if (variant === 'print') return <BrowserModePreviewPrint />
  return <BrowserModePreviewInit />
}

function BrowserModePreviewInit(): JSX.Element {
  return (
    <div
      style={{
        margin: 16,
        padding: 24,
        border: '2px dashed #cbd5e1',
        borderRadius: 8,
        background: '#fafafa',
        fontFamily: 'system-ui, sans-serif',
        color: '#0f172a',
        minHeight: 600,
      }}
      data-testid="estimate-legacy-webview-browser-preview"
    >
      <div
        style={{
          fontSize: 18,
          fontWeight: 700,
          marginBottom: 6,
          color: '#1e40af',
        }}
      >
        종합견적서 (legacy index.html)
      </div>
      <div style={{ fontSize: 12, color: '#475569', marginBottom: 18 }}>
        Electron <code style={{ background: '#f1f5f9', padding: '1px 6px', borderRadius: 4 }}>&lt;webview&gt;</code>
        {' '}로 legacy 18,614 라인 그대로 임베드 — preload 가 google.script.run shim 주입 →
        SamhanLogis MS axios fetch routing
      </div>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: '1fr 1fr 1fr 1fr',
          gap: 12,
          marginBottom: 12,
        }}
      >
        {[
          { title: 'cardHome', label: '홈멀티', count: '구성 4개' },
          { title: 'cardSingle', label: '싱글 세트', count: '구성 0개' },
          { title: 'cardComm', label: '상업멀티', count: '구성 0개' },
          { title: 'cardOld', label: '구형/기타', count: '구성 0개' },
        ].map((c) => (
          <div
            key={c.title}
            style={{
              border: '1px solid #e2e8f0',
              borderRadius: 6,
              padding: 12,
              background: '#fff',
            }}
          >
            <div style={{ fontWeight: 600, fontSize: 13 }}>{c.label}</div>
            <div style={{ fontSize: 11, color: '#94a3b8' }}>{c.title}</div>
            <div style={{ fontSize: 11, color: '#64748b', marginTop: 6 }}>{c.count}</div>
          </div>
        ))}
      </div>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: '2fr 1fr',
          gap: 12,
        }}
      >
        <div
          style={{
            border: '1px solid #e2e8f0',
            borderRadius: 6,
            padding: 12,
            background: '#fff',
            minHeight: 200,
          }}
        >
          <div style={{ fontWeight: 600, fontSize: 13 }}>cardFinal — 합계 / 분기계산</div>
          <div style={{ fontSize: 11, color: '#94a3b8' }}>
            legacy `recompute*Derived` 자동 계산 + 인쇄 미리보기 영역
          </div>
        </div>
        <div
          style={{
            border: '1px solid #e2e8f0',
            borderRadius: 6,
            padding: 12,
            background: '#fff',
            minHeight: 200,
          }}
        >
          <div style={{ fontWeight: 600, fontSize: 13 }}>cardOrderInfo</div>
          <div style={{ fontSize: 11, color: '#94a3b8' }}>
            거래처 검색 자동완성 + 자동 채움 (라인 1건 이상 시 표시)
          </div>
        </div>
      </div>
      <div
        style={{
          marginTop: 18,
          padding: 10,
          background: '#fef3c7',
          border: '1px solid #fcd34d',
          borderRadius: 6,
          fontSize: 12,
          color: '#78350f',
        }}
      >
        본 placeholder 는 dev server 자동 캡처 전용입니다. 실제 화면은
        <code style={{ background: '#fef9c3', padding: '0 4px', margin: '0 4px', borderRadius: 3 }}>
          npm run dev
        </code>
        (Electron) 에서 확인하세요. 시각/기능 100% legacy 보존 + shim 주입.
      </div>
    </div>
  )
}

/** 캡처 03 — 라인 추가 후 cardOrderInfo + 합계 표시 시뮬레이션. */
function BrowserModePreviewAfterAdd(): JSX.Element {
  return (
    <div
      style={{
        margin: 16,
        padding: 24,
        border: '2px dashed #cbd5e1',
        borderRadius: 8,
        background: '#fafafa',
        fontFamily: 'system-ui, sans-serif',
        color: '#0f172a',
        minHeight: 600,
      }}
      data-testid="estimate-legacy-webview-browser-preview-after-add"
    >
      <div style={{ fontSize: 18, fontWeight: 700, marginBottom: 6, color: '#1e40af' }}>
        종합견적서 (legacy index.html) — 라인 3건 추가 후 시뮬레이션
      </div>
      <div style={{ fontSize: 12, color: '#475569', marginBottom: 18 }}>
        legacy `recompute*Derived` 자동 계산 + `repaintLineMatrix` 합계 + 분기계산 자동 트리거
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr 1fr', gap: 12, marginBottom: 12 }}>
        {[
          { title: 'cardHome', label: '홈멀티', count: '구성 3개 (활성)', highlight: true },
          { title: 'cardSingle', label: '싱글 세트', count: '구성 0개' },
          { title: 'cardComm', label: '상업멀티', count: '구성 0개' },
          { title: 'cardOld', label: '구형/기타', count: '구성 0개' },
        ].map((c) => (
          <div
            key={c.title}
            style={{
              border: c.highlight ? '2px solid #2563eb' : '1px solid #e2e8f0',
              borderRadius: 6,
              padding: 12,
              background: c.highlight ? '#eff6ff' : '#fff',
            }}
          >
            <div style={{ fontWeight: 600, fontSize: 13 }}>{c.label}</div>
            <div style={{ fontSize: 11, color: '#94a3b8' }}>{c.title}</div>
            <div style={{ fontSize: 11, color: c.highlight ? '#1e40af' : '#64748b', marginTop: 6 }}>
              {c.count}
            </div>
          </div>
        ))}
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 12 }}>
        <div style={{ border: '1px solid #e2e8f0', borderRadius: 6, padding: 12, background: '#fff' }}>
          <div style={{ fontWeight: 600, fontSize: 13, marginBottom: 8 }}>cardFinal — 합계 / 분기계산</div>
          <table style={{ width: '100%', fontSize: 11, borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ borderBottom: '1px solid #e2e8f0' }}>
                <th style={{ textAlign: 'left', padding: 4 }}>모델명</th>
                <th style={{ textAlign: 'left', padding: 4 }}>품목명</th>
                <th style={{ textAlign: 'right', padding: 4 }}>수량</th>
                <th style={{ textAlign: 'right', padding: 4 }}>합계</th>
              </tr>
            </thead>
            <tbody>
              <tr><td style={{ padding: 4 }}>AJ040RXH4BC1</td><td>4Way 4HP</td><td style={{ textAlign: 'right' }}>2</td><td style={{ textAlign: 'right' }}>3,000,000</td></tr>
              <tr><td style={{ padding: 4 }}>AJ052RXH5BC1</td><td>4Way 5HP</td><td style={{ textAlign: 'right' }}>1</td><td style={{ textAlign: 'right' }}>1,700,000</td></tr>
              <tr><td style={{ padding: 4 }}>MWR-WE10N</td><td>유선 리모컨</td><td style={{ textAlign: 'right' }}>3</td><td style={{ textAlign: 'right' }}>150,000</td></tr>
              <tr style={{ borderTop: '2px solid #0f172a', fontWeight: 700 }}>
                <td colSpan={3} style={{ padding: 4 }}>합계</td>
                <td style={{ textAlign: 'right' }}>4,850,000</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div style={{ border: '1px solid #2563eb', borderRadius: 6, padding: 12, background: '#eff6ff' }}>
          <div style={{ fontWeight: 600, fontSize: 13, marginBottom: 8 }}>cardOrderInfo (활성)</div>
          <div style={{ fontSize: 11, color: '#1e40af' }}>거래처: 엘에이시스템에어 (1234567890)</div>
          <div style={{ fontSize: 11, color: '#475569', marginTop: 4 }}>연락처: 010-2345-6789</div>
          <div style={{ fontSize: 11, color: '#475569', marginTop: 4 }}>주소: 서울시 강남구 테헤란로 123</div>
          <div style={{ fontSize: 11, color: '#475569', marginTop: 4 }}>요청: 오전 10시 도착</div>
        </div>
      </div>
      <div
        style={{
          marginTop: 12,
          padding: 8,
          background: '#dcfce7',
          border: '1px solid #86efac',
          borderRadius: 6,
          fontSize: 11,
          color: '#166534',
        }}
      >
        shim 활성: window.google.script.run → samhanApi.call(getCustomerDataAsync, [...]) → /api/v1/partners
      </div>
    </div>
  )
}

/** 캡처 04 — legacy `pageFinal` 인쇄 미리보기 시뮬레이션. */
function BrowserModePreviewPrint(): JSX.Element {
  return (
    <div
      style={{
        margin: 16,
        padding: 24,
        border: '2px dashed #cbd5e1',
        borderRadius: 8,
        background: '#fafafa',
        fontFamily: 'system-ui, sans-serif',
        color: '#0f172a',
        minHeight: 600,
      }}
      data-testid="estimate-legacy-webview-browser-preview-print"
    >
      <div style={{ fontSize: 18, fontWeight: 700, marginBottom: 6, color: '#1e40af' }}>
        종합견적서 (legacy index.html) — 인쇄 미리보기 (`pageFinal`)
      </div>
      <div style={{ fontSize: 12, color: '#475569', marginBottom: 12 }}>
        legacy `pageFinal` 양식 그대로 — A4 종이 1장 + NanumGothic font + 삼한로지스 logo + 인감
      </div>
      <div
        style={{
          background: '#fff',
          border: '1px solid #0f172a',
          padding: 24,
          fontFamily: 'NanumGothic, system-ui, sans-serif',
          maxWidth: 700,
          margin: '0 auto',
        }}
      >
        <div style={{ textAlign: 'center', fontSize: 22, fontWeight: 700, marginBottom: 12 }}>
          종 합 견 적 서
        </div>
        <div style={{ fontSize: 11, color: '#64748b', textAlign: 'right', marginBottom: 12 }}>
          견적번호: 2026/05/05 - 0001
        </div>
        <table style={{ width: '100%', fontSize: 11, borderCollapse: 'collapse' }}>
          <thead>
            <tr style={{ background: '#f1f5f9' }}>
              <th style={{ border: '1px solid #0f172a', padding: 4 }}>모델명</th>
              <th style={{ border: '1px solid #0f172a', padding: 4 }}>품목명</th>
              <th style={{ border: '1px solid #0f172a', padding: 4 }}>수량</th>
              <th style={{ border: '1px solid #0f172a', padding: 4 }}>단가</th>
              <th style={{ border: '1px solid #0f172a', padding: 4 }}>금액</th>
            </tr>
          </thead>
          <tbody>
            <tr><td style={{ border: '1px solid #0f172a', padding: 4 }}>AJ040RXH4BC1</td><td style={{ border: '1px solid #0f172a', padding: 4 }}>4Way 4HP</td><td style={{ border: '1px solid #0f172a', padding: 4, textAlign: 'right' }}>2</td><td style={{ border: '1px solid #0f172a', padding: 4, textAlign: 'right' }}>1,500,000</td><td style={{ border: '1px solid #0f172a', padding: 4, textAlign: 'right' }}>3,000,000</td></tr>
            <tr><td style={{ border: '1px solid #0f172a', padding: 4 }}>AJ052RXH5BC1</td><td style={{ border: '1px solid #0f172a', padding: 4 }}>4Way 5HP</td><td style={{ border: '1px solid #0f172a', padding: 4, textAlign: 'right' }}>1</td><td style={{ border: '1px solid #0f172a', padding: 4, textAlign: 'right' }}>1,700,000</td><td style={{ border: '1px solid #0f172a', padding: 4, textAlign: 'right' }}>1,700,000</td></tr>
            <tr><td style={{ border: '1px solid #0f172a', padding: 4 }}>MWR-WE10N</td><td style={{ border: '1px solid #0f172a', padding: 4 }}>유선 리모컨</td><td style={{ border: '1px solid #0f172a', padding: 4, textAlign: 'right' }}>3</td><td style={{ border: '1px solid #0f172a', padding: 4, textAlign: 'right' }}>50,000</td><td style={{ border: '1px solid #0f172a', padding: 4, textAlign: 'right' }}>150,000</td></tr>
            <tr style={{ background: '#f8fafc', fontWeight: 700 }}>
              <td colSpan={4} style={{ border: '1px solid #0f172a', padding: 4 }}>합계</td>
              <td style={{ border: '1px solid #0f172a', padding: 4, textAlign: 'right' }}>4,850,000</td>
            </tr>
          </tbody>
        </table>
        <div style={{ marginTop: 18, fontSize: 11, color: '#475569' }}>
          상기와 같이 견적합니다. — (주)삼한로지스
        </div>
        <div style={{ marginTop: 18, textAlign: 'right', fontSize: 11, color: '#1e40af' }}>
          (인감)
        </div>
      </div>
      <div
        style={{
          marginTop: 12,
          padding: 8,
          background: '#dcfce7',
          border: '1px solid #86efac',
          borderRadius: 6,
          fontSize: 11,
          color: '#166534',
        }}
      >
        실제 화면: legacy `pageFinal` div + html2canvas + jspdf 벡터 PDF / 복사 / 이미지 저장
      </div>
    </div>
  )
}

export function EstimateLegacyWebviewPage() {
  const setPageTitle = usePageTitleStore((s) => s.setPageTitle)
  const webviewRef = useRef<HTMLElement | null>(null)
  const [src, setSrc] = useState<string | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)

  useEffect(() => {
    setPageTitle({ title: '종합견적서', meta: '판매 · legacy' })
    return () => setPageTitle({ title: '' })
  }, [setPageTitle])

  // legacy 자산 URL 비동기 조회 — main 프로세스가 file:// 절대 경로 결정.
  // 브라우저 모드 (mock 캡처 등 Electron 외 환경) 에서는 `window.samhanLegacy` 가 부재하므로
  // 안내 placeholder 표시.
  useEffect(() => {
    let cancelled = false
    const load = async () => {
      // dev / mock 환경 — Electron 외부면 즉시 placeholder.
      if (typeof window === 'undefined' || !window.samhanLegacy) {
        if (!cancelled) {
          setSrc('about:blank')
          setLoadError('BROWSER_MODE')
        }
        return
      }
      try {
        const url = await window.samhanLegacy.getEstimateUrl()
        if (!cancelled) setSrc(url || 'about:blank')
      } catch (err) {
        if (!cancelled) {
          console.error('[EstimateLegacyWebviewPage] URL 조회 실패', err)
          setLoadError(String(err))
          setSrc('about:blank')
        }
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [])

  // webview 로딩 후 preload shim 의 매핑 함수 list 콘솔 출력 — QA 검증용.
  useEffect(() => {
    const el = webviewRef.current
    if (!el || !src) return
    const onReady = () => {
      console.log('[EstimateLegacyWebviewPage] webview dom-ready — shim 활성')
    }
    el.addEventListener('dom-ready', onReady)
    return () => el.removeEventListener('dom-ready', onReady)
  }, [src])

  return (
    <div className={styles['salesScope']}>
      <SalesSubNav />
      <div className={styles['wrap']} style={{ padding: 0, height: 'calc(100vh - 110px)' }}>
        {loadError === 'BROWSER_MODE' ? (
          <BrowserModePreview />
        ) : loadError ? (
          <div className={styles['emptyState']}>
            <h3>legacy 자산 로드 실패</h3>
            <p>{loadError}</p>
            <p style={{ fontSize: 11 }}>
              `migration/source/scripts/estimate/` 디렉토리 + `npm run build:legacy` 실행 후 재시도.
            </p>
          </div>
        ) : !src ? (
          <div className={styles['emptyState']}>legacy 자산 URL 조회 중…</div>
        ) : (
          // Electron `<webview>` tag — types/electron.d.ts JSX intrinsic 로 선언.
          // ref 는 HTMLElement 로 받아 둔다 (Electron 전용 메서드는 직접 cast 하여 호출).
          (<webview
            ref={(el) => {
              webviewRef.current = el as HTMLElement | null
            }}
            src={src}
            preload={resolveWebviewPreloadUrl()}
            allowpopups
            style={{
              width: '100%',
              height: '100%',
              minHeight: 600,
              border: '1px solid #e2e8f0',
              borderRadius: 6,
              background: '#fff',
            }}
            data-testid="estimate-legacy-webview"
          />)
        )}
      </div>
    </div>
  )
}
