# 현재 작업 — 2026-08-10 회사PC 세션 마감 → 집PC 인계

> 새 PC 첫 세션은 **이 파일만** 읽으면 컨텍스트가 회복됩니다.
> 이전 회차: `docs/handoff/` 의 날짜 앞선 파일들.

---

## 0. 🚨 집PC 에서 가장 먼저 할 것

```text
1  git pull                     (main 에 정찰·메모리 정정 다수 들어옴)
2  .\scripts\sync-claude-memory.ps1
3  🚨 아래 "회수 못 한 라운드" 3건의 산출물을 먼저 확인
   회사PC 에서 codex 가 돌던 중 세션이 끝났습니다. 워크트리에 파일만 남아 있을 수 있습니다
   확인법: 각 워크트리에서 `git status --porcelain` → 변경이 있으면 그 라운드는 완주한 것
4  🚨 시드 데이터가 양 PC 다릅니다 — 문서의 수치를 그대로 믿지 말고 그 PC 에서 다시 세십시오
```

## 🚨 회수 못 한 라운드 3건 (세션 종료 시각 기준 실행 중이었음)

| 트랙 | 워크트리 | 브랜치 | 무엇을 시켰나 |
|---|---|---|---|
| `#1126` R32 | `D:\dev\Samhan-Public` (메인) | `feat/896-qty-sync-chip-track` | SOL 검증 — **R31 차단이 정상 경로를 막는가** |
| `#1134` R8 | `.claude/worktrees/w1134` | `feat/1091-version-history-remaining` | SOL 검증 — **7상태 표시가 틀린 조합** |
| `#1162` 범위확대 | `.claude/worktrees/w1162` | `fix/it-ephemeral-credentials` | LUNA 구현 — **배포물 자격 제거** |

> 🚩 `#1126` 은 **워크트리가 아니라 메인 디렉터리**가 트랙입니다. 헷갈리기 쉽습니다.
> 🚩 codex 산출물은 커밋 없이 파일만 남습니다. `git diff --name-only` 로 옮기면
>    **신규 파일이 통째로 누락**됩니다 — `git status --porcelain` 을 쓰십시오.

---

## 1. 트랙 상태판

| 트랙 | PR | 상태 | 다음 한 수 |
|---|---|---|---|
| **수량동기화 칩** | `#1126` | R31 커밋 `17becb74a` · CI 52/52 (`1294cdb5a` 기준) | R32 SOL 회수 → 결함 0 이면 라이브QA |
| **버전이력 모달** | `#1134` | R7 커밋 `d55a927a7` · CI 51/51 (`0db123837` 기준) | R8 SOL 회수 |
| **자격 노출** | `#1162` | 범위 확대 발주 · CI 40/40 (`db692e813` 기준) | 구현 회수 → SOL 재검증 |
| **중앙 감사로그** | 이슈 `#1161` | S0 실측 완료 · 보존/실패 **권장안 확정 게시** | **S1.5** (보존 정책 + 실패 경로) 착수 |
| **옵션 명칭 통일** | (`#896` 내 슬라이스) | 정찰 완료 `ae238d828` | 🚨 **설계 재수립** — 아래 참조 |
| 판매전표 헤더 | `#1131` | 착수 전 | 🚨 회사PC 활성 SALES 전표 **0건** · WIP `8fe285a72` 신뢰 금지 |
| 세트전개·정액분류 | `#1132` | 착수 전 | `#1089` 기본구성품만 + `#1090` 정액을 품목분류로 |
| 재고 인스턴스 | `#1128` | 착수 전 | 집PC 는 `GET /inventory/balances` 404 였음 — 재측정 |
| 내부 채팅 | `#1125` | 착수 전 | 🚫 별도 채팅 도메인 신설 금지 · 기존 `messages` 확장 |
| 양식 디자이너 | `#1158` | 착수 전 | 정찰 미실시 |
| 품목 식별자 | `#1051`·`#1086` | 트랙 재개설 필요 | 🚨 회사PC 표본 0개 (집PC 33개) — **집PC 가 유리** |

### 미착수 · 이슈 없음 (정찰만 끝남)

```text
주문서 계보 3필드   set_head · parent_set_model · bundle_set_options
                    ⟹ 활성 주문의 57.1% 가 판매전표로 전환 불가
거래처 격차 3건     slips.partner_id 를 생성 시 필수로 (활성 1행이 NULL)
                    견적 → 전표 전환에 기초거래처 검색·설정 단계가 없음
                    EstimateFormPage.tsx:1758-1765 가 거래처 없는 견적 생성을 막을 수 있음
#896 결정 A         음수 factor 허용 + 평가기 전역 max(0,·) 클램프
                    QuantitySyncRuleValidator.java:459 의 signum() <= 0 거부가 막고 있음
```

---

## 2. 🚨 이번 세션에서 뒤집힌 전제 3가지

### ① 홈멀티 리모컨 `기본` 은 `무선` 이 아니다

