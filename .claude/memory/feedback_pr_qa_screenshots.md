---
name: PR 첨부 — QA 결과 스크린샷 필수
description: 모든 PR 본문에 QA 결과 스크린샷(1장 이상)을 인라인으로 첨부. 백엔드 PR은 test 리포트, UI PR은 화면 캡처
type: feedback
originSessionId: 78cac99d-5dee-47ca-8254-3834a088f393
---
**규칙**: SamhanLogis 의 모든 PR 본문에는 **QA 결과 스크린샷(최소 1장)** 을 인라인으로 첨부한다. 텍스트 코드블럭만으로는 불충분.

**Why**: 개발책임자가 명시 — "PR 시 QA 결과를 스크린샷(1장 이상)과 함께 첨부해주면 좋겠어." 텍스트는 위·변조 가능성이 있고 한 눈에 결과가 안 들어옴. 스크린샷은 빌드/테스트가 실제로 실행된 시각 증거이며, 추후 회고 시에도 유용.

**적용 범위**:
- 모든 신규 PR (cleanup, feature, hotfix 무관)
- 백엔드 변경: `./gradlew test` 결과 (BUILD SUCCESSFUL 화면 또는 HTML 테스트 리포트 캡처)
- UI 변경: 변경된 화면 캡처 (before/after 권장)
- 인프라 변경: 동작 결과 화면 (e.g. `docker compose ps`, GitHub Actions 워크플로우 결과 등)
- DB 마이그레이션: 적용 후 스키마 또는 데이터 캡처

**저장 위치**: `docs/qa/<slice-slug>/<n>-<설명>.png` 형태로 슬라이스의 feature 브랜치에 함께 커밋. 예:
- `docs/qa/post-phase2-cleanup/1-test-results.png`
- `docs/qa/user-service-first-slice/1-org-chart.png`
- `docs/qa/user-service-first-slice/2-employee-create.png`

**PR 본문 참조 형식** (반드시 commit-pinned raw URL 사용):
```markdown
## QA 결과 스크린샷
![테스트 통과](https://raw.githubusercontent.com/ewoo14/SamhanLogis/<commit-sha>/docs/qa/<slug>/1-test-results.png)
```

**중요**: 상대경로 `docs/qa/...` 형태는 **PR 본문에서 깨짐**. PR description 은 base branch (main) 컨텍스트로 렌더링되는데 신규 파일은 main에 아직 없기 때문. 반드시 절대 URL 을 써야 한다. 권장 패턴:
- `https://raw.githubusercontent.com/<owner>/<repo>/<commit-sha>/<path>` (영구, 머지 후에도 동작)
- 또는 `https://github.com/<owner>/<repo>/raw/<commit-sha>/<path>` (위 URL 로 redirect)
- 브랜치 이름 사용은 비권장 — 머지 후 브랜치 삭제 시 깨짐

