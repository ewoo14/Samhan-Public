/**
 * legacy estimate webview 용 preload — `<webview src="legacy://...">` 의 격리된 컨텍스트에
 * 주입되어 Apps Script 호환 `window.google.script.run` API 를 제공한다.
 *
 * <h2>shim 설계</h2>
 * <ul>
 *   <li>legacy `index.html` 은 모든 backend 호출을 `google.script.run.withSuccessHandler(cb)
 *       .withFailureHandler(cb).<fnName>(...args)` 패턴으로 수행한다.</li>
 *   <li>본 shim 은 Proxy 로 임의 fnName 을 가로채 SamhanLogis MS endpoint 매핑
 *       (`samhanApi.call(fnName, args)`) 으로 routing.</li>
 *   <li>외부 호출 (e-Count UrlFetchApp / Notion API) 은 server-side Code.js 만 호출하므로
 *       webview 내 client-side 코드에서는 발생하지 않는다. 안전망으로 noop + warn.</li>
 * </ul>
 *
 * <h2>contextIsolation</h2>
 * <p>webview 는 nodeIntegration=false / contextIsolation=true. 본 preload 는
 * contextBridge 로 `window.google` 객체를 노출한다. (단순 namespace shim 이므로
 * exposeInMainWorld 만 사용, IPC 직접 호출 X)</p>
 *
 * <h2>endpoint 매핑</h2>
 * <p>{@link samhanApi} 가 fnName → SamhanLogis MS endpoint mapping table 을 보유.
 * 매핑 누락 시 noop + console.warn. 실 fetch 는 fetch API (axios 미사용 — preload 컨텍스트
 * 의 의존성 최소화).</p>
 *
 * <p>매핑 표 상세: {@code docs/dev-reports/legacy-rpc-mapping-estimate.md}</p>
 */
import { contextBridge } from 'electron'
import { samhanApi, type RpcResponse } from './samhanApi'

interface RunChain {
  withSuccessHandler(cb: (result: unknown) => void): RunChain
  withFailureHandler(cb: (err: unknown) => void): RunChain
  [fnName: string]: ((...args: unknown[]) => void) | unknown
}

/**
 * `google.script.run` Proxy — get(prop) 시 prop 이 `withSuccessHandler` /
 * `withFailureHandler` 면 새 chain 시작, 그 외 임의 prop 이면 즉시 실행 함수를 반환.
 *
 * Apps Script 의 실제 동작:
 *   `google.script.run.withSuccessHandler(cb).withFailureHandler(cb).fnName(args)`
 *   → fnName(args) 호출 즉시 server 호출 시작, cb 는 결과 도착 시 호출
 */
function createRunProxy(): RunChain {
  let onSuccess: ((result: unknown) => void) | null = null
  let onFailure: ((err: unknown) => void) | null = null

  const handler: ProxyHandler<RunChain> = {
    get(_target, prop: string) {
      if (prop === 'withSuccessHandler') {
        return (cb: (result: unknown) => void) => {
          onSuccess = cb
          return new Proxy({} as RunChain, handler)
        }
      }
      if (prop === 'withFailureHandler') {
        return (cb: (err: unknown) => void) => {
          onFailure = cb
          return new Proxy({} as RunChain, handler)
        }
      }
      // 임의 fnName — 실 RPC 호출 함수 반환
      return (...args: unknown[]) => {
        samhanApi
          .call(prop, args)
          .then((res: RpcResponse) => {
            try {
              if (onSuccess) onSuccess(res)
            } catch (cbErr) {
              console.error(`[legacyShim] success cb 예외 (${prop})`, cbErr)
            }
          })
          .catch((err: unknown) => {
            try {
              if (onFailure) onFailure(err)
              else console.error(`[legacyShim] ${prop} 실패 (failure handler 없음)`, err)
            } catch (cbErr) {
              console.error(`[legacyShim] failure cb 예외 (${prop})`, cbErr)
            }
          })
      }
    },
  }

  return new Proxy({} as RunChain, handler)
}

/**
 * Apps Script `google.script.run` namespace 를 contextBridge 로 노출.
 *
 * legacy index.html 의 inline `<script>` 는 `window.google.script.run.X(...)` 로 호출하므로,
 * exposeInMainWorld 의 `google` 키 아래로 동일 구조를 매핑한다.
 *
 * 추가로 `window.samhanShim` 를 노출 — 매핑 표 누락 함수 디버깅 / fallback noop 활성 여부.
 */
function installShim(): void {
  try {
    const google = {
      script: {
        get run() {
          return createRunProxy()
        },
      },
    }
    contextBridge.exposeInMainWorld('google', google)
    contextBridge.exposeInMainWorld('samhanShim', {
      version: '4.0.0',
      mappedFunctions: samhanApi.mappedFunctions(),
    })
    console.log(
      `[legacyShim] 주입 완료 — 매핑 함수 ${samhanApi.mappedFunctions().length}개`,
    )
  } catch (err) {
    console.error('[legacyShim] 주입 실패 — webview 가 일반 브라우저로 fallback', err)
  }
}

installShim()
