# 현재 작업 — 2026-08-10 집PC 야간 세션 (진행 중 · 자율 운행)

> 이 파일만 읽으면 이어받을 수 있습니다.
> 이전 회차: `docs/handoff/` 의 날짜 앞선 파일들.

---

## 0. 🚨 이어받는 사람이 가장 먼저 할 것

```text
1  docker ps -a --filter "name=samhan-" --format "{{.Names}}\t{{.Status}}"
   🚨 있는 것만 읽지 말고 **없는 것을 세십시오.** 이번 세션에서 그걸 놓쳐
      product-service·partner-service 가 죽은 채로 라이브QA 여러 라운드가 돌았습니다
2  git pull && .\scripts\sync-claude-memory.ps1
3  수치는 그 PC 에서 다시 세십시오 (양 PC 시드 상이)
```

### 🔴 배포본과 DB 가 어긋나면 서비스가 죽습니다 (이번 세션 실측)

```text
No enum constant ProductStatus.NOT_FOR_SALE
No enum constant ProductStatus.OUT_OF_STOCK
```

`#1133`(`6c73926c7`)이 새 품목 상태값을 DB 에 넣었는데 배포본 jar 이 그 이전이라 읽지 못했습니다.
**옛 jar 재기동은 해결이 아닙니다.** main 기준 재빌드 후 배포해야 합니다.

```bash
./gradlew :services:product-service:bootJar :services:partner-service:bootJar --no-daemon
docker compose -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.local-all.yml \
  up -d --build product-service partner-service
```

---

## 1. 개발책임자 결정 (이번 세션)

| # | 결정 |
|---|---|
| **수량동기화 방향** | 부자재가 아니라 **주품목 → 부자재를 타겟으로** 설정. 칩은 **`품목명:수량`** — 주품목 1개당 부자재가 몇 개씩 들어가는지 |
| **수량의 출처** | 🔑 **구성품·이름에서 추론하지 않는다. 오로지 수량동기화 설정값이 정한다.** *"40HP는 2개로 설정했으면 그대로 나올 뿐"* |
| **D11 범위** | 84건 전체 — 14건도 세트로 등록하고 진행 |
| **정액DC 분류 우선순위** | **가장 하위 분류가 이긴다 (S > M > L)** · 품목 개별 override 최우선 |
| **라이브QA 구조** | **트랙별 순차 배포·검증** (로컬 스택이 하나라 4트랙 백엔드 동시 불가) |
| **전수조사 주체** | **클로드(PM)**. 코덱스는 구현·적대검증·fix 만 |
| **`#1162`** | 집PC 에서 진행 안 함. env 파일은 전달 완료 |
| **`#1163`** | 신규 트랙 개설 승인 |
| 🆕 **결정 형식** | **결정이 필요하면 항상 선택지를 제시** → 메모리 `feedback_always_present_options_for_decisions` |

---

## 2. 트랙 상태판

| 트랙 | PR | HEAD | 게이트 | 다음 한 수 |
|---|---|---|---|---|
| 수량동기화 칩 | `#1126` | `bd95907c8` | ① 프런트축 0 · 백엔드 E2E 유보 | **배포 후 백엔드 축 재확인** |
| 판매전표 헤더·품목 | `#1131` | `bedb9dac6` | ① SOL 재검증 중 | SOL 결과 회수 |
| 버전이력 모달 | `#1134` | `3e7fcb254` | ① SOL 재검증 중 · DC 축 배포 대기 | SOL 결과 + DC 배포 |
| 세트전개·정액DC | `#1132` | `f829946fc` | ① SOL 첫 검증 중 | SOL 결과 회수 |
| UUID 잔여노출 | `#1164`(`#1163`) | `0aac907c8` | ① SOL 검증 중 | SOL 결과 회수 |
| 자격 노출 | `#1162` | `ae2609670` | ⏸️ 회사PC 로 이월 | 아래 §5 |

### 이번 세션에 닫은 도달 결함 (전부 라이브QA 발견)

```text
#1126  시트동기화가 규칙 계약을 우회 (상태축 :1347 + 역할축 :1403)      R33
       409 로 차단당한 사용자가 규칙에 도달할 수 없음                    MED
       부자재에서 본체 미표시 · 서버가 인정한 target 을 picker 가 숨김   R35
#1131  WIP 이 신규 품목 입력을 저장 payload 에서 누락 (진단 후 revert)
       고를 수 있는 세트 품목이 저장은 거부되고 기존 행 수정까지 소실
#1134  DC 버전이력 404 · 실 memo 3행이 '변경 이력 없음' 으로 은폐        R11
       이력 변경자에 UUID 노출 (저장·응답·렌더 3층)                     R12
#1163  창고 이력 화면의 UUID 8자 폴백 · inventory callerId 저장
```

