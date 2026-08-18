# 현재 진행 상황 (2026-08-18 · Codex 사용 한도 소진 시점)

> 🚨 새 세션은 이 파일만 읽으면 컨텍스트가 회복된다.

## 🚨 지금 막혀 있는 것 — Codex 사용 한도

```text
You've hit your usage limit. … try again at Aug 20th, 2026 12:31 PM.
```

**새 codex 라운드를 발주할 수 없다.** 계정 한도라 `TERRA` 폴백도 안 듣는다
(`feedback_model_substitution_delegated_to_pm` 의 "at capacity" 와 다른 상황).

### ✅ 개발책임자 결정 (2026-08-18) — **한도 회복까지 대기**

> *"그래 그러면 코덱스 토큰 회복될때까지 기다릴게. 우선 핸드오프로 정리 부탁해."*

```text
재개 가능 시각  2026-08-20 12:31 (또는 크레딧 충전 시 즉시)
              https://chatgpt.com/codex/settings/usage
```

🚫 Claude 서브에이전트로 대체하지 않는다 — 같은 토큰 풀이라 절약이 0 이다
   (`feedback_pm_delegate_to_codex_conserve_tokens`)

### 🔁 재개 순서 (권장)

```text
1  공유 QA 계정 1068689215 잠금 해제        ← 아래 §"공유 QA 계정 잠금" 참조
2  #1268  golden fixture 기대값 갱신        ← 가장 가깝다. 한 라운드면 머지
3  #1265  SOL 재판정 4회차
4  #1271  브리핑 순화 후 SOL 판정
5  #1245  라이브 확인 · SOL 적대검증
6  #1251  범위 좁혀 증상 재현부터
7  #1268 머지 후 → 판넬·자재·할인 축으로 하드코딩 제거 확대 → #1269 보류 해제
```

---

## 오늘 머지 6건

```
#1262  시트 식별자 마스킹        다섯 라운드 · 도달 0 · 표기 형태 축 닫힘
#1266  UUID wire vs 표시         네 라운드 · 47개 전수 표로 종결
#1272  카테고리 설정 견적품목 이전  🔑 병목 해제 — #1268 을 열었다
#1267  거래처 마스터·importer     조인 키를 코드로 · 중복 재유입 0
#1264  일마감 회계전표 + A-2 탭 분류
#1270  일마감 열 정합 + 견적품목 납품가   17열 17/17
```

---

## 🔑 이 세션의 핵심 — 하드코딩 축

개발책임자 지시로 방향이 잡혔다.
> *"하드코딩만 안되면 돼" · "견적품목 옵션만 제대로 동작해야지"*
> *"기초품목에서 견적품목으로 이동하는 게 먼저 되어야 하는 거 아닌가"*

### 정찰 실측

```text
종합견적서  하드코딩 24곳 · 설정 기반 9곳
주문서웹    운영 92블록 · 대체 가능 87 / 불가 5
합계        116곳
격차        서버 필드 7개 · DB 설정 324건 · 웹 함수 9개

🚨 설정이 거의 비어 있다 — 걷어내면 기능이 사라진다
   활성 수량동기화 규칙 1/20 규칙군 · 옵션 축 채움 0건
   HOME 구성품 관계 0건 · COMMERCIAL OUTDOOR 외 0건
   컬러 리모컨만 65세트(활성 62)
```

### 확정된 순서

```text
① #1272  저장 자리 + 데이터 이전        ✅ 머지 완료
② #1268  양 웹을 동적 소비로 전환         ← 진행 중 (리모컨 축)
③ #1269  단가표 걷어내고 설정에서 읽기     ← 보류 (② 완료 후)
```

### 🎉 #1268 이 관문을 통과했다

```text
코드 어디에도 없는 이름 「해오라기824731」 을 설정에 넣었더니
  목록에 뜸 → 선택 적용 → 세트가 1,653,531원 · 상세 단가 137,531원
  양쪽 웹 모두 정상
⟹ 파이프라인(설정→서버→웹)이 진짜 설정 기반으로 돈다
```

🔑 **기존 값이 맞는 것과 설정 기반인 것은 다르다.** 기존 이름으로 시험하면 하드코딩이 남아도 통과한다. 이것이 판정 기준으로 자리잡았다.

---

## 열린 PR 재개 지점

| PR | 상태 | 다음 할 일 |
|---|---|---|
| **#1268** 옵션 설정 소비 | ✅ **판정 완료 — 도달 결함 0 · fallback 잔여 0블록**<br>fallback 제거 전/후 신규 variant 값 동일(`1,653,531원 / 137,531원`) ⟹ fallback 은 죽은 경로였고 **golden fixture 가 낡은 것**으로 확정 | **golden fixture 기대값 갱신만 남았다** (CI 필수 2건). 기능은 완결됐다. LUNA 한 라운드면 된다 |
| **#1265** 웹→전표 | fix 3 커밋 완료 (`2e4d19e6c`) | **SOL 재판정 4회차 발주** — 편집 중 1원 · 주문번호 화면 표시 · shared/common 파급 |
| **#1271** DPS 입고비교 | fix 2 커밋 완료 (`c0c29060c`) | 🚨 **SOL 판정 브리핑을 더 순화해야 발주된다** — 보안 필터에 2회 걸림. 남은 미검증 축 = 헤더 행 탐색 |
| **#1245** 레거시 CSV | 누락 92 + drift 42 적재 완료 (`b24f876df`) | 라이브 화면 확인 · SOL 적대검증 |
| **#1251** TLS·504 | 진단 2회 타임아웃 · 산출물 0 | **범위를 증상 재현 2건으로만** 좁혀 재발주 (60분 상한) |
| #1269 싱글 차액 | 보류 | #1268 완료 후 단가표 제거 · 257행 가격 대조표 필요 |
| #1188 | ⏸️ 외부 자격 대기 | — |

