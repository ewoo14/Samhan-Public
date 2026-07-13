# 로컬 Docker 스택 + 데스크톱 실 QA 함정 (재사용)

> 2026-05-30, PR #320(거래처 RESTORE) Docker 실 QA 중 확인. 데스크톱 FE 를 실 백엔드 대상으로
> 헤드리스 브라우저 QA 할 때 반복 적용.

## 1. 로컬 이미지가 stale 일 수 있음 (가장 큰 함정)
- `scripts/launch-local-stack.ps1` 은 bootJar 는 빌드하지만 **docker 이미지는 캐시 재사용** →
  컨테이너가 일주일 전(예: 2026-05-22) jar 로 돌 수 있다. 새 컨트롤러/엔드포인트가 "No static
  resource ..." (404→500 wrap) 로 안 잡히면 **이미지 stale** 의심.
- 단일 서비스 재빌드+재기동(프로젝트명 `infrastructure`, working_dir `infrastructure/`):
  `docker compose -p infrastructure --project-directory <repo>/infrastructure -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.local-all.yml build <svc>`
  후 `... up -d --no-deps --force-recreate <svc>`. 이미지는 `*spring-build`(`infrastructure/docker/spring-service.Dockerfile`)가 `JAR_FILE` 을 COPY 만 함(소스 재컴파일 X, 빠름).
- 이미지 생성시각 확인: `docker inspect infrastructure-<svc>:latest --format '{{.Created}}'`.

