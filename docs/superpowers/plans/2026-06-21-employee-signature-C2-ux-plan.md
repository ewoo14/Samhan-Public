> **슬라이스 플랜** — 에픽 인덱스: [2026-06-21-employee-signature-stamp-plan.md](2026-06-21-employee-signature-stamp-plan.md) (Global Constraints · 공유 계약 · 실행 방식 · 구현시점 확인항목). 본 파일 = 단일 슬라이스 = 1 PR. Step 은 `- [ ]` 체크박스로 추적.

## Slice C2: 등록 UX + 모바일 공개 웹앱 (desktop FE + 신규 mobile-public)

**PR boundary:** 이 슬라이스 = PR 1개. `[FEAT] 사원 서명 등록 UX + 모바일 공개 웹앱 (C2)`. **FE 중심 + BE qrUrl 1줄 정합(Task C2.0)** — C1b 가 발급하는 `qrUrl` 이 모바일 공개 웹앱 페이지가 아니라 POST API 엔드포인트(`/api/public/employee-signatures/{token}`)를 가리키는 계약 불일치를 바로잡는 user-service 1파일 수정(+IT 단언 갱신)을 본 슬라이스에 포함한다(아래 Task C2.0). 그 외는 desktop FE + 신규 web 번들 + nginx 정적 서빙/배포 메모. 의존 = **C1a/C1b 이미 머지**(PR #547/#548, main `12097acba`) — 공개·관리자 엔드포인트 **실 계약**(아래 공유 계약, 검증 완료 ground truth 그대로). desktop 모달은 mock(`VITE_MOCK_MODE=1`)로 Playwright green, 실 BE 연동은 Docker 2-디바이스 실QA. 조기 PR([[feedback_open_pr_early]]): Task C2.0 첫 push 직후 PR 오픈.

> 정찰로 확정한 ground truth (구현자는 이 전제를 신뢰하고 시작):
> - QR 라이브러리는 모노레포에 **없음**(`grep qrcode|react-qr` 0건). → desktop renderer 에 `qrcode@^1.5.4`(순수 JS, native dep 0, MIT, `QRCode.toDataURL()`) 1개만 추가. design-system 에는 QR 컴포넌트 없음 → 추가 안 함.
> - `@samhan/design-system` 은 `SignaturePad`/`SignatureViewer`/`SignaturePadHandle` 를 이미 export(`src/index.ts:38-39`). desktop 은 `file:../web/design-system` junction 소비(`clients/desktop/package.json:26`). 신규 mobile-public 도 동일 `file:` 의존 + `@vitejs/plugin-react`.
> - desktop mock: `apiClient` 인터셉터가 `VITE_MOCK_MODE=1` 시 `getMockResponse(config)`(`mock.ts:1604`)를 axios adapter 로 주입. 핸들러는 `method`/`url` 매칭 → `envelope(data)` 또는 `mockError(status, code, msg)` 반환. Playwright 는 mock 위에서 회귀(`page.route` 미사용, `playwright.config.ts`).
> - `UsersPage.tsx` 행 "관리" 셀(`:282-356`)에 버튼들 존재 → 여기 "서명 등록" 버튼 추가. modal 은 `CreateUserModal` 패턴(design-system `Modal`/`Button`/`FormField`/`Input`) 재사용.
> - 모바일 mock(`MobileSignaturePage.tsx`)은 slip 인수자 전용. 신규 mobile-public 은 **사원 서명 전용** → `SignaturePad` 만 재사용. `sha256OfDataURL` 헬퍼(`MobileSignaturePage.tsx:36-45`)는 복제.
> - `mobile-public` 은 `/api/public/employee-signatures/{token}` 에 직접 POST. dev 는 vite proxy → api-gateway(8080), 운영은 **nginx** 정적 서빙(Task C2.6 — api-gateway 는 reactive WebFlux 라 정적 서빙 native 불가).
> - **⚠️ qrUrl 계약 정합(2026-06-21 재검토 확정)**: C1b 실제 머지본은 `qrUrl = {public-base-url}/api/public/employee-signatures/{token}`(=POST API URL, `EmployeeSignatureHandoffService.java:63`)를 발급 → 폰으로 열면 SignaturePad 페이지가 아니라 API 엔드포인트로 연결됨. **본 슬라이스 Task C2.0 이 qrUrl 을 웹앱 페이지 origin `{web-base-url}/s/{token}` 으로 바로잡는다.** desktop mock/테스트(C2.1·C2.3)와 mobile-public `readToken()`(C2.4)은 **이미 `/s/{token}` 형식을 가정**하고 있으므로 FE 측 변경 불요 — BE 만 정합시키면 끝. `HandoffTokenAdminControllerIT:83` 의 `containsString("/api/public/employee-signatures/")` 단언만 `/s/` 로 동반 갱신.

---

### Task C2.0: BE qrUrl 정합 — 모바일 웹앱 페이지 origin (`/s/{token}`) 발급 (user-service)

**근거(2026-06-21 재검토):** C1b 머지본(`EmployeeSignatureHandoffService.java:63`)은 `qrUrl = {public-base-url}/api/public/employee-signatures/{token}`(=POST API URL)를 발급한다. 사원이 폰으로 이 QR 을 열면 SignaturePad 웹앱이 아니라 **POST 전용 API 엔드포인트를 GET 으로 여는 꼴**(405/오류) → 모바일 손서명 화면으로 진입 불가. spec §5.2 의 "qrUrl(실 origin)" 의도는 **사람이 여는 웹앱 페이지**다. desktop mock/테스트(C2.1·C2.3)와 mobile-public `readToken()`(C2.4)은 **이미 `/s/{token}` 형식을 가정** → BE 만 정합시키면 전체 흐름이 닫힌다. C1b 머지됐지만 동일 에픽 후속 슬라이스라 본 PR 에 포함(개발책임자 2026-06-21 옵션 A 승인).

**Files:**
- modify: `services/user-service/src/main/java/com/samhanair/logis/user/service/EmployeeSignatureHandoffService.java`
- modify: `services/user-service/src/main/java/com/samhanair/logis/user/web/dto/HandoffTokenResponse.java` (record javadoc 의 qrUrl 설명 sweep)
- modify: `services/user-service/src/test/java/com/samhanair/logis/user/it/HandoffTokenAdminControllerIT.java`
- modify: `services/user-service/src/test/java/com/samhanair/logis/user/it/UserPermissionControllerIT.java` (@MockBean stub qrUrl 구경로 sweep — 테스트는 GREEN 이나 stale 박제 제거)

**Interfaces (Produces):** `HandoffTokenResponse.qrUrl = {public-base-url}/s/{token}` (token/expiresAt 불변). `app.signature.public-base-url`(env `SAMHAN_SIGNATURE_PUBLIC_BASE_URL`) 의미 = **모바일 공개 웹앱 origin** — 운영은 nginx same-origin 정적 서빙 origin(api-gateway 는 reactive 라 정적 native 불가), dev 는 vite mobile-public LAN origin(예 `http://192.168.x.x:5185`). API 제출 경로(`/api/public/employee-signatures/{token}`)는 mobile-public 앱이 **same-origin** 으로 POST(dev=C2.4 vite proxy `/api`→8080, 운영=nginx `proxy_pass`→api-gateway).

> ⚠️ 마이그레이션 무(코드+테스트만). **user-service 재배포 동반** — "C2=순수 FE" 전제는 본 Task 로 폐기됨(PR boundary 참조). Codex files-only(git/gradle 금지), Claude 가 `./gradlew` 검증 + 커밋 대행([[feedback_codex_sandbox_git]], 본 세션 교훈 3: `cmd /c gradlew.bat` 금지 → `./gradlew` 사용).

- [ ] **Step 1: 실패 테스트 — IT 단언 갱신.** `HandoffTokenAdminControllerIT.java` `토큰_발급_200_token64자_qrUrl_expiresAt` 의 단언을 교체. ⚠️ **구 단언은 `:83-84` 2줄에 걸쳐 있음**(`containsString(` 인자가 다음 줄로 wrap) → 두 줄을 함께 교체:
  ```java
  // 구(:83-84 2줄):
  //   .andExpect(jsonPath("$.data.qrUrl").value(org.hamcrest.Matchers.containsString(
  //           "/api/public/employee-signatures/")))
  // 신:
  .andExpect(jsonPath("$.data.qrUrl").value(org.hamcrest.Matchers.containsString("/s/")))
  .andExpect(jsonPath("$.data.qrUrl").value(org.hamcrest.Matchers.matchesPattern(".*/s/[A-Za-z0-9_-]{64}$")))
  ```
  (웹앱 페이지 `/s/{token}` 형식 + token 으로 끝남을 박제 — API 경로가 다시 새어들지 못하게.)
- [ ] **Step 2: 실행 (FAIL 예상).** `./gradlew :services:user-service:test --tests "*HandoffTokenAdminControllerIT"` → 구 경로(`/api/public/...`)라 `/s/` 단언 FAIL. (Windows Bash 도구는 `./gradlew` 사용; `cmd /c "gradlew.bat"` 미작동 — 본 에픽 세션 교훈 3.)
- [ ] **Step 3: 구현.** `EmployeeSignatureHandoffService.java:63` 의 qrUrl 빌드 1줄 교체:
  ```java
  // 모바일 공개 웹앱 페이지 origin + /s/{token} (사원이 폰으로 여는 SignaturePad 페이지).
  // API 제출(/api/public/employee-signatures/{token})은 웹앱이 same-origin 으로 POST.
  String qrUrl = normalizedPublicBaseUrl() + "/s/" + token.getToken();
  ```
  클래스 javadoc 의 "게이트웨이 공개 라우트 `/api/public/employee-signatures/{token}` 결합"(실제 `:29` 단독 — `:28` 의 "public-base-url = 모바일 공개 웹앱 origin" 은 이미 정합, 유지) → "웹앱 페이지 `/s/{token}` 경로 결합(API 제출은 웹앱 same-origin)" 로 갱신.
- [ ] **Step 3b: 구경로 전수 sweep([[feedback_defect_family_sweep_fix]]).** (a) `HandoffTokenResponse.java` record javadoc 의 `@param qrUrl ... 실 origin + /api/public/employee-signatures/{token}` → `웹앱 페이지 origin + /s/{token}` 갱신. (b) `UserPermissionControllerIT.java` 의 `@MockBean handoffService.issueToken()` stub qrUrl(`http://localhost:8080/api/public/employee-signatures/tok`) → `http://localhost:8080/s/tok` 갱신(테스트는 GREEN 이나 stale 박제 제거). (c) `grep -rn "/api/public/employee-signatures" services/user-service/src` 로 **qrUrl 참조** 잔존 0 확인 — ⚠️ 게이트웨이 `application.yml` 라우트·`PublicEmployeeSignatureController` `@RequestMapping`·mobile-public POST 경로는 **실 API 제출 경로라 정상값, 변경 금지**(QR 타깃 qrUrl 만 sweep).
- [ ] **Step 4: 실행 (PASS 예상).** `./gradlew :services:user-service:test --tests "*HandoffTokenAdminControllerIT"` → PASS.
- [ ] **Step 5: 변경 모듈 전체 test 완주.** `./gradlew :services:user-service:test`([[feedback_changed_module_full_test_before_push]] — 기존 서명/핸드오프 IT 회귀 동반 확인).
- [ ] **Step 6: 커밋(Claude 대행).** `git add services/user-service/src/main/java/com/samhanair/logis/user/service/EmployeeSignatureHandoffService.java services/user-service/src/test/java/com/samhanair/logis/user/it/HandoffTokenAdminControllerIT.java && git commit -F <파일>` 메시지: `fix(user): 핸드오프 qrUrl 을 모바일 웹앱 페이지 /s/{token} origin 으로 정합 (C2.0)`

---

### Task C2.1: adminApi.ts 서명 함수 + 타입 (desktop)

**Files:**
- modify: `clients/desktop/src/renderer/api/adminApi.ts`
- create: `clients/desktop/src/renderer/api/__tests__/adminApiSignature.test.ts`

**Interfaces (Produces):**
- `export type SignatureChannel = 'MOBILE_CANVAS' | 'UPLOAD'`
- `export interface EmployeeSignatureUploadRequest { signaturePngBase64: string; signatureHash: string; channel: SignatureChannel }`
- `export interface EmployeeSignatureResponse { registered: boolean; signedAt: string | null; signatureChannel: SignatureChannel | null }`
- `export interface HandoffTokenResponse { token: string; qrUrl: string; expiresAt: string }`
- `export interface HandoffStatusResponse { used: boolean; expired: boolean }`
- `export async function uploadUserSignature(id: string, body: EmployeeSignatureUploadRequest): Promise<EmployeeSignatureResponse>` → `PATCH /api/v1/admin/users/{id}/signature`
- `export async function createSignatureHandoffToken(id: string): Promise<HandoffTokenResponse>` → `POST /api/v1/admin/users/{id}/signature/handoff-token`
- `export async function fetchSignatureHandoffStatus(id: string, token: string): Promise<HandoffStatusResponse>` → `GET /api/v1/admin/users/{id}/signature/handoff/{token}/status`

**Consumes:** C1a `PATCH .../signature` (200 `EmployeeSignatureResponse`), C1b `POST .../handoff-token` (200 `HandoffTokenResponse`), C1b `GET .../handoff/{token}/status` (200 `HandoffStatusResponse`). `apiClient` + `ApiEnvelope<T>` (`api/client.ts`).

- [ ] **Step 1: 실패 테스트 — uploadUserSignature 가 올바른 path/body/envelope unwrap.** `clients/desktop/src/renderer/api/__tests__/adminApiSignature.test.ts`:
  ```ts
  import { describe, expect, it, vi, beforeEach } from 'vitest'
  import { apiClient } from '../client'
  import {
    uploadUserSignature,
    createSignatureHandoffToken,
    fetchSignatureHandoffStatus,
  } from '../adminApi'

  describe('adminApi 서명 함수', () => {
    beforeEach(() => vi.restoreAllMocks())

    it('uploadUserSignature 는 PATCH .../signature 호출 + data unwrap', async () => {
      const patch = vi.spyOn(apiClient, 'patch').mockResolvedValue({
        data: { success: true, code: 'OK', message: null, timestamp: '', data: { registered: true, signedAt: '2026-06-21T10:00:00', signatureChannel: 'UPLOAD' } },
      } as never)
      const res = await uploadUserSignature('u-1', {
        signaturePngBase64: 'data:image/png;base64,AAA',
        signatureHash: 'a'.repeat(64),
        channel: 'UPLOAD',
      })
      expect(patch).toHaveBeenCalledWith('/api/v1/admin/users/u-1/signature', {
        signaturePngBase64: 'data:image/png;base64,AAA',
        signatureHash: 'a'.repeat(64),
        channel: 'UPLOAD',
      })
      expect(res).toEqual({ registered: true, signedAt: '2026-06-21T10:00:00', signatureChannel: 'UPLOAD' })
    })

    it('createSignatureHandoffToken 는 POST .../handoff-token 호출 + data unwrap', async () => {
      const post = vi.spyOn(apiClient, 'post').mockResolvedValue({
        data: { success: true, code: 'OK', message: null, timestamp: '', data: { token: 'tok-x', qrUrl: 'https://sign.samhan-air.com/s/tok-x', expiresAt: '2026-06-21T10:10:00' } },
      } as never)
      const res = await createSignatureHandoffToken('u-1')
      expect(post).toHaveBeenCalledWith('/api/v1/admin/users/u-1/signature/handoff-token')
      expect(res.qrUrl).toBe('https://sign.samhan-air.com/s/tok-x')
    })

    it('fetchSignatureHandoffStatus 는 GET .../handoff/{token}/status 호출', async () => {
      const get = vi.spyOn(apiClient, 'get').mockResolvedValue({
        data: { success: true, code: 'OK', message: null, timestamp: '', data: { used: true, expired: false } },
      } as never)
      const res = await fetchSignatureHandoffStatus('u-1', 'tok-x')
      expect(get).toHaveBeenCalledWith('/api/v1/admin/users/u-1/signature/handoff/tok-x/status')
      expect(res).toEqual({ used: true, expired: false })
    })
  })
  ```
- [ ] **Step 2: 실행 (FAIL 예상).** `cd clients/desktop && npx vitest run src/renderer/api/__tests__/adminApiSignature.test.ts` → 함수 미존재로 import 에러 FAIL.
- [ ] **Step 3: 최소 구현.** `adminApi.ts` 끝(부서 섹션 위, 사용자 섹션 내)에 추가:
  ```ts
  // ---------------------------------------------------------------------------
  // 사원 서명 등록 (signature-slice-C C2) — AdminUserController .../signature
  // 공유 계약: PATCH 업로드 / POST 핸드오프 토큰 / GET 핸드오프 status.
  // UUID 비공개: id 는 path key 전용, 응답에 UUID 없음 (signedAt/channel 만).
  // ---------------------------------------------------------------------------

  /** 서명 입력 경로 — BE SignatureChannel enum 과 1:1 (CHECK IN ('MOBILE_CANVAS','UPLOAD')). */
  export type SignatureChannel = 'MOBILE_CANVAS' | 'UPLOAD'

  /** 서명 업로드 요청 — BE EmployeeSignatureUploadRequest 와 1:1. base64 dataURL + SHA-256 hex. */
  export interface EmployeeSignatureUploadRequest {
    signaturePngBase64: string
    signatureHash: string
    channel: SignatureChannel
  }

  /** 서명 등록 응답 — BE EmployeeSignatureResponse 와 1:1. */
  export interface EmployeeSignatureResponse {
    registered: boolean
    signedAt: string | null
    signatureChannel: SignatureChannel | null
  }

  /** 핸드오프 토큰 발급 응답 — BE HandoffTokenResponse 와 1:1. qrUrl = 모바일 공개 웹앱 실 origin. */
  export interface HandoffTokenResponse {
    token: string
    qrUrl: string
    expiresAt: string
  }

  /** 핸드오프 상태 폴링 응답 — BE HandoffStatusResponse 와 1:1. */
  export interface HandoffStatusResponse {
    used: boolean
    expired: boolean
  }

  /**
   * 서명 이미지 업로드 — PATCH /api/v1/admin/users/{id}/signature.
   * 권한 admin.users UPDATE. BE 가 hash 재검증 + PNG magic-byte + ≤50KB 가드(초과 422).
   */
  export async function uploadUserSignature(
    id: string,
    body: EmployeeSignatureUploadRequest,
  ): Promise<EmployeeSignatureResponse> {
    const res = await apiClient.patch<ApiEnvelope<EmployeeSignatureResponse>>(
      `/api/v1/admin/users/${id}/signature`,
      body,
    )
    return res.data.data
  }

  /**
   * 모바일 핸드오프 토큰 발급 — POST /api/v1/admin/users/{id}/signature/handoff-token.
   * 재발급 시 동일 사원 미사용 토큰은 BE 가 무효화. TTL 10분.
   */
  export async function createSignatureHandoffToken(
    id: string,
  ): Promise<HandoffTokenResponse> {
    const res = await apiClient.post<ApiEnvelope<HandoffTokenResponse>>(
      `/api/v1/admin/users/${id}/signature/handoff-token`,
    )
    return res.data.data
  }

  /**
   * 핸드오프 상태 폴링 — GET /api/v1/admin/users/{id}/signature/handoff/{token}/status.
   * used/expired 둘 다 true 가능(만료 후 소진 불가). 권한 admin.users VIEW.
   */
  export async function fetchSignatureHandoffStatus(
    id: string,
    token: string,
  ): Promise<HandoffStatusResponse> {
    const res = await apiClient.get<ApiEnvelope<HandoffStatusResponse>>(
      `/api/v1/admin/users/${id}/signature/handoff/${token}/status`,
    )
    return res.data.data
  }
  ```
- [ ] **Step 4: 실행 (PASS 예상).** `cd clients/desktop && npx vitest run src/renderer/api/__tests__/adminApiSignature.test.ts` → 3 PASS.
- [ ] **Step 5: 커밋.** `git add clients/desktop/src/renderer/api/adminApi.ts clients/desktop/src/renderer/api/__tests__/adminApiSignature.test.ts && git commit -F <파일>` 메시지: `feat(desktop): 사원 서명 등록 adminApi 함수 3종 + 타입 추가 (C2)`

---

### Task C2.2: 서명 이미지 정규화 유틸 (canvas, 외부 의존성 0, ≤50KB, SHA-256)

**Files:**
- create: `clients/desktop/src/renderer/utils/signatureImage.ts`
- create: `clients/desktop/src/renderer/utils/signatureImage.test.ts`

**Interfaces (Produces):**
- `export const PNG_MAX_BYTES = 50 * 1024`
- `export async function sha256OfDataUrl(dataUrl: string): Promise<string>` — base64 부분 → SHA-256 hex 64자 (Web Crypto)
- `export function dataUrlByteLength(dataUrl: string): number` — base64 → 바이트 수
- `export async function normalizeSignaturePng(file: File): Promise<{ dataUrl: string; hash: string; bytes: number }>` — canvas 리사이즈(최대 400×200, 인감 비율 유지) + PNG 재인코딩 + ≤50KB 가드(초과 시 throw `Error('SIGNATURE_TOO_LARGE')`). 외부 라이브러리 0 (브라우저 canvas API only).

**Consumes:** 없음 (브라우저 내장 API). vitest node 환경에는 canvas 없음 → `normalizeSignaturePng` 는 Playwright(실 브라우저)에서 검증, 순수 함수(`sha256OfDataUrl`/`dataUrlByteLength`)만 vitest 로 단언.

- [ ] **Step 1: 실패 테스트 — 순수 함수.** `clients/desktop/src/renderer/utils/signatureImage.test.ts`:
  ```ts
  import { describe, expect, it } from 'vitest'
  import { dataUrlByteLength, sha256OfDataUrl, PNG_MAX_BYTES } from './signatureImage'

  // 1x1 투명 PNG (base64) — 알려진 dataURL.
  const ONE_PX_PNG = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=='

  describe('signatureImage 순수 함수', () => {
    it('PNG_MAX_BYTES 는 51200', () => {
      expect(PNG_MAX_BYTES).toBe(50 * 1024)
    })

    it('dataUrlByteLength 는 base64 디코딩 바이트 수 반환', () => {
      // "AAAA" base64 = 3바이트
      expect(dataUrlByteLength('data:image/png;base64,AAAA')).toBe(3)
    })

    it('sha256OfDataUrl 은 64자 hex 반환 (결정적)', async () => {
      const h = await sha256OfDataUrl(ONE_PX_PNG)
      expect(h).toMatch(/^[0-9a-f]{64}$/)
      expect(await sha256OfDataUrl(ONE_PX_PNG)).toBe(h)
    })
  })
  ```
- [ ] **Step 2: 실행 (FAIL).** `cd clients/desktop && npx vitest run src/renderer/utils/signatureImage.test.ts` → 모듈 미존재 FAIL.
- [ ] **Step 3: 구현.** `clients/desktop/src/renderer/utils/signatureImage.ts`:
  ```ts
  /**
   * 사원 서명 이미지 정규화 유틸 — signature-slice-C C2 (외부 의존성 0).
   *
   * 업로드 경로(UPLOAD)에서 파일 → canvas 리사이즈 → PNG 재인코딩 → ≤50KB 가드 + SHA-256.
   * 모바일 손그림(MOBILE_CANVAS)은 SignaturePad.toDataURL() 이 이미 PNG dataURL 이라 본 유틸의
   * sha256OfDataUrl 만 사용. slip MobileSignaturePage.sha256OfDataURL 패턴과 동일 알고리즘.
   */

  /** 서버 가드(slip PNG_MAX_BYTES=50*1024)와 동일 — 클라 선검증으로 UX 빠른 피드백. */
  export const PNG_MAX_BYTES = 50 * 1024

  /** dataURL("data:image/png;base64,XXXX")의 base64 디코딩 바이트 수. */
  export function dataUrlByteLength(dataUrl: string): number {
    const base64 = dataUrl.split(',')[1] ?? ''
    const padding = (base64.match(/=+$/)?.[0]?.length ?? 0)
    return Math.floor((base64.length * 3) / 4) - padding
  }

  /** dataURL 의 base64 → SHA-256 hex 64자 (Web Crypto). BE 재검증 키. */
  export async function sha256OfDataUrl(dataUrl: string): Promise<string> {
    const base64 = dataUrl.split(',')[1] ?? ''
    const binary = atob(base64)
    const bytes = new Uint8Array(binary.length)
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
    const buf = await crypto.subtle.digest('SHA-256', bytes)
    return Array.from(new Uint8Array(buf))
      .map((b) => b.toString(16).padStart(2, '0'))
      .join('')
  }

  /** 인감 최대 박스 (px) — 디자이너 가이드 잠정. 비율 유지 contain. */
  const MAX_W = 400
  const MAX_H = 200

  /**
   * 업로드 파일 → 정규화 PNG dataURL + hash + bytes.
   * 1) Image 로드 2) MAX_W×MAX_H contain 리사이즈 3) canvas PNG 재인코딩 4) ≤50KB 가드.
   * ≤50KB 초과 시 Error('SIGNATURE_TOO_LARGE'). 흰배경 투명화는 best-effort 미적용(원본 보존).
   */
  export async function normalizeSignaturePng(
    file: File,
  ): Promise<{ dataUrl: string; hash: string; bytes: number }> {
    const objectUrl = URL.createObjectURL(file)
    try {
      const img = await new Promise<HTMLImageElement>((resolve, reject) => {
        const el = new Image()
        el.onload = () => resolve(el)
        el.onerror = () => reject(new Error('SIGNATURE_IMAGE_DECODE_FAILED'))
        el.src = objectUrl
      })
      const scale = Math.min(MAX_W / img.width, MAX_H / img.height, 1)
      const w = Math.max(1, Math.round(img.width * scale))
      const h = Math.max(1, Math.round(img.height * scale))
      const canvas = document.createElement('canvas')
      canvas.width = w
      canvas.height = h
      const ctx = canvas.getContext('2d')
      if (!ctx) throw new Error('SIGNATURE_CANVAS_UNAVAILABLE')
      ctx.drawImage(img, 0, 0, w, h)
      const dataUrl = canvas.toDataURL('image/png')
      const bytes = dataUrlByteLength(dataUrl)
      if (bytes > PNG_MAX_BYTES) throw new Error('SIGNATURE_TOO_LARGE')
      const hash = await sha256OfDataUrl(dataUrl)
      return { dataUrl, hash, bytes }
    } finally {
      URL.revokeObjectURL(objectUrl)
    }
  }
  ```
- [ ] **Step 4: 실행 (PASS).** `cd clients/desktop && npx vitest run src/renderer/utils/signatureImage.test.ts` → 3 PASS.
- [ ] **Step 5: 커밋.** `git add clients/desktop/src/renderer/utils/signatureImage.ts clients/desktop/src/renderer/utils/signatureImage.test.ts && git commit -F <파일>` 메시지: `feat(desktop): 서명 이미지 정규화 유틸 (canvas ≤50KB + SHA-256, 외부의존 0) (C2)`

---

### Task C2.3: UsersPage 서명 등록 모달 (업로드 + QR + 폴링)

**Files:**
- modify: `clients/desktop/src/renderer/routes/admin/UsersPage.tsx`
- modify: `clients/desktop/package.json` (`qrcode@^1.5.4` + `@types/qrcode@^1.5.5` 추가)
- create: `clients/desktop/playwright/signature-register/signature-register.spec.ts`
- modify: `clients/desktop/src/renderer/api/mock.ts` (mock 핸들러 3종)

**Interfaces (Produces):**
- UsersPage 행 "관리" 셀에 `data-testid="admin-user-signature-button"` 버튼 → `SignatureRegisterModal` 오픈.
- `SignatureRegisterModal` (UsersPage 내부 컴포넌트): `data-testid="admin-user-signature-modal"`. 탭 2: `signature-tab-upload` / `signature-tab-mobile`.
- 업로드: `<input type="file" data-testid="signature-file-input">` → `normalizeSignaturePng` → `SignatureViewer` 미리보기(`signature-preview`) → `uploadUserSignature`. 50KB 초과 시 `signature-too-large-error`.
- 모바일: `createSignatureHandoffToken` → QR `<img data-testid="signature-qr-image">` (`QRCode.toDataURL(qrUrl)`) + `<span data-testid="signature-copy-link"><CopyButton text={qrUrl} /></span>`(⚠️ CopyButton prop=`text`, testid 는 래퍼 span — CopyButton 은 임의 prop spread 안 함) + 2s 폴링(`fetchSignatureHandoffStatus`) until used/expired/모달닫힘. used → `signature-mobile-done`.

**Consumes:** Task C2.1 (`uploadUserSignature`/`createSignatureHandoffToken`/`fetchSignatureHandoffStatus`), Task C2.2 (`normalizeSignaturePng`/`PNG_MAX_BYTES`), design-system `SignatureViewer`/`CopyButton`/`Modal`/`Button`, `qrcode`.

- [ ] **Step 1: mock 핸들러 추가 (테스트 선결).** `clients/desktop/src/renderer/api/mock.ts` `getMockResponse` 내부(사용자 섹션 근처)에 추가. 핸드오프 status 는 첫 폴 미사용→두번째 used 전이(`window` 카운터로 결정성):
  ```ts
  // PATCH /api/v1/admin/users/{id}/signature — 업로드 등록
  if (method === 'PATCH' && /\/api\/v1\/admin\/users\/[^/]+\/signature$/.test(url)) {
    const body = parseMockBody(config)
    if (typeof body['signatureHash'] !== 'string' || (body['signatureHash'] as string).length !== 64) {
      return mockError(400, 'SIGNATURE_HASH_MISMATCH', '서명 해시가 올바르지 않습니다.')
    }
    return envelope({ registered: true, signedAt: '2026-06-21T10:00:00', signatureChannel: body['channel'] ?? 'UPLOAD' })
  }
  // POST /api/v1/admin/users/{id}/signature/handoff-token — 토큰 발급
  if (method === 'POST' && /\/api\/v1\/admin\/users\/[^/]+\/signature\/handoff-token$/.test(url)) {
    ;(window as unknown as { __SIG_POLL__?: number }).__SIG_POLL__ = 0
    return envelope({ token: 'mock-token-1', qrUrl: 'https://sign.samhan-air.com/s/mock-token-1', expiresAt: '2026-06-21T10:10:00' })
  }
  // GET /api/v1/admin/users/{id}/signature/handoff/{token}/status — 폴링 (2번째부터 used)
  if (method === 'GET' && /\/api\/v1\/admin\/users\/[^/]+\/signature\/handoff\/[^/]+\/status$/.test(url)) {
    const w = window as unknown as { __SIG_POLL__?: number }
    w.__SIG_POLL__ = (w.__SIG_POLL__ ?? 0) + 1
    return envelope({ used: w.__SIG_POLL__ >= 2, expired: false })
  }
  ```
- [ ] **Step 2: 실패 Playwright 테스트.** `clients/desktop/playwright/signature-register/signature-register.spec.ts`:
  ```ts
  import { test, expect } from '@playwright/test'

  // VITE_MOCK_MODE dev server (webServer) 위에서 실행. UsersPage 진입 = MASTER mock 세션.
  const USERS_URL = '/#/admin/users?mockRole=MASTER&mockDepartment=대표실'

  test.describe('사원 서명 등록 모달 (C2)', () => {
    test('관리 셀 서명 등록 버튼 → 모달 오픈', async ({ page }) => {
      await page.goto(USERS_URL)
      await page.getByTestId('admin-users-table').waitFor()
      await page.getByTestId('admin-user-signature-button').first().click()
      await expect(page.getByTestId('admin-user-signature-modal')).toBeVisible()
      await expect(page.getByTestId('signature-tab-upload')).toBeVisible()
      await expect(page.getByTestId('signature-tab-mobile')).toBeVisible()
    })

    test('이미지 업로드 → 미리보기 → 등록 성공', async ({ page }) => {
      await page.goto(USERS_URL)
      await page.getByTestId('admin-users-table').waitFor()
      await page.getByTestId('admin-user-signature-button').first().click()
      await page.getByTestId('signature-tab-upload').click()
      const buffer = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==', 'base64')
      await page.getByTestId('signature-file-input').setInputFiles({ name: 's.png', mimeType: 'image/png', buffer })
      await expect(page.getByTestId('signature-preview')).toBeVisible()
      await page.getByTestId('signature-upload-submit').click()
      await expect(page.getByTestId('signature-upload-done')).toBeVisible()
    })

    test('모바일로 그리기 → QR 발급 + 복사링크 + 폴링 used 감지', async ({ page }) => {
      await page.goto(USERS_URL)
      await page.getByTestId('admin-users-table').waitFor()
      await page.getByTestId('admin-user-signature-button').first().click()
      await page.getByTestId('signature-tab-mobile').click()
      await page.getByTestId('signature-handoff-issue').click()
      await expect(page.getByTestId('signature-qr-image')).toBeVisible()
      await expect(page.getByTestId('signature-copy-link')).toBeVisible()
      await expect(page.getByTestId('signature-mobile-done')).toBeVisible({ timeout: 8000 })
    })
  })
  ```
- [ ] **Step 3: 실행 (FAIL).** `cd clients/desktop && npx playwright test signature-register/signature-register.spec.ts --reporter=line` → testid 미존재 FAIL (webServer 자동 기동, `VITE_MOCK_MODE=1`).
- [ ] **Step 4: 의존성 추가 + import.** `package.json` `dependencies` 에 `"qrcode": "^1.5.4"`, `devDependencies` 에 `"@types/qrcode": "^1.5.5"` 추가 후 `cd clients/desktop && npm install`. `UsersPage.tsx` 상단 import 에 `SignatureViewer`, `CopyButton` 를 `@samhan/design-system` 에서, `uploadUserSignature, createSignatureHandoffToken, fetchSignatureHandoffStatus, type EmployeeSignatureResponse` 를 `../../api/adminApi` 에서, `normalizeSignaturePng` 를 `../../utils/signatureImage` 에서, `import QRCode from 'qrcode'`, 그리고 `useEffect` 를 `react` 에서 추가.
- [ ] **Step 5: 버튼 + 모달 구현.** `UsersPage` 컴포넌트에 `const [signatureModal, setSignatureModal] = useState<AdminUser | null>(null)` 추가. "관리" 셀 버튼 배열(수정 버튼 옆)에:
  ```tsx
  <Button
    variant="ghost"
    size="sm"
    data-testid="admin-user-signature-button"
    onClick={(e) => {
      e.stopPropagation()
      setSignatureModal(u)
    }}
  >
    서명 등록
  </Button>
  ```
  렌더 트리 모달 블록(`disableModal` 블록 아래)에:
  ```tsx
  {signatureModal ? (
    <SignatureRegisterModal
      user={signatureModal}
      onClose={() => setSignatureModal(null)}
    />
  ) : null}
  ```
  파일 하단에 `SignatureRegisterModal` 추가:
  ```tsx
  // ---------------------------------------------------------------------------
  // SignatureRegisterModal — 사원 서명 등록 (이미지 업로드 + 모바일 핸드오프)
  // 공유 계약: PATCH .../signature (UPLOAD) / POST .../handoff-token / GET .../status
  // UUID 비공개: user.id 는 path key 전용, 화면 라벨은 fullName/loginId.
  // ---------------------------------------------------------------------------
  function SignatureRegisterModal({ user, onClose }: { user: AdminUser; onClose: () => void }) {
    const [tab, setTab] = useState<'upload' | 'mobile'>('upload')
    const [preview, setPreview] = useState<{ dataUrl: string; hash: string } | null>(null)
    const [tooLarge, setTooLarge] = useState(false)
    const [uploadDone, setUploadDone] = useState<EmployeeSignatureResponse | null>(null)
    const [handoff, setHandoff] = useState<{ token: string; qrUrl: string; qrDataUrl: string } | null>(null)
    const [mobileDone, setMobileDone] = useState(false)
    const [mobileExpired, setMobileExpired] = useState(false)

    const uploadMutation = useMutation({
      mutationFn: () => {
        if (!preview) throw new Error('NO_PREVIEW')
        return uploadUserSignature(user.id, {
          signaturePngBase64: preview.dataUrl,
          signatureHash: preview.hash,
          channel: 'UPLOAD',
        })
      },
      onSuccess: (res) => setUploadDone(res),
    })

    const handleFile = async (file: File) => {
      setTooLarge(false)
      try {
        const { dataUrl, hash } = await normalizeSignaturePng(file)
        setPreview({ dataUrl, hash })
      } catch (err) {
        if (err instanceof Error && err.message === 'SIGNATURE_TOO_LARGE') setTooLarge(true)
        else setPreview(null)
      }
    }

    const issueMutation = useMutation({
      mutationFn: () => createSignatureHandoffToken(user.id),
      onSuccess: async (res) => {
        const qrDataUrl = await QRCode.toDataURL(res.qrUrl, { width: 220, margin: 1 })
        setHandoff({ token: res.token, qrUrl: res.qrUrl, qrDataUrl })
      },
    })

    // 2s 폴링 — handoff 발급 후 used/expired/언마운트 시 종료
    useEffect(() => {
      if (!handoff || mobileDone || mobileExpired) return
      let cancelled = false
      const timer = setInterval(async () => {
        try {
          const s = await fetchSignatureHandoffStatus(user.id, handoff.token)
          if (cancelled) return
          if (s.used) setMobileDone(true)
          else if (s.expired) setMobileExpired(true)
        } catch {
          // 폴링 일시 실패 무시 (다음 tick 재시도)
        }
      }, 2000)
      return () => {
        cancelled = true
        clearInterval(timer)
      }
    }, [handoff, mobileDone, mobileExpired, user.id])

    return (
      <Modal
        open
        onClose={onClose}
        title={`서명 등록 — ${user.fullName} (${user.loginId})`}
        size="lg"
        footer={<Button variant="ghost" onClick={onClose}>닫기</Button>}
      >
        <div data-testid="admin-user-signature-modal" style={formColStyle}>
          <div style={{ display: 'flex', gap: 8 }}>
            <Button variant={tab === 'upload' ? 'primary' : 'ghost'} size="sm" data-testid="signature-tab-upload" onClick={() => setTab('upload')}>이미지 업로드</Button>
            <Button variant={tab === 'mobile' ? 'primary' : 'ghost'} size="sm" data-testid="signature-tab-mobile" onClick={() => setTab('mobile')}>모바일로 그리기</Button>
          </div>

          {tab === 'upload' ? (
            uploadDone ? (
              <div data-testid="signature-upload-done" style={{ fontSize: 14 }}>서명이 등록되었습니다.</div>
            ) : (
              <>
                <input
                  type="file"
                  accept="image/png,image/jpeg"
                  data-testid="signature-file-input"
                  onChange={(e) => { const f = e.target.files?.[0]; if (f) void handleFile(f) }}
                />
                {tooLarge ? (
                  <div role="alert" data-testid="signature-too-large-error" style={{ color: 'var(--state-danger)', fontSize: 13 }}>
                    서명 이미지가 50KB 를 초과합니다. 더 작은 이미지를 선택하세요.
                  </div>
                ) : null}
                {preview ? (
                  <div data-testid="signature-preview">
                    <SignatureViewer signaturePngBase64={preview.dataUrl} signerName={user.fullName} signedAt="" signatureHash={preview.hash} />
                  </div>
                ) : null}
                <Button variant="primary" data-testid="signature-upload-submit" disabled={!preview} loading={uploadMutation.isPending} onClick={() => uploadMutation.mutate()}>등록</Button>
              </>
            )
          ) : (
            <>
              {!handoff ? (
                <Button variant="primary" data-testid="signature-handoff-issue" loading={issueMutation.isPending} onClick={() => issueMutation.mutate()}>모바일 링크 발급</Button>
              ) : mobileDone ? (
                <div data-testid="signature-mobile-done" style={{ fontSize: 14 }}>모바일 서명이 등록되었습니다.</div>
              ) : mobileExpired ? (
                <div data-testid="signature-mobile-expired" style={{ color: 'var(--state-danger)', fontSize: 13 }}>링크가 만료되었습니다. 다시 발급하세요.</div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8 }}>
                  <img data-testid="signature-qr-image" src={handoff.qrDataUrl} alt="모바일 서명 QR" width={220} height={220} />
                  {/* ⚠️ CopyButton prop = text(필수), value 아님. data-testid 는 CopyButton 이 ...rest spread 안 하므로 래퍼 span 에 부착(검증 P1). */}
                  <span data-testid="signature-copy-link"><CopyButton text={handoff.qrUrl} label="링크 복사" /></span>
                  <span style={{ fontSize: 12, color: 'var(--ink-tertiary)' }}>휴대폰으로 QR 을 스캔해 서명하세요. (10분 유효)</span>
                </div>
              )}
            </>
          )}
        </div>
      </Modal>
    )
  }
  ```
- [ ] **Step 6: 실행 (PASS).** `cd clients/desktop && npx vitest run src/renderer/api/__tests__/adminApiSignature.test.ts` (회귀) + `npx playwright test signature-register/signature-register.spec.ts --reporter=line` → 3 PASS. 타입: `npm run typecheck`.
- [ ] **Step 7: 커밋.** `git add clients/desktop/src/renderer/routes/admin/UsersPage.tsx clients/desktop/src/renderer/api/mock.ts clients/desktop/package.json clients/desktop/package-lock.json clients/desktop/playwright/signature-register/signature-register.spec.ts && git commit -F <파일>` 메시지: `feat(desktop): 사원 서명 등록 모달 (업로드+QR+폴링) + qrcode 의존 (C2)`

---

### Task C2.4: 신규 mobile-public vite 앱 스캐폴딩 (단일 SignaturePad 서명 페이지)

**Files:**
- create: `clients/web/mobile-public/package.json`
- create: `clients/web/mobile-public/vite.config.ts`
- create: `clients/web/mobile-public/tsconfig.json`
- create: `clients/web/mobile-public/tsconfig.node.json`
- create: `clients/web/mobile-public/index.html`
- create: `clients/web/mobile-public/src/main.tsx`
- create: `clients/web/mobile-public/src/vite-env.d.ts`
- create: `clients/web/mobile-public/src/api.ts`
- modify: `scripts/run-client-local-dev.cjs` (`web-mobile-public` 타깃 등록)

**Interfaces (Produces):**
- `clients/web/mobile-public/src/api.ts`: `export interface PublicEmployeeSignatureRequest { signaturePngBase64: string; signatureHash: string }` + `export async function submitPublicSignature(token: string, body: PublicEmployeeSignatureRequest): Promise<void>` → `POST /api/public/employee-signatures/{token}` (200 성공 / 404·409·410 → throw) + `export async function sha256OfDataUrl(dataUrl: string): Promise<string>`.

**Consumes:** `@samhan/design-system` (`SignaturePad`/`SignaturePadHandle`/`Button`), 공유 계약 공개 엔드포인트 `POST /api/public/employee-signatures/{token}`. ⚠️ **`vite.config.ts` 는 아래 Step 2 스니펫 그대로 신규 작성**(order-app 은 vanilla-JS PWA — `@vitejs/plugin-react`·design-system 미포함 → React 클론 원본 아님; React+design-system+file:junction 선례는 `clients/desktop`). `tsconfig.json`/`tsconfig.node.json` 만 order-app 복제(React 비의존이라 무방, `jsx: "react-jsx"` 추가).

- [ ] **Step 1: package.json.** `clients/web/mobile-public/package.json`:
  ```json
  {
    "name": "@samhan/mobile-public",
    "version": "0.1.0",
    "private": true,
    "description": "삼한공조시스템 모바일 공개 서명 웹앱 — 사원 서명 손그림 제출 (NO-AUTH 토큰 게이트). slip 인수자 공개 서명도 향후 재사용.",
    "type": "module",
    "scripts": {
      "dev": "vite",
      "local-dev": "node ../../../scripts/run-client-local-dev.cjs web-mobile-public",
      "build": "tsc -p tsconfig.json --noEmit && vite build",
      "preview": "vite preview",
      "typecheck": "tsc -p tsconfig.json --noEmit",
      "lint": "eslint \"src/**/*.{ts,tsx}\"",
      "test": "vitest run"
    },
    "dependencies": {
      "@samhan/design-system": "file:../design-system",
      "axios": "^1.7.7",
      "react": "^18.3.1",
      "react-dom": "^18.3.1"
    },
    "devDependencies": {
      "@testing-library/react": "^16.1.0",
      "@types/react": "^18.3.12",
      "@types/react-dom": "^18.3.1",
      "@typescript-eslint/eslint-plugin": "^8.13.0",
      "@typescript-eslint/parser": "^8.13.0",
      "@vitejs/plugin-react": "^4.3.3",
      "eslint": "^9.14.0",
      "jsdom": "^25.0.1",
      "typescript": "^5.6.3",
      "vite": "^5.4.10",
      "vitest": "^2.1.4"
    }
  }
  ```
- [ ] **Step 2: vite/tsconfig/html/vite-env.**
  - `vite.config.ts`:
    ```ts
    import { defineConfig } from 'vite'
    import react from '@vitejs/plugin-react'
    import { resolve } from 'node:path'

    // 모바일 공개 서명 웹앱 — 운영은 nginx 정적 서빙(sign.samhan-air.com), dev 는 proxy → api-gateway(8080).
    export default defineConfig({
      plugins: [react()],
      resolve: { alias: { '@': resolve(__dirname, 'src') } },
      server: {
        port: 5185,
        host: true,
        proxy: { '/api': { target: process.env['VITE_API_BASE_URL'] ?? 'http://localhost:8080', changeOrigin: true } },
      },
      preview: { port: 5186 },
      build: { outDir: 'dist', sourcemap: true, target: 'es2020' },
    })
    ```
  - `tsconfig.json`: order-app `tsconfig.json` 복제하되 `compilerOptions.jsx: "react-jsx"` 추가, `include: ["src/**/*"]`.
  - `tsconfig.node.json`: order-app `tsconfig.node.json` 복제(`include: ["vite.config.ts"]`).
  - `index.html`:
    ```html
    <!doctype html>
    <html lang="ko">
      <head>
        <meta charset="UTF-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
        <title>삼한공조시스템 — 서명</title>
      </head>
      <body>
        <div id="root"></div>
        <script type="module" src="/src/main.tsx"></script>
      </body>
    </html>
    ```
  - `src/vite-env.d.ts`: `/// <reference types="vite/client" />`
- [ ] **Step 3: api.ts + main.tsx.**
  - `src/api.ts`:
    ```ts
    /** 모바일 공개 서명 제출 — NO-AUTH 토큰 게이트. POST /api/public/employee-signatures/{token}. */
    import axios from 'axios'

    const apiClient = axios.create({
      baseURL: import.meta.env['VITE_API_BASE_URL'] ?? '',
      timeout: 10_000,
      headers: { 'Content-Type': 'application/json' },
    })

    export interface PublicEmployeeSignatureRequest {
      signaturePngBase64: string
      signatureHash: string
    }

    /** 토큰 검증(미만료·미사용) 후 서명 저장. 404 무효 토큰 / 409 사용됨 / 410 만료 → axios error throw. */
    export async function submitPublicSignature(
      token: string,
      body: PublicEmployeeSignatureRequest,
    ): Promise<void> {
      await apiClient.post(`/api/public/employee-signatures/${encodeURIComponent(token)}`, body)
    }

    /** dataURL base64 → SHA-256 hex 64자 (Web Crypto). BE 재검증 키. */
    export async function sha256OfDataUrl(dataUrl: string): Promise<string> {
      const base64 = dataUrl.split(',')[1] ?? ''
      const binary = atob(base64)
      const bytes = new Uint8Array(binary.length)
      for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
      const buf = await crypto.subtle.digest('SHA-256', bytes)
      return Array.from(new Uint8Array(buf)).map((b) => b.toString(16).padStart(2, '0')).join('')
    }
    ```
  - `src/main.tsx`:
    ```tsx
    import { StrictMode } from 'react'
    import { createRoot } from 'react-dom/client'
    import '@samhan/design-system/tokens.css'
    import '@samhan/design-system/style.css'
    import { EmployeeSignaturePage } from './EmployeeSignaturePage'

    // 토큰 추출: path `/s/:token` 우선, 없으면 `?token=`.
    function readToken(): string {
      const m = window.location.pathname.match(/\/s\/([^/]+)/)
      if (m?.[1]) return decodeURIComponent(m[1])
      return new URLSearchParams(window.location.search).get('token') ?? ''
    }

    createRoot(document.getElementById('root')!).render(
      <StrictMode>
        <EmployeeSignaturePage token={readToken()} />
      </StrictMode>,
    )
    ```
- [ ] **Step 4: local-dev 등록 + 임시 stub + npm install.** `scripts/run-client-local-dev.cjs` `configs` 에 추가:
  ```js
  'web-mobile-public': {
    command: ['npm', 'run', 'dev'],
    // ⚠️ VITE_API_BASE_URL 주입 안 함(검증 P2). mobile-public 은 same-origin(빈 baseURL → vite proxy /api→8080)
    // 으로 POST. commonApi(http://localhost:8080) 절대 주입 시 폰(LAN)이 자기 localhost 로 직타 → 제출 실패.
    // 폰 접근 origin 은 SAMHAN_SIGNATURE_PUBLIC_BASE_URL=http://<PC-LAN-IP>:5185(=qrUrl base, Task C2.6)로 분리 주입.
    env: {},
  },
  ```
  typecheck 가 `EmployeeSignaturePage` import 로 깨지지 않도록, 본 Task 에서 임시 stub `clients/web/mobile-public/src/EmployeeSignaturePage.tsx` = `export function EmployeeSignaturePage(_props: { token: string }) { return null }` 생성(Task C2.5 가 본구현으로 대체). `cd clients/web/mobile-public && npm install` (file: junction 생성). ⚠️ `@samhan/design-system/{style.css,tokens.css}` import 는 design-system `dist/` 산출물을 가리키므로 **dist 존재 전제** — 부재 시(클린 체크아웃/회사 PC) `cd clients/web/design-system && npm run build` 선행(desktop 이 이미 소비 중이라 통상 존재).
- [ ] **Step 5: 빌드 검증 (PASS).** `cd clients/web/mobile-public && npm run typecheck` 통과.
- [ ] **Step 6: 커밋.** `git add clients/web/mobile-public/package.json clients/web/mobile-public/vite.config.ts clients/web/mobile-public/tsconfig.json clients/web/mobile-public/tsconfig.node.json clients/web/mobile-public/index.html clients/web/mobile-public/src/main.tsx clients/web/mobile-public/src/vite-env.d.ts clients/web/mobile-public/src/api.ts clients/web/mobile-public/src/EmployeeSignaturePage.tsx clients/web/mobile-public/package-lock.json scripts/run-client-local-dev.cjs && git commit -F <파일>` 메시지: `feat(mobile-public): 모바일 공개 서명 웹앱 스캐폴딩 (vite+React+design-system) (C2)`

---

### Task C2.5: mobile-public 서명 페이지 구현 + 제출 테스트

**Files:**
- modify: `clients/web/mobile-public/src/EmployeeSignaturePage.tsx` (Task C2.4 stub 대체)
- create: `clients/web/mobile-public/src/EmployeeSignaturePage.test.tsx`
- create: `clients/web/mobile-public/vitest.config.ts`

**Interfaces (Produces):** `export function EmployeeSignaturePage({ token }: { token: string }): JSX.Element`. 빈 토큰 → `mobile-signature-invalid-token`. 제출 성공 → `mobile-signature-success`. 409/410/404 → `mobile-signature-expired`. `data-testid`: `mobile-signature-pad-area`, `mobile-signature-submit`, `mobile-signature-success`, `mobile-signature-expired`, `mobile-signature-invalid-token`.

**Consumes:** `@samhan/design-system` `SignaturePad`/`SignaturePadHandle`/`Button`, Task C2.4 `submitPublicSignature`/`sha256OfDataUrl`. design-system `vitest.config.ts`(jsdom+react) 패턴.

- [ ] **Step 1: vitest.config.ts.** design-system 패턴 미러:
  ```ts
  import react from '@vitejs/plugin-react'
  import { defineConfig } from 'vitest/config'

  export default defineConfig({
    plugins: [react()],
    test: { include: ['src/**/*.test.tsx'], environment: 'jsdom', reporters: 'default', passWithNoTests: false },
  })
  ```
- [ ] **Step 2: 실패 테스트.** `clients/web/mobile-public/src/EmployeeSignaturePage.test.tsx`:
  ```tsx
  import { describe, expect, it, vi, beforeEach } from 'vitest'
  import { render, screen, fireEvent, waitFor } from '@testing-library/react'
  import { EmployeeSignaturePage } from './EmployeeSignaturePage'
  import * as api from './api'

  describe('EmployeeSignaturePage', () => {
    beforeEach(() => vi.restoreAllMocks())

    it('빈 토큰이면 invalid-token 표시', () => {
      render(<EmployeeSignaturePage token="" />)
      expect(screen.getByTestId('mobile-signature-invalid-token')).toBeTruthy()
    })

    it('유효 토큰이면 서명 패드 + 제출 버튼 렌더', () => {
      render(<EmployeeSignaturePage token="tok-1" />)
      expect(screen.getByTestId('mobile-signature-pad-area')).toBeTruthy()
      expect(screen.getByTestId('mobile-signature-submit')).toBeTruthy()
    })

    it('제출이 410 으로 실패하면 expired 화면', async () => {
      vi.spyOn(api, 'sha256OfDataUrl').mockResolvedValue('a'.repeat(64))
      vi.spyOn(api, 'submitPublicSignature').mockRejectedValue(
        Object.assign(new Error('gone'), { isAxiosError: true, response: { status: 410 } }),
      )
      render(<EmployeeSignaturePage token="tok-1" />)
      // 빈 캔버스는 submit disabled → 강제 활성 후 click (만료 분기 단언)
      const btn = screen.getByTestId('mobile-signature-submit') as HTMLButtonElement
      btn.removeAttribute('disabled')
      fireEvent.click(btn)
      await waitFor(() => expect(screen.getByTestId('mobile-signature-expired')).toBeTruthy())
    })
  })
  ```
- [ ] **Step 3: 실행 (FAIL).** `cd clients/web/mobile-public && npx vitest run src/EmployeeSignaturePage.test.tsx` → stub return null 이라 testid 미존재 FAIL.
- [ ] **Step 4: 구현.** `clients/web/mobile-public/src/EmployeeSignaturePage.tsx`:
  ```tsx
  /**
   * EmployeeSignaturePage — 모바일 공개 사원 서명 페이지 (NO-AUTH 토큰 게이트).
   *
   * 핸드오프 토큰(qrUrl 의 /s/:token)으로 진입 → design-system SignaturePad 손서명 →
   * POST /api/public/employee-signatures/{token}. 성공/만료(409 used·410 expired·404 무효) 화면.
   * UUID 비공개: 화면에 사원 식별자/UUID 미노출 — 토큰만.
   */
  import { useRef, useState } from 'react'
  import { Button, SignaturePad, type SignaturePadHandle } from '@samhan/design-system'
  import { submitPublicSignature, sha256OfDataUrl } from './api'

  type Phase = 'sign' | 'success' | 'expired'

  function isExpiredError(err: unknown): boolean {
    const e = err as { response?: { status?: number } }
    const s = e?.response?.status
    return s === 409 || s === 410 || s === 404
  }

  export function EmployeeSignaturePage({ token }: { token: string }) {
    const padRef = useRef<SignaturePadHandle>(null)
    const [empty, setEmpty] = useState(true)
    const [submitting, setSubmitting] = useState(false)
    const [phase, setPhase] = useState<Phase>('sign')
    const [errorMsg, setErrorMsg] = useState<string | null>(null)

    if (!token) {
      return (
        <main style={{ padding: 24, textAlign: 'center' }}>
          <p data-testid="mobile-signature-invalid-token">유효하지 않은 서명 링크입니다.</p>
        </main>
      )
    }
    if (phase === 'success') {
      return (
        <main style={{ padding: 24, textAlign: 'center' }}>
          <p data-testid="mobile-signature-success">서명이 등록되었습니다. 창을 닫으셔도 됩니다.</p>
        </main>
      )
    }
    if (phase === 'expired') {
      return (
        <main style={{ padding: 24, textAlign: 'center' }}>
          <p data-testid="mobile-signature-expired">서명 링크가 만료되었거나 이미 사용되었습니다. 관리자에게 재발급을 요청하세요.</p>
        </main>
      )
    }

    const handleSubmit = async () => {
      const pad = padRef.current
      if (!pad) return
      const dataUrl = pad.toDataURL()
      if (!dataUrl) {
        setErrorMsg('서명 데이터를 가져올 수 없습니다.')
        return
      }
      setSubmitting(true)
      setErrorMsg(null)
      try {
        const hash = await sha256OfDataUrl(dataUrl)
        await submitPublicSignature(token, { signaturePngBase64: dataUrl, signatureHash: hash })
        setPhase('success')
      } catch (err) {
        if (isExpiredError(err)) setPhase('expired')
        else setErrorMsg('전송에 실패했습니다. 잠시 후 다시 시도해주세요.')
        setSubmitting(false)
      }
    }

    return (
      <main style={{ maxWidth: 480, margin: '0 auto', padding: 16 }}>
        <h1 style={{ fontSize: 18, textAlign: 'center' }}>사원 서명 등록</h1>
        <p style={{ fontSize: 13, color: 'var(--ink-tertiary)', textAlign: 'center' }}>아래 영역에 서명한 후 등록을 눌러주세요.</p>
        <div data-testid="mobile-signature-pad-area" style={{ display: 'flex', justifyContent: 'center', margin: '16px 0' }}>
          <SignaturePad ref={padRef} disabled={submitting} onChange={setEmpty} />
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <Button variant="secondary" disabled={empty || submitting} onClick={() => { padRef.current?.clear(); setEmpty(true) }}>다시 서명</Button>
          <Button variant="primary" data-testid="mobile-signature-submit" disabled={empty || submitting} loading={submitting} onClick={() => void handleSubmit()}>등록</Button>
        </div>
        {errorMsg ? <div role="alert" style={{ color: 'var(--state-danger)', marginTop: 8 }}>{errorMsg}</div> : null}
      </main>
    )
  }
  ```
- [ ] **Step 5: 실행 (PASS).** `cd clients/web/mobile-public && npx vitest run src/EmployeeSignaturePage.test.tsx` → 3 PASS. `npm run typecheck` 통과.
- [ ] **Step 6: 커밋.** `git add clients/web/mobile-public/src/EmployeeSignaturePage.tsx clients/web/mobile-public/src/EmployeeSignaturePage.test.tsx clients/web/mobile-public/vitest.config.ts && git commit -F <파일>` 메시지: `feat(mobile-public): 사원 서명 페이지 + 제출/만료 분기 + 단위테스트 (C2)`

---

### Task C2.6: 게이트웨이 공개 라우트 충돌 회피 메모 + 배포 origin 문서

**Files:**
- create: `docs/dev-reports/signature-slice-C/c2-mobile-public-deploy.md`
- create: `clients/web/mobile-public/README.md`

**Interfaces (Consumes):** 공유 계약 `POST /api/public/employee-signatures/**` → user-service (게이트웨이 라우트, **C1b 머지 완료**). 본 Task 는 **문서 only** — 실 게이트웨이 yml 은 C1b 가 이미 추가. 여기서는 C2 산출물(mobile-public dist) 의 **nginx** 정적 서빙 경로·배포 origin·dev proxy 를 명문화하여 DevOps 가 두 경로(게이트웨이 API 라우트 + nginx `/s/**` 정적)를 정확히 매핑하도록 한다.

- [ ] **Step 1: 배포 문서 작성.** `docs/dev-reports/signature-slice-C/c2-mobile-public-deploy.md` 에: (1) 빌드 = `npm run build` → `dist/`(정적 SPA), (2) **운영 웹앱 origin(=qrUrl base) = `https://sign.samhan-air.com` → 웹앱 페이지 `https://sign.samhan-air.com/s/:token`**(Phase 5 deferred 해소 — 기존 nginx 404 를 mobile-public dist 로 교체, `nginx-sign-deferred.md` 연계). **권장 배치 = nginx 리버스프록시 same-origin**(`sign.samhan-air.com`): 동일 origin 에서 `/s/**` 는 mobile-public dist 정적(nginx `try_files` SPA fallback), `/api/public/employee-signatures/**` 는 `proxy_pass`→api-gateway:8080 → 웹앱이 same-origin POST(CORS 0). ⚠️ **api-gateway 는 reactive WebFlux(spring-cloud-gateway) 라 정적 파일 서빙 native 불가**(`spring-boot-starter-web` 금지 제약) → 정적 서빙 주체는 **게이트웨이가 아니라 nginx**(`nginx-sign-deferred.md:16-56` 실 경로, root `/var/www/sign-mobile` + `/public/` proxy_pass). 본 정적 서빙 와이어링은 **Phase 11 cutover 의 nginx 작업**(C2 코드 스코프 밖, deferred). (3) 게이트웨이 공개 라우트 = `/api/public/employee-signatures/**` → user-service(StripPrefix=1, JwtAuthentication 미적용, `StripInboundIdentityHeaders`) — 기존 `/api/public/**`→slip(`application.yml:79-86`)보다 **더 구체 경로**라 우선순위 확보(C1b 가 yml 추가 완료; 충돌 회피). (4) **qrUrl origin = `app.signature.public-base-url`(env `SAMHAN_SIGNATURE_PUBLIC_BASE_URL`) 가 환경별 주입(Task C2.0 으로 경로 `/s/{token}` 확정)** — ⚠️ **dev 는 폰이 PC 로 접속해야 하므로 `localhost`/`127.0.0.1` 불가 → `SAMHAN_SIGNATURE_PUBLIC_BASE_URL=http://<PC-LAN-IP>:5185`(vite mobile-public)** 로 주입하고, mobile-public vite proxy `/api`→8080 이 API 를 게이트웨이로 중계. (5) 재사용: 동일 앱이 slip 인수자 공개 서명(Phase 5) 호스트 가능. dev/staging/prod **웹앱 origin + API same-origin 결합**을 표로 정리(각 환경 `SAMHAN_SIGNATURE_PUBLIC_BASE_URL` 값 명시).
- [ ] **Step 2: README.** `clients/web/mobile-public/README.md` 에 목적(사원 서명 NO-AUTH 손그림), 실행(`npm run dev` :5185, proxy→8080), 빌드(`npm run build`→dist), 토큰 진입(`/s/:token` 또는 `?token=`), 보안(토큰 게이트·identity 헤더 strip은 게이트웨이) 명시.
- [ ] **Step 3: 커밋.** `git add docs/dev-reports/signature-slice-C/c2-mobile-public-deploy.md clients/web/mobile-public/README.md && git commit -F <파일>` 메시지: `docs(mobile-public): C2 배포 origin + 게이트웨이 공개 라우트 충돌회피 메모 (C2)`

---

### Task C2.7: 통합 게이트 — typecheck/lint/test/Playwright 전수 + Docker 2-디바이스 실QA

**Files:** (변경 없음 — 검증 only)

- [ ] **Step 0: BE 회귀 (C2.0).** `./gradlew :services:user-service:test` → PASS (C2.0 qrUrl `/s/{token}` 정합 + 기존 서명/핸드오프/공개제출 IT 회귀 동반, [[feedback_changed_module_full_test_before_push]]).
- [ ] **Step 1: desktop 전수.** `cd clients/desktop && npm run typecheck && npm run lint && npx vitest run && npx playwright test signature-register/signature-register.spec.ts --reporter=line` → 전부 PASS (변경 모듈 전체 test 완주, [[feedback_changed_module_full_test_before_push]]).
- [ ] **Step 2: mobile-public 전수.** `cd clients/web/mobile-public && npm run typecheck && npm run lint && npx vitest run && npm run build` → 전부 PASS (dist 생성 확인).
- [ ] **Step 3: Docker 실QA (C1a/C1b 머지 완료 — C2.0 qrUrl fix 포함 user-service 재빌드).** `docker compose up --build` 로 user-service(C1a/C1b + **C2.0 qrUrl 정합**)+api-gateway 기동([[feedback_overnight_live_capture]]). **⚠️ dev 전제: `SAMHAN_SIGNATURE_PUBLIC_BASE_URL=http://<PC-LAN-IP>:5185` 주입(localhost 면 폰 접근 불가) + mobile-public vite(:5185) 기동 + 폰이 동일 LAN.** (a) desktop UsersPage → 사원 행 "서명 등록" → 이미지 업로드 → SignatureViewer 미리보기 → 등록 200, (b) "모바일로 그리기" → QR 발급 → **실 폰으로 QR 스캔 시 mobile-public 손서명 페이지(`/s/{token}`)가 실제로 열리는지 확인**(C2.0 회귀 가드 — API URL 이 아니라 웹앱 페이지) → 손서명 → 제출 200 → desktop 폴링 used 감지 캡처([[feedback_no_fake_data_ever]] 실 캡처만, PIL 합성 금지). UUID 비노출 실증([[feedback_uuid_no_user_visibility]]). 캡처를 해당 라운드 리뷰 코멘트에 인라인 게시([[feedback_temp_multimodel_workflow]]).
- [ ] **Step 4: 산출.** 캡처는 `docs/qa/signature-c2/*.png` 저장 + PR 본문 인라인([[feedback_pr_qa_screenshots]]). 커밋 불필요(검증 게이트).