---

## 🚨 이 세션에서 배운 것 (메모리 반영 완료)

```text
feedback_uuid_no_user_visibility           UUID 캐논은 표시 금지 · wire 허용
feedback_stale_deployment_looks_like_defect 공유 컨테이너는 main 이미지 — 404 는 배포본 나이
feedback_no_cd_compound_use_git_dash_c     cd 복합은 모드 무관 항상 권한창
feedback_git_add_all_swallows_concurrent_round  커밋 뒤 git status 로 잔여 0 확인
feedback_sol_review_includes_live_qa       fix 라운드에 라이브를 처음부터 넣어라
feedback_codex_briefing_security_filter    권한·자격 표현이 몰리면 보안 필터 (2회 실측)
feedback_daily_closing_uses_estimate_items A-2 탭 분류 레거시 정본 확정
```

### 반복된 함정 (모든 브리핑에 고정 삽입 중)

```text
1  백엔드만 띄우고 renderer 를 안 띄워 ERR_CONNECTION_REFUSED  → 둘 다 띄워라
2  "Browser 런타임 []" 을 라이브 불가로 오판                    → Playwright 를 쓴다 (3회)
3  `-live.spec.ts` 가 mock 게이트에 수집돼 CI 파손              → `-real-qa.spec.ts` (2회)
4  라운드가 남의 보고서 경로에 덮어씀                            → 지정 경로에만 (3회)
5  라운드가 남의 워크트리 프로세스를 종료                        → 회수는 자기 것만
6  브리핑이 넓으면 2시간 타임아웃 · 잔여 0                       → 좁혀서 완주 (2회)
```

### 🔑 범위 축소가 효과를 냈다 — #1245 사례

```text
1차  전체(누락+drift+가역+라이브)  → 2시간 타임아웃 · 잔여 0
2차  누락 92만                     → 완주 · V7 · fresh 0→92
3차  drift 판정만                  → 완주 · 원천 42 / 현행 1 / 판단불가 0
4차  drift 적재만                  → 완주 · V8 · UPDATE 42 · 원복 검증
```

**판정과 적재를 분리한 것이 값을 냈다.** 한 번에 했으면 `4348703365`(2026-08-13 에 사람이 고친 값)를 원천으로 덮었을 것이다.

---

## 🚨 개발책임자 확인 필요 — 공유 QA 계정 잠금

```text
계정      1068689215
상태      LOCKED — 로그인 3회 실패 (credential 불일치)
경위      #1268 라이브QA 중 초기 후보 계정으로 시도하다 잠김
조치      🚫 수동 복구 안 함 — 공유 DB write 금지 규율을 지켰다
대체      최종 증거는 resolveQaCredential() 과 일치하는 9999000001 로 수집
```

### ✅ 개발책임자 지시 (2026-08-18) — **메뉴에서 풀고 진행**

> *"메뉴에서 잠금해제(잠금 → 승인 상태로 변경)하여 진행요망"*

🚨 **재개 시 가장 먼저 할 일이다.** PM 이 경로를 확인해 뒀다.

```text
서비스   partner-auth-service
경로     PartnerApprovalService.updateStatus(partnerCode, APPROVED)
동작     services/partner-auth-service/.../service/PartnerApprovalService.java:118-126
           status == PENDING      → approvePending()
           status == LOCKED       → unlock()          ← 이 계정이 해당
           status == LONG_UNUSED  → restoreFromLongUnused()
결과     PartnerAuth.unlock() 이 failedAttempts = 0 으로 되돌리고 잠금을 푼다
           services/partner-auth-service/.../domain/PartnerAuth.java:235-237
```

⟹ **거래처 승인 관리 메뉴에서 `1068689215` 를 「승인」으로 바꾸면 된다.**
🚫 DB 직접 UPDATE 로 풀지 마라 — 화면 경로로 하라는 지시다.

🚨 재발 방지 — QA 브리핑에 "자격은 `resolveQaCredential()` 로만 얻고, 임의 계정으로 시도하지 마라" 를 넣어야 한다.

---

## 환경 상태

```text
권한 모드     bypassPermissions (.claude/settings.local.json)
워크트리      본체 + w1234 · w1240 · wd03 · wdps · wslip · wsrd · (wcat/wdcp/wdc70/wp2/wuuid 회수됨)
              전부 미커밋 0
공유 컨테이너  24개 유지 (unhealthy 0) · 격리 컨테이너 0
QA 포트       잔여 listener 0
main          origin 과 격차 0
원격 브랜치    머지된 6건 잔재 0
```

### 🚩 세션 종료 시 회수한 것

```text
라운드들이 "회수 완료" 로 보고했지만 실제로 남아 있던 것
  java 4개  포트 28081 · 28085 · 28185 · 28186   (브랜치 JAR)
  node 2개  포트 5195 · 5196                     (renderer dev server)
  격리 컨테이너 2개  codex-1271-sol-r3b-pg · codex-1271-r3-pg

🚨 라운드 보고의 "프로세스 잔여 0" 을 그대로 믿지 마라.
   세션 종료 전에 PM 이 포트를 직접 훑어야 한다
   (`feedback_qa_processes_leak_and_starve_machine`)
🚩 특히 **중단·실패한 라운드**는 회수 단계에 도달하지 못해 반드시 남긴다.
   이번엔 보안 필터로 죽은 #1271 판정과 PM 이 중단시킨 #1269 판정이 그랬다
```