**저장소 visibility 전제** (2026-05-04 PR #18 회고 보강):
- **본 패턴은 PUBLIC repo 가정**. PRIVATE repo 의 raw URL 은 익명 접근 시 HTTP 404 반환 → PR body markdown 의 image 가 안 보임 (GitHub camo proxy 도 raw URL 인증 못 함)
- `ewoo14/SamhanLogis` 는 PUBLIC. visibility 변경 (private 전환) 시 본 메모리의 commit-pinned raw URL 패턴 무효화
- visibility 확인: `gh repo view <owner>/<repo> --json visibility -q '.visibility'`
- 만약 PRIVATE 라면 대안:
  1. **GitHub web UI drag&drop** — PR 편집에서 PNG 끌어다 놓기 → user-attachments.githubusercontent.com URL 자동 생성 (public CDN, 유일하게 작동하는 옵션)
  2. **저장소 public 변경** (회사 정책 검토 필요)
  3. 외부 image hosting (외부 노출 위험)
- visibility 전환 직후엔 **GitHub raw CDN cache propagation 1~5분 delay** 가능. 일부 PNG 가 200 / 일부 404 패턴이면 cache 일관성 문제로 약간 기다린 후 재시도

**과거 위반 사례**:
- **PR #5** (2026-05-04): 첫 본문 상대경로 → "첨부 안 됐다" → commit-pinned URL 정정
- **PR #2/#3** (2026-05-04, User Service): 텍스트만, 스크린샷 0
- **PR #18** (2026-05-04, Electron skeleton): 1차 발행 시 "FE 단독성 슬라이스라 자동 캡처 산출물 없음" 명목 placeholder 회피 → 개발책임자 지적 → hotfix `784cfad7` 로 mock 모드 + Vite + Edge headless 캡처 5장 첨부. **이때 저장소가 private 상태라 raw URL 안 보임 → 사용자가 public 으로 변경 후 정상화** (본 메모리 visibility 섹션 보강 계기)

**스크린샷 생성 방법** (Claude 가 자동화):
- 백엔드 테스트 리포트: `./gradlew test` 실행 → `services/<svc>/build/reports/tests/test/index.html` 생성됨
- Edge 헤드리스로 렌더링 + 스크린샷:
  ```powershell
  & "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe" --headless --disable-gpu --screenshot="<출력경로>" --window-size=1280,1024 "file:///<HTML경로>"
  ```
- 또는 Chrome 가 설치돼 있으면 동일 옵션의 chrome.exe 사용 가능
- 둘 다 없으면 단순 fallback: 콘솔 텍스트 + 직접 캡처 요청

**예외**: 문서 전용 PR (코드/테스트 변경 없음, e.g. README 오타 수정) 은 스크린샷 생략 가능.

## 강화 — backend skeleton PR 표준 캡처 (2026-05-06 PR #91 회고)

**규칙**: backend skeleton / API 신규 PR 에서 QA 캡처 누락 사례 발생 (PR #91). 향후 backend PR 의 QA 캡처는 다음 2종 이상 의무:

1. **단위 + IT 결과 HTML 리포트** — `services/<svc>/build/reports/tests/test/index.html` Edge headless 캡처
2. **Swagger UI 캡처** — bootRun 또는 OpenAPI yaml 정적 분석으로 endpoint 목록 캡처
3. (선택) **Postman / curl 호출 결과** — 정상 200 + 인증 실패 401/403 응답 캡처

저장 위치: `docs/qa/<phase-N-step-M>-<svc-name>/<n>-<설명>.png`

PR #91 사례:
- `docs/qa/phase9-step-1-partner-service/1-test-report.png` (PartnerServiceTest 8/8 PASS)
- `docs/qa/phase9-step-1-partner-service/2-swagger-internal.png` (`/internal/partners/{partnerCode}` endpoint)
- `docs/qa/phase9-step-1-partner-service/3-swagger-admin.png` (`/admin/partners` CRUD endpoint)

## spawn prompt 의무 (TM / reviewer / 종합 TM)

향후 모든 spawn prompt 에 다음 의무 항목 명시:

```
## QA 캡처 의무 (본 PR 에 포함)
- backend: gradle test HTML 리포트 + Swagger UI 캡처 2종 이상
- frontend: 변경 화면 캡처 (before/after, viewport 매트릭스)
- 저장: docs/qa/<slug>/*.png + commit-pinned raw URL 로 PR 본문 인라인
- 가드: feedback_pr_qa_screenshots.md 일관
```

## 회고 사례

- **PR #91** (2026-05-06 — Phase 9 1차 partner-service skeleton): 5 reviewer 토론 + 종합 TM 모두 QA 캡처 첨부 누락 → 사용자 지적 → 후속 commit 으로 backend QA 캡처 3종 보강. 본 회고 후 spawn prompt 강화.

- **PR #92** (2026-05-07 — Phase 9 2차 groupware-service skeleton): QA 캡처 3종은 첨부됐으나 raw URL 이 commit `380eb66` 으로 pin 됨. 종합 TM 이 임시 브랜치 (`tm-pr92-work → push origin HEAD:feature/...`) 로 추가 commit 적용 → force-push 효과 → `380eb66` dangling commit (`gh api repos/.../commits/380eb66` HTTP 422 "No commit found") → raw URL 모두 404 → 사용자 PR 본문에서 이미지 안 보임 지적. 새 HEAD commit SHA 로 raw URL 일괄 재 pin 후 200 회복.

## 강화 — raw URL pin 시점 + reachable 검증 (2026-05-07 PR #92 회고)

**규칙 1**: raw URL pin 은 **PR 발행 직전 또는 추가 commit push 후 최종 HEAD commit SHA** 로 통일.

- 중간 commit SHA pin 금지 (force-push / rebase / dangling commit 시 404)
- 종합 TM / fix TM 이 추가 commit push 한 직후 **PR 본문의 raw URL 도 동시 re-pin 의무**
- HEAD SHA 결정 시점:
  ```pwsh
  $headSha = git rev-parse HEAD  # local push 직후
  # 또는
  $headSha = gh pr view <PR> --repo <owner>/<repo> --json headRefOid --jq .headRefOid
  ```

**규칙 2**: PR 본문 갱신 직후 **raw URL HEAD 200 검증 의무** (CDN propagation 1~5분 delay 대응).

```pwsh
@(
  "1-test-report-summary.png",
  "2-test-approval-line-class.png",
  "3-api-endpoints-summary.png"
) | ForEach-Object {
  $url = "https://raw.githubusercontent.com/<owner>/<repo>/$headSha/docs/qa/<slug>/$_"
  try {
    $r = Invoke-WebRequest -Method Head -Uri $url -UseBasicParsing -ErrorAction Stop
    "$($r.StatusCode) $($r.Headers.'Content-Length')B $_"
  } catch {
    "$($_.Exception.Response.StatusCode.value__) FAIL $_"
  }
}
```

3장 모두 200 + Content-Length > 10KB 확인. 404 발견 시 commit reachable 검증 (`gh api repos/<owner>/<repo>/commits/<sha>`) → unreachable 시 새 HEAD SHA 로 re-pin.

**규칙 3**: 임시 브랜치 push 패턴 회피.

- `git push origin HEAD:feature/...` 는 force-push 효과로 중간 commits 가 dangling 가능
- 정공법: PR 브랜치 worktree 에서 직접 작업 (다른 worktree 가 lock 중이면 lock 해제 또는 같은 worktree 재사용)
- 불가피하게 임시 브랜치 사용 시 **fast-forward 만 허용** (`git push --force-with-lease` 도 금지 — 중간 SHA pin 무효화)

**규칙 4**: spawn prompt 에 의무 추가.

```
## raw URL pin + reachable 검증 (의무)
- raw URL 은 push 한 최종 HEAD commit SHA 로 pin (`git rev-parse HEAD`)
- 중간 commit SHA pin 금지 (force-push 시 dangling)
- PR 본문 갱신 직후 raw URL HEAD 200 + Content-Length > 10KB 검증
- 404 발견 시 commit reachable 검증 후 새 HEAD SHA 로 re-pin
- 임시 브랜치 push 패턴 회피 (force-push 효과 위험)
```

**과거 위반 사례**: 2026-05-04 PR #2, #3 (User Service 슬라이스) 본문에 텍스트만 포함, 스크린샷 없음. 머지 후 추가는 불가하므로 지나간 사례. 후속 정리 슬라이스부터 적용.

## 강화 — 작동 화면 캡처 절대 의무 (2026-05-10 PR #115/#117/#118 회고)

**규칙**: QA 단계는 **시나리오 markdown + 단위 테스트 case + 실 작동 화면 캡처 3종** 의무. 시나리오만 / 테스트만으로 불충분.

**Why**: 사용자 명시 — "QA 는 앞으로 작동하는 화면 캡처도 같이 요청". PR #115/#117/#118 에서 QA agent 가 시나리오 markdown (160 case) + 단위 테스트 (49+56+20 case) 만 작성하고 실 페이지 띄워 작동 화면 캡처 0 → 사용자 지적.

**적용 범위**:
- 모든 신규 page (FE 신규 route 시 의무)
- BE 신규 endpoint (Swagger UI 캡처)
- 실 데이터 입력 후 동작 결과 캡처 (form 입력 → 저장 → 결과 표시)

**캡처 방법**:
- 기존 `tools/manual-capture/` 의 Playwright 스크립트 활용 (capture-desktop.js + capture.config.json 보강)
- 또는 별도 `npx playwright codegen` + screenshot
- Electron renderer 단독 (`cross-env VITE_MOCK_MODE=1 npx vite`) 으로 BE 없이도 캡처 가능

**저장 위치**: `docs/qa/<slice-slug>/working-<page-name>.png`
예:
- `docs/qa/phase-10-step-9-sheet-notion-import/working-regions-list.png`
- `docs/qa/phase-10-step-10-gas-b-ecount-auto/working-dispatch-sms-preview.png`

**spawn prompt QA 의무 강화**:
```
## QA 작동 화면 캡처 (절대 의무)
- 시나리오 markdown ✓
- 단위 테스트 ✓
- **실 페이지 작동 화면 캡처 (Playwright/Electron headless)**
  - 신규 page 1장 이상
  - 실 데이터 입력 → 결과 표시 → 캡처
  - 저장: docs/qa/<slug>/working-<page>.png
- PR 본문 raw URL 인라인 첨부
- 누락 시 PR 발행 보류
```

## 강화 — 커밋만 하고 본문 인라인 누락 반복 (2026-06-11 PR #462/#463 회고)

**증상**: QA PNG 를 `docs/qa/<slug>/` 에 **커밋은 했으나 PR 본문에 인라인 안 함** → 개발책임자 "또 스크린샷 업로드 안함" 반복 지적. 규칙 부재가 아니라 **발행 절차 누락**. PNG 커밋 ≠ 인라인. `gh pr view <PR> --json body | grep '!\['` 로 **본문에 실제 image markdown 이 있는지 자가 검증** 의무.

**체크리스트 (PR 발행/갱신 시 매번)**:
1. QA PNG 커밋 (`docs/qa/<slug>/*.png`)
2. **본문에 raw URL image markdown 인라인** — `![설명](https://raw.githubusercontent.com/<owner>/<repo>/<full-sha>/docs/qa/<slug>/<file>.png)`
3. raw URL HEAD 200 검증 (`curl -s -o /dev/null -w "%{http_code}"`)
4. `gh pr view <PR> --json body -q .body | grep -c '!\['` ≥ 1 자가 확인

**운영 함정 — `gh pr edit` 스코프 실패 (신규)**: `gh pr edit <PR> --body-file f.md` 가 토큰 read:org 스코프 부재로 GraphQL 에러(`'login'/'name'/'slug' field requires read:org`) → **본문 미갱신**. 우회: REST API PATCH (repo 스코프로 충분) —
```bash
gh api repos/<owner>/<repo>/pulls/<PR> -X PATCH -F body=@body.md
```
`-F body=@file` 로 파일 내용을 본문 문자열로 주입. 한글 본문은 Write 로 UTF-8 파일 생성 후 `@` 참조([[powershell-utf8-writes]]).

## 🚨 강화 — 브랜치 URL 머지 후 404 + 매 리뷰-라운드 코멘트 의무 (2026-06-13 PR #474 회고, 4번째 재발)

**증상**: §7 슬라이스 0(PR #474) 리뷰 라운드 코멘트들이 이미지를 **feature 브랜치 URL**(`.../blob/feat/global-collab-slip-reference/...?raw=true`)로 인라인 → **review 당시엔 렌더됐으나 머지 시 브랜치 삭제로 전부 HTTP 404(깨진 이미지)**. PM 종합은 경로만 텍스트 언급(인라인 0). 개발책임자 "인라인 스크린샷 왜 자꾸 첨부 안 해" 4번째 지적. → 머지 커밋 SHA 기준 9컷 재첨부로 정정.

**못 박는 규칙 (이전 규칙들의 재확인 + 강화)**:
1. **스크린샷 URL = 불변 full commit SHA 必**. 브랜치 이름 URL 절대 금지 — review 시 렌더돼도 **머지 시 브랜치 삭제 → 404**. (`https://github.com/<o>/<r>/blob/<full-sha>/docs/qa/<slug>/<f>.png?raw=true` 또는 `raw.githubusercontent.com/<o>/<r>/<full-sha>/...`)
2. **인라인은 PR 본문뿐 아니라 매 리뷰-라운드 코멘트마다**. 각 라운드 = 실서버 스크린샷 인라인 임베드([[temp-multimodel-workflow]]). 경로 텍스트 언급 ≠ 인라인.
3. **게시 직후 자가검증 의무**: 임베드한 모든 URL `curl -s -o /dev/null -w "%{http_code}"` = 200 확인. 하나라도 404면 SHA/경로 정정 후 재게시.
4. PM 종합(머지 직전) 코멘트도 동일 — 핵심 캡처 인라인 임베드.

> 규칙은 2026-05-04부터 5회 박제돼 있었음 — **부재가 아니라 미준수**. 매 스크린샷 게시 시 본 4항 mental check.

**🔁 5번째 재지적 (2026-06-16, 에픽 #18 슬1 PR #494 착수 시점)**: 개발책임자 "스크린샷은 로컬에만 저장하면 확인 불가 — PR 리뷰로 게시 요청". 즉 `docs/qa/<slug>/*.png` 로컬 커밋만으로는 **확인 불가** → 반드시 **PR 코멘트/리뷰에 full-SHA raw URL 로 인라인 임베드**해야 PR 화면에서 보인다. 매 리뷰 라운드(Opus/Codex) 코멘트에 그 라운드 Docker 실QA 스크린샷을 인라인 + curl 200 자가검증. (착수 시 mental check 고정.)

## 관련 가드

- 통합 PR 패턴 의무 (`feedback_integrated_pr_pattern.md`) 와 함께 적용 — 단편 fix PR 금지, 통합 PR 1개에 전수 QA 캡처 첨부 (PR #66 회고)
- 다모델 워크플로우 ([[temp-multimodel-workflow]]) — 각 리뷰 라운드 PR 게시 + 실서버 스크린샷 인라인
