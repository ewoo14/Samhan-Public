---
name: feedback_electron_packaging_gotchas
description: 데스크톱 Electron 패키지(build:win) 함정 — design-system prod dep asar 크래시·preload ESM+sandbox white screen·winCodeSign 심링크·app.asar 잠금·CDP 검증
metadata:
  type: feedback
---

데스크톱 Electron 앱 패키징(electron-builder `build:win`) 함정 (#804 세션 white-screen 디버깅서 실증·2026-07-14). **패키지 빌드가 design-system 파손으로 지금껏 미실행이라 dev 모드만 검증됐고, 첫 패키징서 아래가 연쇄 노출됨.**

**Why:** 패키지(file://) 런타임은 dev(vite dev server)와 로딩/샌드박스가 달라 dev-green이 packaged-red일 수 있다. `clients/desktop`·`clients/arologis-desktop` 공통.

**How to apply:**
1. **design-system `file:` prod dep → electron-builder asar 크래시**: `@samhan/design-system`(`file:../web/design-system`)이 `dependencies`면 electron-builder asar packer가 심링크를 실경로로 따라가 앱 디렉토리 밖 파일(`.storybook/*.ts`) 상대경로 계산 실패("... must be under ...") → 빌드 중단. **fix=`devDependencies`로 이동**(vite/electron-vite가 renderer 번들에 이미 인라인·electron-builder는 devDep 미패킹·node_modules 심링크는 dep/devDep 무관 존재). [[feedback_rename_filedep_junction]]
2. **preload ESM + `sandbox:true` → packaged white screen**: electron-vite가 preload를 ESM(`.mjs`·`format:'es'`)로 빌드하는데 **샌드박스 preload는 CommonJS만 허용** → packaged에서 "Cannot use import statement outside a module"로 preload 미로드 → `window.<authBridge>`(IPC contextBridge) undefined → 세션 부트스트랩/토큰 저장 실패 → **흰 화면**. 부수: 로그인 401/네비 리다이렉트가 브리지 부재로 web 분기(`window.location.replace('/login')`)→`file:///C:/login` 차단. **fix=`sandbox:false`**(ESM preload 로드·contextIsolation:true 유지) **또는 preload를 CJS(`.cjs`·package.json `type:module`이라 `.js`도 ESM)로 빌드+sandbox 유지**(더 안전·정식).
3. **winCodeSign 심링크 추출 실패(Windows)**: `winCodeSign` 캐시(darwin `.dylib` 심링크) 추출이 관리자/개발자모드 없으면 "Cannot create symbolic link : 권한 없음" → nsis/portable 서명 단계 실패. **단 win-unpacked 폴더는 그 전에 완성**(`release/<v>/win-unpacked/<App>.exe` 실행 가능) → `electron-builder --win --dir`로 서명 우회 or win-unpacked 직접 사용.
4. **app.asar 파일 잠금**: 실행 중 앱/AV 스캔이 `resources/app.asar` 잠금("being used"/"Device or resource busy") → 재빌드 실패. 앱 종료(`Get-Process "<App>"|Stop-Process`) or 새 출력 디렉토리(`-c.directories.output=release2/<v>`).
5. **검증(GUI 없이)**: `<App>.exe --remote-debugging-port=9222` 실행 → node 전역 WebSocket(node22+)로 CDP 접속 → `Runtime.evaluate typeof window.<bridge>`(=object면 preload OK)·`Log.entryAdded`/`Runtime.exceptionThrown` 에러·`location.hash` 확인.

관련: 데스크톱 white-screen 근본수정 브랜치 `fix/desktop-packaging-preload`(#804 세션·미머지·캐논 리뷰 대기). VITE_API_BASE_URL 미설정 시 renderer 기본 `http://localhost:8080`(게이트웨이).