## 1.5 cross-service referent 체인 stale (2026-07-13 #773 S5 실증) — 증상≠근원
- **회계 일마감 재검증(#773)은 accounting→product-service 벌크 endpoint 의존**: `getDailyDetail` 이 매칭 productId 에 대해 `POST /products/internal/price-history/applicable-bulk`(S1a)+`fixed-discount-rate-bulk`(S1c) 호출. **product-service 이미지가 stale 하면** 이 벌크가 **구(舊) 단건-404 동작**을 해 accounting `postBulkReferent` 가 4xx→`INVALID_INPUT(400)` 전파 → **daily-detail 전체 400**(`"product-service 조회 요청 오류: 404 NOT_FOUND"`). 증상은 accounting 인데 근원은 product-service stale.
- **오진 주의**: 현 main 소스는 `applicableBulk`=`findApplicableIfPresent`(부분성공·200 빈 Map). 정가 결측(dev `price_history`=0)이어도 정상은 200→per-line MISSING_REFERENT degrade. 400 이 나면 코드 버그로 오해 말고 **product-service 이미지 날짜부터 확인**(`docker exec ... ls -la /app/app.jar`).
- **`--tests`/토큰 진단**: 라우트 존재 판별 시 `InternalTokenGuard` 가 **미인증 요청을 라우트 매칭 前 401** → 토큰없는 probe 의 401 은 "라우트 존재" 증거 아님. 유효 `X-Internal-Token: dev-internal-token-change-me`(dev 기본) 로 직접 호출해 200 vs 404 판정.
- **결론**: **#773 회계 재검증 라이브 QA = accounting + product 양측 재배포 필수**(accounting 단독 재배포는 부족). [[feedback_qa_docker_real_test]] 보강.

## 2. 게이트웨이 라우팅 격차 (FE→gateway 가 막히는 경우)
- `/api/v1/partners/**` 라우트는 **StripPrefix=2** 인데 4tab/revision 컨트롤러는 풀패스
  `@RequestMapping("/api/v1/partners…")` → strip 후 `/partners/…` 로 404. 풀패스 컨트롤러는
  blocks/orders 처럼 **no-strip 라우트**가 따로 있어야 동작.
- `/auth/**`(auth-service-legacy) 라우트엔 **JwtAuthentication 필터 미적용** → 게이트웨이가
  X-User-Id/Role 미주입 → auth-service `HeaderAuthenticationFilter` 가 인증 실패 → `isAuthenticated()`
  endpoint(예: `/auth/admin/permissions/my`) 403(빈 body). auth-service 는 Bearer 자체검증 안 함(헤더 신뢰).
- 게이트웨이 `JwtAuthenticationGatewayFilterFactory` 는 **X-User-Id / X-User-Role / X-User-Department
  만 주입(X-User-Name 미주입)**. → header 인증 service 의 `principal.getName()` = **X-User-Id(UUID)**.
  컨트롤러가 이를 표시명으로 쓰면 UUID 가 화면에 샌다(PR #320 F4). 표시명 필요 시 UUID 가드 필수.

## 3. 헤드리스 브라우저 실 QA 브리지 기법 (Playwright)
- web 모드엔 electron preload 없음 → `addInitScript` 로 `window.samhanAuth`(+`samhanLegacy`) IPC
  shim 주입(토큰 localStorage 미러). 앱 라우팅은 **HashRouter**(`#/...`).
- 게이트웨이 격차 우회: `context.route('**')` 로 대상 서비스 **직접 포트(:8095 등)** 프록시(node http,
  `X-User-Id/Role/Name` 주입 = 게이트웨이 필터 대행) + 기능 무관 endpoint(권한매트릭스/검색)는 stub.
  로그인/정책은 실 게이트웨이 passthrough. → 기능 자체는 실 서버 적중, 화면은 실 UI.
- **DS `Modal` 은 `data-testid` 를 전달하지 않음** → 다이얼로그 대기는 `[role=dialog]` 또는 내부 실
  testid 버튼(예: `partner-detail-edit-btn`) 사용. DS `Input` 은 `type=text` 속성 없을 수 있음 →
  `input:not([disabled])`. `DataTable` 행 onClick 은 `<tr>` (셀 텍스트 클릭 버블 OK).
- 캡처 스크립트 선례: `clients/desktop/playwright/partner-restore-qa/capture.mjs`.

## 3.5 폴더 rename → compose project label 함정 (2026-06-08 PR #432 후속)
- 루트 폴더 rename(SamhanLogis→Samhan-Public) 후, **세션 이전 생성 컨테이너 중 일부 project label=`<none>`**(또는 구 프로젝트). 현재 가동 컨테이너 project = **`infrastructure`**(compose 파일 dir 기준). `docker inspect <c> --format '{{ index .Config.Labels "com.docker.compose.project" }}'` 로 확인.
- label 불일치 컨테이너는 compose 가 자기 소유로 못 알아봐 **container_name 충돌**("already in use"). 해법: 충돌 orphan(Exited) `docker rm <c>` 후 `docker compose -p infrastructure ... up` 재생성.
- **`up -d <svc>` 는 `depends_on` 으로 postgres/eureka 까지 Recreate 시도** → 단일/부분 재기동 시 반드시 **`--no-deps`** 추가(미사용 시 postgres 가 Created(stopped) 로 떨어져 스택 다운; `docker start samhan-postgres` 로 복구, 볼륨 데이터는 무손실).
- 전체 재기동: orphan rm → 16 jar build → `docker compose -p infrastructure -f docker-compose.yml -f docker-compose.local-all.yml up -d --build`. `samhan-nginx` 는 로컬 dev 에서 상시 unhealthy(`/healthz` 80 미기동, 443 ssl 전제 — 클라이언트는 :8080/:8097 직결이라 무영향).

## 3.6 호스트 포트 점유 충돌 — slip-service 8086 ↔ influxd (2026-06-15 Phase2 0제거 QA)
- **dev PC 의 `influxd`(InfluxDB) 가 8086 LISTENING** (InfluxDB 기본 포트 = slip-service 기본 포트와 동일). `docker compose up slip-service` 시 host 바인딩 `127.0.0.1:8086:8086` 충돌 → 컨테이너가 **`Created` 에서 멈춤**(`bind: An attempt was made to access a socket ... forbidden`). Flyway 가 안 돌아 마이그 미적용(겉보기 "재기동 완료"인데 DB 미변경 — false success 주의).
- 점유 프로세스 확인: `netstat -ano | grep ":8086"` → PID → `Get-Process -Id <pid>`(influxd).
- **해법**: `docker-compose.local-all.yml` 의 slip-service `ports` 를 **host 쪽만** 임시 remap(`127.0.0.1:8186:8086`) 후 재기동 → Flyway 정상. **게이트웨이는 내부망 `http://slip-service:8086` 사용**(host 포트 무관)이라 QA 영향 0. 끝나면 compose 편집 `git checkout` 으로 되돌림(running 컨테이너는 영향 없음). influxd 를 죽이지 말 것(사용자 무관 서비스).

## 4. react-query 캐시 stale
- 편집 mutation 이 연관 list 쿼리(`['partnerRevisions', code]` 등)를 invalidate 안 하면 탭 전환만으로는
  최신 안 보임. 같은 SPA 세션 재오픈으로도 안 되면 문서 리로드 필요. → 근본 fix 는 onSuccess invalidate.

## 5. crypto.subtle 은 LAN HTTP(비 secure-context)에서 비활성 (2026-06-21 C2 mobile-public)
- 공개 모바일 web 앱(`clients/web/mobile-public` 등)이 `window.crypto.subtle.digest`(SHA-256)에 의존하면,
  **dev 실폰 접속 origin `http://<PC-LAN-IP>:5185`(비 secure-context)에서 subtle=undefined** → 제출 전 throw.
  (Chrome 은 `http://localhost` 만 secure 취급, LAN IP HTTP 는 insecure.) prod HTTPS·localhost 는 정상이라
  **localhost 라이브 QA 로는 안 잡힘**(C2 Codex R2 적발, Opus R3 가 폴백 정확성 검증).
- 해법: `globalThis.crypto?.subtle` 가용 시 사용, 없으면 **순수 JS SHA-256 폴백**(외부의존 0, BE `MessageDigest`
  와 byte-for-byte 일치). ⚠️ 단위 'abc'(단일블록) 벡터만으로 불충분 — 멀티블록(>55byte)·대용량은 Node crypto 대조
  필수. slip 인수자 공개 서명 등 향후 공개 web 서명 동일 적용.
- **desktop renderer 는 Electron IPC(`window.samhanAuth`) 라 순수 브라우저 부팅 불가** → 실 캡처 시 §3 shim
  필수. mobile-public 같은 순수 web 앱은 shim 없이 실 브라우저 캡처 가능(C2 desktop 모달은 캡처 불가, API+mock 갈음).
