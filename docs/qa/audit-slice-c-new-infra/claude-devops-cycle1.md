# DevOps 리뷰 — Audit Slice C (CI/Detox 인프라) Cycle 1

리뷰어: DevOps Agent  
날짜: 2026-05-19  
대상 파일:
- `.github/workflows/qa-e2e.yml` (detox-android-arologis job 신규)
- `qa/detox/.detoxrc.js` (arologis 설정 추가)
- `qa/detox/package.json` (4 스크립트 추가)

---

## 1. 결함 목록

### [중간] D-01 — npm ci cache 누락 (detox-android-arologis job)

`detox-android` job 의 `actions/setup-node@v4` 스텝에는 `cache: 'npm'` 및 `cache-dependency-path: qa/detox/package-lock.json` 설정이 없다. 반면 `playwright` job 에는 동일 캐시 설정이 존재한다. `detox-android` job 도 캐시 없이 동작 중이므로 두 detox job 모두 캐시 미적용 상태이다. CI 실행 시마다 `npm ci` 전체 다운로드가 발생하여 불필요한 wall-clock 시간이 소비된다. `detox-android-arologis` 신규 job 도 동일 패턴을 그대로 복사했으므로 함께 수정이 필요하다.

**수정 방향**: `actions/setup-node@v4` 스텝에 `cache: 'npm'` 및 `cache-dependency-path: qa/detox/package-lock.json` 추가.

### [낮음] D-02 — artifacts path 실질 내용 없음 경고 위험

`detox-android-arologis` job 의 artifact 업로드 스텝은 `qa/detox/artifacts/` 경로를 참조한다. 그러나 현 PR 단계에서는 실 에뮬레이터 구동 없이 typecheck 와 `detox config` 검증만 수행하므로 해당 디렉토리가 생성되지 않는다. `actions/upload-artifact@v4` 는 path 가 비어 있을 경우 step 을 오류로 처리(기본값 `if-no-files-found: warn`)한다. 실질적 실패는 아니지만 CI 로그에 경고가 누적된다.

**수정 방향**: `if-no-files-found: ignore` 옵션 추가, 또는 실 에뮬레이터 활성화 PR 전까지 업로드 스텝을 주석 처리.

### [낮음] D-03 — AVD 이름 하드코딩 — 환경 간 불일치 가능성

`.detoxrc.js` 의 `arologisEmulator` 디바이스는 `avdName: 'Pixel_6_API_34'` 로 고정되어 있다. 기존 `emulator` 디바이스(`Pixel_API_33`)와 다른 AVD 이름 체계를 사용하므로, CI 머신(macos-latest GitHub Actions runner)에서 해당 AVD 가 사전 존재하지 않을 경우 실 에뮬레이터 활성화 시점에 즉시 실패한다. mobile-v4 의 `Pixel_API_33` 관례와 달리 `Pixel_6_API_34` 는 AVD Manager 에서 직접 생성한 이름이며, CI 러너 이미지 내 사전 설치 AVD 목록과 일치 여부 확인이 필요하다.

**수정 방향**: CI workflow 에 AVD 생성 스텝(`reactivecircus/android-emulator-runner@v2`) 추가를 실 에뮬레이터 활성화 PR 에서 반드시 포함.

---

## 2. 긍정 평가

- `detox-android-arologis` job 구조(체크아웃 → JDK 17 → Node 20 → npm ci → typecheck → detox config 검증 → artifacts)가 기존 `detox-android` job 과 일관된 패턴을 유지한다.
- `timeout-minutes: 60` 설정이 기존 job 과 동일하게 적용되어 wall-clock 정책을 준수한다.
- `npx detox config --configuration arologis.android.release || true` 패턴으로 실 에뮬레이터 없이 config 파싱 오류만 선제 차단하는 접근이 적절하다.
- `package.json` 의 4개 신규 스크립트(`detox-build:arologis`, `detox-test:arologis`, `detox-build:arologis-debug`, `detox-test:arologis-debug`)가 `.detoxrc.js` configuration 키(`arologis.android.release`, `arologis.android.debug`)와 정확히 대응한다.
- `e2e/arologis-mobile/smoke.test.ts` 의 testID 매핑 주석이 `PhoneLoginScreen.tsx` 컴포넌트 명세와 연결되어 있어 유지보수성이 양호하다.
- 기존 `detox-android` job 및 `playwright` job 변경 없이 신규 job 만 추가되어 기존 CI 경로에 영향 없음 확인.

---

## 3. 종합 판정

**결함 3건 (중간 1 / 낮음 2)** — 현 단계(typecheck + config 검증)에서 CI 차단 결함은 없으나, D-01 캐시 누락은 실 에뮬레이터 활성화 전 수정 권장. D-02, D-03 은 실 에뮬레이터 활성화 PR 에서 함께 해결.