---

## 3. 🚩 아침에 올릴 것 (선택지로 정리해 둘 것)

```text
· GAS 전수조사에서 나오는 "기본값을 자동으로 정할 수 없는 케이스"
· #1132 의 금액 영향 세트 72개 — 견적 0건 / 전표 0건이라 지금이 가장 싼 시점
· default_qty 가 이름과 불일치하는 2부 구성 세트 15건
  🔑 "수량은 설정값이 정한다" 결정으로 **받침 수량과는 무관해졌음**.
     세트 전개 금액에 닿는지만 #1132 SOL 결과로 판단
```

---

## 4. GAS 전수조사 (진행 중)

```text
분모 고정   docs/dev-reports/2026-08-10-gas-function-inventory.md   12파일 889 함수
회수본      2026-08-10-gas-sweep-A-estimate-1-10000.md
            1~10000행 358개 전수 분류 (업무규칙 167 / UI 121 / 인프라 31 / 데드 39)
            🚩 골격이지 확정본이 아님. 분류 자체가 재검증 대상
워크플로우  w6ioqixag — Extract 11 파티션(SONNET5) → Refute 11(OPUS) →
            SheetAxis 2 → Synthesize → Critic
```

🚩 **파티션 하나가 실패했습니다.** 원인은 입력이 아니라 **출력 폭주**입니다(`Prompt is too long` 0건 · 출력 토큰 최대 39,667). 한 에이전트에게 3,300줄 + 함수 107개의 상세표를 한 턴에 쓰게 한 설계가 과했습니다.

```text
고칠 방향  index.ejs 6 파티션 → 12 (1,650줄) · 보고서를 파일에 나눠 append ·
           반환값에는 집계만
재개       Workflow({scriptPath: <저장된 스크립트>, resumeFromRunId: "wf_24f729f1-e3d"})
           완료 에이전트는 캐시로 즉시 반환 — 실패분만 다시 돕니다
```

---

## 5. `#1162` — 회사PC 로 이월

```text
✅ jar BOOT-INF 평문 0건 (378파일 스캔) · 소스 잔존 28건은 전부 docs/·.claude/memory/
🔴 미해결 — 컨테이너 제거 + postgres_data 볼륨 유지 시 부트스트랩이 새 랜덤 자격을
   만들어 전 서비스 DB 인증 실패 (격리 실험으로 실증)
   좌표 scripts/ensure-local-env.sh:86 · infrastructure/scripts/ensure-local-env.ps1:51
   LUNA 가 D7(회전 금지)과 충돌한다며 중단·보고 → D7 해석 판단 필요
🔑 개발책임자께 전달한 infrastructure/.env 를 회사PC 에 넣으면 이 상황 자체가 안 생김
```

---

## 6. 이 세션에서 굳힌 절차

### 🚨 슬롯이 비면 **즉시** 채운다

커밋·PR 게시를 하느라 슬롯을 비워 뒀다가 지적받았습니다.
**순서 = SOL 결과 회수 → 다음 라운드 발주 → 그다음 커밋·게시.**

### 🚨 구현자가 수단 승인을 요구하면 — 원인 확정 여부로 가른다

```text
원인 미확정  승인하지 않고 조건만 달아 되돌린다 (#1126 MED 사례)
원인 확정    PM 이 설계를 낸다. 미루지 않는다 (#1131 S2 사례)
             단, 설계를 냈으면 **그 설계가 만드는 새 표면을 미리 지목**해
             같은 라운드에서 닫게 한다 (5가지 지목 → 전부 GREEN)
```

### 🚨 `docker ps` 는 **없는 것을 세는 도구**다

있는 것만 읽어서 죽은 서비스 2개를 5시간 동안 못 봤습니다.

### 🚨 새 워크트리는 의존성이 없다

`#1163` 프런트 RED-B 3건이 `Cannot find package 'vitest'` 로 미실행 → **판정 불가**였습니다.
PM 이 `npm install` 후 직접 돌려 5/5 GREEN + 뮤테이션 2건 FAIL 을 확인해 닫았습니다.
🚩 `npm install` 이 `package-lock.json` 을 건드리므로 커밋 전 되돌릴 것.