```js
// index.ejs:8252-8267
if (opt === '기본') {
  if (REMOTE_360_DEFAULT) setR(REMOTE_360_DEFAULT, cntC);        // 360 카세트
  if (R_CH)               setR(R_CH, cntI);                      // AR-CH01
  if (REMOTE_WIRELESS)    setR(REMOTE_WIRELESS, cntW + cntWall);  // 벽걸이 + 그 외
} else {
  const main = (opt === '유선') ? R_WE : R_WG;   // 전량 한 모델
}
```

🔑 `기본` = **"실내기마다 그 종류의 표준 리모컨"** · `유선`/`컬러유선` = **"전량 한 모델"**.
   의미의 층이 다릅니다. 명칭만 접으면 **360 카세트와 1way 리모컨이 사라집니다.**

⟹ 옵션 명칭 통일 슬라이스는 **설계를 다시 세워야** 합니다.
   `기본` 을 값으로 유지하고 전개를 수량동기화 규칙으로 표현하는 방향.

### ② 옵션 문자열은 이미 DB 에 저장돼 있다

```text
slip_lines 옵션 JSON 20건 중 '블랙판넬' 4건 (2026-08-10 17:17:43 KST 실측)
⟹ "condition_json 이 0행이라 소급 비용 없음" 은 옵션 문자열에 해당하지 않는다
```

### ③ GitGuardian 통과는 안전 증거가 아니었다

```text
.gitguardian.yaml:27-28 이 samhan_dev_pw 를 전역 ignored-matches 로 등록
application*.yml 도 별도 ignored path
⟹ 스캐너가 이 비밀을 원천적으로 안 본다. #1162 가 이 등록을 해제한다
```

---

## 3. 개발책임자 결정 (이번 세션)

```text
· 로그는 전 서비스 · 실패도 · 조회도 기록 · auth-service 로 모두 · 일원화
  🚨 데이터가 다 모이므로 부하 대비 기획이 필요 → #1161
· 보존 정책 · 실패 경로 모두 **권장안으로 진행**
  보존은 A 변경감사(길게) / B 실패(길게) / C 조회(짧게) 3등급, 부하 시 C 부터 버림
  실패는 재시도 후 DLQ · DLQ 소비자 신설 · queue 상한 · 조용한 소실 금지
· 주문서·견적서가 판매전표 정보를 **모두** 보유해야 한다 (전환 가능해야 하므로)
· 거래처는 견적서만 없어도 되고, 주문서·판매전표는 필수
  견적 → 전표 전환 시 기초거래처에서 **검색해서 설정**(필수값)
· 수량동기화 = **본체(실내기·실외기) → 부자재**. 부자재 쪽에서 본체를 칩으로 복수 지정
· 받침·발통은 실외기만으로 판별 불가 — **카테고리별 · 실외기별**로 다르다
· AIM-A01N 은 홈멀티·싱글중대형의 **1way 실내기(싱글은 1way 세트)** 에 한해
  유선/컬러유선 옵션 시 그 수량만큼
· 홈멀티 분기관은 AJ060MXHNBC1 실외기에 한해 첫 분기관이 AXJ-YA2512N, 나머지는 AXJ-YA1509N
· #1162 범위를 넓혀도 됨
```

---

## 4. 이번 세션에 머지된 것

```text
c1a11e14b  #1159 이관 메뉴 제거 (이슈 #826 CLOSED)
3aa413ba8  #1157 V31 복구 — V35 로 1,114 품목 되살림
```

## 5. 남긴 문서

```text
docs/dev-reports/2026-08-10-audit-logging-full-inventory.md
docs/dev-reports/2026-08-10-audit-logging-operation-matrix.md      580 동작 · 42 기록 · 485 공백
docs/dev-reports/2026-08-10-order-detail-vs-slip-detail.md
docs/dev-reports/2026-08-10-estimate-order-slip-field-carryover.md
docs/dev-reports/2026-08-10-qty-sync-initial-values-sweep.md       72 계열 · 82 규칙
docs/dev-reports/2026-08-10-legacy-base-foot-mapping.md            45 매핑 · chooseBaseModel
docs/dev-reports/2026-08-10-qty-sync-target-classification-recon.md
docs/dev-reports/2026-08-10-896-option-naming-recon.md             🚨 기본=3갈래 발견
docs/handoff/DB-MIGRATION-RUNBOOK.md                               V35 복구 절차 (컨테이너 psql)
```

## 6. 환경 메모 (회사PC 실측)

```text
· .env 파일 **없음** (루트·infrastructure 둘 다). compose 의 평문이 유일한 자격 출처
  🚩 집PC 는 .env 에 저장하고 워크트리마다 주입한다고 하셨습니다 — #1162 가 이 체계를 맞춥니다
· 배포본이 낡음. 라이브QA 전 `docker inspect -f '{{.Created}}'` 로 나이를 재십시오
· 워크트리 45개. 오래된 것 다수 — 정리 대상이지만 이번 세션에선 손대지 않았습니다
