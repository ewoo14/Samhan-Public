# 현재 작업 핸드오프 노트

> 회사 PC 첫 세션 시작 시 본 파일만 읽으면 즉시 컨텍스트 복원 가능.
> 갱신: 2026-06-30 오전 (**회사PC 재개 세션** — 집PC 인계 완료, **협업 S2b(#675) 머지** squash `3ea02f1e`: Codex 라운드3 0수렴·1:1 라운드 소급보완·실 Docker 라이브 QA). **협업 S2c·S2d-1·S2d-1b·S2d-2(#679 `27c686b7`) 머지**(상태의존 카운트 · 헤더+라인 셀 레드라인 · **라이브 변경 하이라이트** — S2d 계열 완료). **S3-0 공용화 토대 머지(#680 `3a8e8882`)** — 다음 = **S3-1~ 문서별 롤아웃**(주문→견적→회계→결재→배차). **🌙 야간 자율(내일 오전까지·답변불가·권장방향).** **⚠️ 개발책임자 확정 대기 3건**(CODEF cutover 파라미터·셀 문자캐럿·협업 A~D 세부)은 아래 "⚠️ 오전 개발책임자 확정 대기" 절. 다음 세션 첫 읽기 파일.

---

## 🌙 2026-06-30 새벽 자율 세션 — CODEF 완결 + 협업 에픽 (개발책임자 "권장방향 진행, 오전 확정")

> 개발책임자 위임: "협업 슬라이스까지 워크플로우 준수 모두 완료, PM 자율" + "권장방향으로 진행하고 오전에 모두 보고·수정방향 확정"(새벽 결정 불가). **본 절이 최신.**

### ✅ 머지 3건 (오늘 밤)
- **#669**(`f540b252`): #531 잔여 검증 + dashboard AccountingClient 실 동작·요청계약 테스트 보강.
- **#670**(`8246d2c9`): **CODEF Task 6 — easyCodef 실 SDK**(EasyCodefClientImpl + Factory). 순차 듀얼리뷰 0수렴(Opus 5-agent×2 + Codex×2 + Opus BE 재확인). **실 CODEF 샌드박스 라이브 QA**(createAccount ACTIVE·listCards=3·listBankAccounts=10). 라이브 QA가 CODEF-mode 파손 버그(`organizationCode` — 목록 항상 빈)를 단독 적발→Opus fix. 증적 `docs/qa/codef-task6/`. (본 세션 `.env` 샌드박스 자격 보유.)
- **#671**(`0bebb587`): **CODEF Task 7 — FE 금융연동 페이지**(CodefConnectionPage, MASTER, page-code accounting.bank-matching). 7라운드 듀얼리뷰 0수렴(Opus×3 + Codex×3). Design BLOCKING(Badge/FormGrid 자체재구현)→Opus fix. **Codex 라운드2가 loginType=ID_PASSWORD 실등록 실패 위험 단독 적발**→CODEF raw 코드(5/0/1) fix. 데스크톱+모바일 라이브 QA 8컷 `docs/qa/codef-task7/`.

### ⚠️ 오전 개발책임자 확정 필요 (CODEF cutover 파라미터 — **코드 결함 아님**)
1. **loginType 코드↔방식 매핑**: 현 `5`=마이데이터(기본, Task6 검증값)/`0`=공동인증서/`1`=아이디·비밀번호. `0`/`1`은 CODEF 문서/샌드박스 businessType별 확정 필요.
2. **credentials key명**: 현 `{id,password}`. loginType 0/1 실사용 시 CODEF가 `loginId`/`loginPw` 등 기대하면 조정(Task6=마이데이터선 credential 미사용으로 미검).
→ FE 구조/플러밍 정합. 실 CODEF 파라미터는 샌드박스 cutover서 확정.

### 🔧 워크플로우 메모리 보강 (#670 위반 박제, `18c4a421d`)
#670서 Codex 라운드2·Opus 수렴재확인을 **실행 후 미게시**(PM 종합에 흡수) → 개발책임자 지적 → 소급 게시 + `feedback_canonical_workflow.md` PREFLIGHT #6/머지게이트에 **"실행 라운드 수 = PR 게시 라운드 수 1:1 대조"** 박제.

### 🚧 진행/다음 — 협업 코-에디팅 에픽(#16) + 단가인상(#17)
**협업 = 구글 독스/시트식 라이브 코-에디팅**(개발책임자 2026-06-30 명확화·정정 다수). soft-lock 접근(#672)은 **draft 파킹**(피벗 — 락 아닌 낙관적 라이브 머지). 목표 = 각 전표/문서 **전 범위(모든 헤더 필드+품목 셀)** 라이브 커서·셀 셀렉트·실시간 편집 + **A~D**: ①단일색상(presence=coedit=audit, BE PresenceColor 단일소스) ②상태의존 카운트(판매전표=작성완료·창고이관 後 수정카운트 증가, 前은 편집O·카운트X) ③로그=첫 작성 이후 항상 ④레드라인 재귀(카운트 증가 상태 편집=기존값 취소선+바로 위 수정값을 사용자색+라벨, 수정의 수정도 스택). 6문서(slip·견적·배차·회계전표·주문·그룹웨어결재) 롤아웃.
- ✅ **S1 머지(#673, `886906b33`)**: Yjs 코-에디팅 **토대**(provider·SSE relay·awareness·CollaborativeTextField·mirror-div 커서). slip 협업 메모 1필드. Opus×5·Codex×5 0수렴(payload DoS·IME·resync/retry·caret·권한 VIEW→CREATE·Yjs snapshot 무결성·커서 UI 실결함 다수 해소). 설계 `docs/superpowers/specs/2026-06-30-live-coediting-design.md`.
- ✅ **S2a 머지(#674, `fcdbb6bea`)**: slip 전표 **전체 폼** Yjs 바인딩(헤더 `Y.Map`/자유텍스트 `Y.Text` + 품목 `Y.Array<Y.Map>`) + 문서전역 awareness(필드/셀 라이브 커서) + **단일색상(A 달성** — `presenceColor.ts`=BE `PresenceColor.fromUserId` 일치, presence=coedit). Opus×3·Codex×2 0수렴(숫자셀 clear·품목셀 배지 높이·**provider 영구잠금 회귀** 해소). CollaborativeSlipInput·createDocCoeditProvider.
- ✅ **S2b 머지(#675, squash `3ea02f1e`, 회사PC 2026-06-30 오전)**: slip 문서전역 수정/버전 로그(첫 작성부터) — 저장 PUT 후 EDIT revision capture + 인접 스냅샷 diff(헤더/품목 셀 `fieldChanges`, **productId Deque 발생순서 매칭**) + SlipVersionHistoryPanel 단일색상 표시. 기존 `slip_revisions` 편입(신규 Flyway 0)·UUID 비노출. **듀얼리뷰 7라운드 0수렴**(Codex개발·Opus×3·Codex×3): capture-trigger 누락 라운드별 1건(spec/note·매입 supervisionAddress·productId) → Opus 라운드3 전수 sweep 계열 종결 → **Codex 라운드3 독립 0수렴**. ⚠️**회사PC 머지게이트 1:1 점검이 Codex 라운드2 실행-후-미게시 자가적발→소급 보완**([[feedback_canonical_workflow]] PREFLIGHT #6, #670 패턴 재발 차단). PM 독립 BE 검증=계열 clean(매입·매출 digest 대칭, capture 호출처 9곳 감사공백 0). 라이브 QA: gradlew slip **597 passed**·**실 Docker slip-service 재빌드 healthy 부팅**·버전이력 라우트 라이브(401)·실DB IT green·CI 28/28.
- ✅ **S2c 머지(#676, squash `b237e76b`, 회사PC 2026-06-30)**: 사용자 노출 "전표수정내역"(editHistoryCount) 상태의존 게이트 — OUTBOUND=창고이관(`inspect()`/COMPLETED)·비-OUTBOUND=결재선(`send()`/SENT) 後 편집만 카운트. revisionCount(audit) 불변, 신규 `revision_count_baseline`(V53) 차감, 기존 임계통과 backfill=0. **듀얼리뷰 5라운드 0수렴**(Codex가 **restore 카운트누락·mock params false-green 2건 단독 적발**→fix, Opus 결함계열 sweep로 collab restoreSnapshot SYSTEM 미카운트 구분 명시). 라이브 QA: 실 DB V53 backfill(COMPLETED→0/DRAFT→null)·gradlew slip 602·FE vitest 401·fresh PG probe. 📌**개발책임자 검토가능(가역)**: ①복원=카운트(user restoreToRevision, 감사revert 일관) ②INBOUND PurchaseQueryPage 컬럼 미노출(forward-compatible). ⚠️**게시규율 재발**: Codex 5-agent 라운드 즉시 미게시→개발책임자 지적→시정([[feedback_canonical_workflow]] PREFLIGHT #6 트리거 강화).
- ✅ **S2d-1 머지(#677, squash `d42ad796`, 2026-06-30)**: 임계 통과 전표 조회 시 **헤더 셀 인라인 레드라인**(track-changes) — anchor(V54 `redline_anchor_revision_no`, 임계 전이 시점 max revision_no) 後 `slip_revisions` 인접 diff를 필드별 layers 재구성(`RedlineCell` 재귀 스택: 기존값 취소선+사용자색 수정값+라벨). **헤더 한정** 스코프(라인 셀=S2d-1b 후속). **듀얼리뷰 5라운드 0수렴**(Opus 2 BLOCKING[라인 row-index 누적·단가/합계 VAT]→헤더한정 / Codex 3 BLOCKING[redline 권한403·헤더셀 8종 미배선·hooks] **단독 적발**→fix / Opus 0+Codex 0). anchor 결함계열 sweep=production 무버그. 라이브 QA: V54 fresh PG probe·gradlew slip **611 passed**·권한 IT 46/46·vitest 406·**실 RedlineCell 컴포넌트 캡처**(개발책임자 요청, vite 직접서빙 데모 — 앱 hash 라우터 mock-web deep-link 미지원 우회). DECISIONS D-COEDIT-S2D-01~02.
- ✅ **S2d-1b 머지(#678, squash `c55a112e`, 2026-06-30)**: **라인(품목) 셀 인라인 레드라인** — productId+등장순서(occurrence) 안정키 별도계산 + `SlipSnapshot.Line` VAT포함 필드(`unitPriceWithVat`/`vatAmount`/`supplyAmount`) 확장(결정 A, NON_NULL JSON·**Flyway 불요**). 단가/합계 VAT포함 표시값(과거 snapshot=VAT제외 정직 fallback, ×1.1 가짜 배제). 헤더+라인 전 셀 redline 완성. **듀얼리뷰 5라운드 0수렴**(Opus 2 BLOCKING[QA `SlipRedlineIT` `complete` 전이 누락 CI RED·Design mock 비정합]→fix / Codex **IT 응답파싱 root fields→`data.fields`(ApiResponse 래핑) 버그 단독 적발**→fix / Opus 0+Codex 0 — **순차 듀얼리뷰가 상대 fix 버그 차단 실증**). 라이브 QA: fresh PG probe(jsonb 역직렬화 안전)·gradlew slip **616 passed**·**slip-it-core CI 통과**·vitest 408·실 RedlineCell 라인 캡처. 한계(dev-report): 동일 productId 복수행 occurrence·legacy 정직 fallback·restore drift. DECISIONS D-COEDIT-S2D-03.
- ✅ **S2d-2 머지(#679, squash `27c686b7`, 2026-06-30 야간)**: **라이브 변경 하이라이트** — awareness에 `lastEdit:{fieldPath,ts}` 추가 → 임계 前 Yjs 라이브 코-에디팅 중 원격 사용자가 방금 바꾼 셀을 사용자색으로 ~2.5s 펄스+이름 배지(`getRemoteEdits` ts 내림차순 최신우선·본인제외). 양 provider+`CollaborativeSlipInput/TextField`·`global.css` keyframe. **BE 변경 0**(awareness opaque relay). 접근법 A(transient, accept/reject 없음). **듀얼리뷰 5라운드 0수렴**(Opus 5-agent 전 차원 0 BLOCKING→Opus fix[ts정렬·페이드 ts-기준·송신단언] ↔ Codex 5-agent 0수렴 → Opus 0+Codex 0). 라이브 QA: vitest 27·실 펄스 캡처·BE 무변경·CI 28/28. DECISIONS D-COEDIT-S2D-04. 편집모드 redline 스택=S2d-2b 후속.
- ✅ **S3-0 머지(#680, squash `3a8e8882`, 2026-06-30 야간)**: **코-에디팅 relay/provider 공용화** — slip 전용 `SlipCoeditService`→`shared/collab-core` `CollabCoeditService`(도메인 무관 documentId) + slip delegate + FE `makeCoeditApi(basePath)` 팩토리 + provider `headerTextFields` 옵션화 + slip 재배선. **계약 무변경**. S3 6문서 롤아웃 토대. **듀얼리뷰**: Opus 5-agent가 false-green(`CollabCoeditServiceTest` useJUnitPlatform 미선언→0건 실행) BE·DevOps·QA 만장일치 적발→Opus fix(build.gradle 자가선언+ci.yml 등재+byte-cap 복원) ↔ 2차 독립 재검(codex exec 환경실패→Agent 대체 [[codex-mcp-session-limit]]) 0수렴. 라이브 QA: collab-core 8 tests·vitest 32·CI 33·공유 relay standalone round-trip+SSE 무회귀 실증. DECISIONS D-COEDIT-S3-00.
> ✅ **S2d 계열 + S3-0(공용화 토대) 완료**(2026-06-30 야간). **협업 에픽 잔여 = S3-1~ 문서별 롤아웃**(주문→견적→회계→결재→배차 순; 각 1차=단일 메모 저위험/2차=폼 셀. 공유 `CollabCoeditService` delegate + FE `createDocCoeditProvider(basePath=/{도메인}/{id})` 배선) → 협업 종결 → **#17 단가인상** 등 지시 에픽. redline 일반화=독립 하위트랙(revision 보유 주문·견적만). **🌙 야간 자율 위임(내일 오전까지·답변불가·권장방향·블로킹 질문 금지). ⚠️Codex 디스패치=sub-agent 금지 단일프롬프트 또는 Agent 대체([[codex-mcp-session-limit]]).**
> 📌 **별도 트랙(금융연동 vendor) — 🆕 팝빌 요금표 확정(개발책임자 제공 2026-06-30 야간)**: 결론=계좌+카드(대출 제외), 자체 ASP(마이데이터) 불가. **🔴정정: 팝빌이 계좌+카드(사용내역=지출 B) 둘 다 제공**(앞선 "팝빌 카드 없음" 리서치 오류). 팝빌 금융 단가: 계좌거래내역=신청계좌당 월정액(10분 6천/30분 5천/1시간 4천/4시간 3,500/24시간 3천), 카드사용내역=신청카드당 3천(전일 기준). **권고: 계좌+카드지출=팝빌 단독**(12계좌+10카드 가정 ≈ 월 6.6만[계좌 24시간]~10.2만[계좌 10분], vs CODEF 종량 80만 → 8~16배↓). ⚠️주의: 팝빌 카드=전일(daily, 30분 불가나 회계 카드매칭엔 충분)·**지출(B)만**(가맹점 매출 A는 여신금융협회/CODEF 별도). Seyfert 부적합·하이픈 대출미확인. 팝빌은 전자세금계산서 발급(100/건)·홈택스 수집(3만/월)도 제공. docs/research/2026-06-30-financial-integration-codef-vs-alternatives.md + [[external-integration-research]] 메모리(별도 트랙 docs 브랜치 통합 예정). **실 계좌/카드 개수·카드매출(A) 필요여부 회신 대기.**
> ⚠️ **오전 개발책임자 확정 대기**: (1) **CODEF cutover 파라미터**(loginType 0/1 매핑·credentials key명 — 위 ⚠️절) (2) **단일라인 셀 문자 캐럿 여부**(현재 메모 textarea=문자 캐럿 / 단일라인 입력 셀=셀 강조+라벨까지 — 모든 셀에 문자 캐럿 추가할지, anchor/head 는 이미 awareness 전송 중 → S2 polish 가능) (3) 협업 A~D 세부 방향.
- ⏳ NB polish(S2a 이연): removeLine setState 사이드이펙트 분리·onValueChange useCallback·Y.Text applyDelta(문자 CRDT).
- ⏳ **S3+**: 6문서 각 동일 모델 전범위 롤아웃.
- ⏳ **#17 단가인상**: 변동 전/후 별도 카테고리 분리·렌더 시점 '인상 전/후' 옵션·주문서 카테고리별 변동날짜(KST) 자동전환. 협업 이후.
> 정직: 협업 전범위 라이브 코-에디팅 = 대형 다슬라이스(계산·검증·상태·영속·레드라인 UI). S1 토대 수렴만 Opus×5·Codex×5(10라운드). S2~ + 6문서 + #17 = 상당 잔여 — 야간 최대 진척, 오전 종합 검토·확정.
> 메모리: PREFLIGHT #3 보강(Codex 라운드도 라이브QA 스샷 인라인 의무, `5c5c97d3f`).

---

## 🔄 2026-06-29 세션 3 — CODEF 실연동 에픽 (집PC 재개 지점)

> 6시 퇴근 종료. 집PC 재개 — **이 절을 먼저 읽을 것.**

### 완료
- **#666 머지**: README 최상단 프로젝트 구조(17 BE+8 client+6 shared) + **DB ER 다이어그램**(15 service-DB Mermaid + cross-service flowchart). (samhan-public-overview.html ER 동기화는 후속 — 미완.)
- **CODEF 에픽 기획 완료**(main 반영): brainstorming → 설계 `docs/superpowers/specs/2026-06-29-codef-connectedid-registration-design.md` → 계획 `docs/superpowers/plans/2026-06-29-codef-connectedid-registration.md` (7 task). 결정: 회사 1개 connectedId·easyCodef SDK·자격 무저장·경계=등록+목록검증(거래내역 fetch는 다음 epic).
- **#667 머지**(`dd0d6ac9`): CODEF **BE 슬라이스 1**(Task 1~5) — easyCodef SDK 1.0.6 의존성 + EasyCodefClient 인터페이스 + Flyway V47(codef_connection·codef_registered_institution, 자격 무저장) + CodefConnectionService/Controller(MASTER) + CodefClientImpl CODEF분기 배선. 순차 듀얼리뷰가 실결함 4건 적발(Opus ci.yml / Codex connection 정합성 3: 동시성 advisory lock·null connectedId throw·status ACTIVE 필터).

### ✅ #668 머지 완료 (집PC 세션 4, `a6e7a2aec`)
- #667 **0수렴 단축** 적발 → 소급 재리뷰 중 Codex 가 BLOCKING(`saveConnection()` 의 같은 @Transactional 안 catch 후 재조회 → PostgreSQL **aborted-tx** 복구 무효) 적발 → #668 머지 보류였음.
- **집PC fix(Opus 직접)**: 깨진 catch 제거 — advisory lock(`pg_advisory_xact_lock`)이 등록을 직렬화 + 기존 row in-place 갱신하므로 동시 INSERT 경합 없는 **도달 불가 dead 코드**였음. 위반은 그대로 전파(정직한 에러). 직렬화·in-place 불변을 주석·IT 단언(`findAll().hasSize(1)`)에 박제.
- **fresh 순차 듀얼리뷰 0수렴**(Opus 5-agent 5차원 0 + Codex gpt-5.5 0, 양쪽 새 fix 없음). family sweep clean(다른 DIV catch 는 REQUIRES_NEW 격리/skip/rethrow). QA: fresh PG V47 제약 실증(unique·CHECK 실 위반 캡처)+실 PG IT 12건(0 skip/fail). CI 전 잡 green. 교훈 [[feedback_aborted_tx_after_div_catch]]. 머지는 하네스 게이트로 개발책임자 명시 승인 후 진행.

### 다음 (CODEF 완결 → 그 다음)
> ⚠️ **집PC 제약**: `services/accounting-service/.env`(샌드박스 자격)가 집PC 에 **없음** → **슬라이스 2(Task6 실 SDK)의 의무 라이브 QA(샌드박스 호출)는 집PC 불가 → 회사PC 과제**. 집PC 진행 가능 = **슬라이스 3(Task7 FE, mock 기반 QA — 자격 무관)**. Task7 은 BE 엔드포인트(#667 기머지)에 의존하며 Task6(실 SDK)와 독립 진행 가능.
- **슬라이스 2**(브랜치 `feat/codef-easycodef-sdk-impl` 생성됨, **구현 0** — 무결성 점검에 우선순위 양보): EasyCodefClientImpl 실 SDK. **easyCodef API 확인됨**: `io.codef.api:easycodef-java:1.0.6`, `new EasyCodef()`+`setClientInfoForDemo/setPublicKey`, `createAccount(EasyCodefServiceType.SANDBOX, HashMap)`, `requestProduct(url, type, map)`, 응답 `result.code`="CF-00000". 샌드박스 `https://development.codef.io` `/v1/account/create`(countryCode=KR·businessType BK/CD·clientType=P·organization·loginType·password[SDK RSA]), **fixed-response**. → **반드시 실 Docker 라이브 QA**(standalone 기동+샌드박스 호출+실 캡처 docs/qa/ — 개발책임자 명시).
- **슬라이스 3**: FE 회계 설정 "CODEF 금융연동" 페이지.
- **CODEF 전부 완료 후** → **라이브 필드-레벨 협업 에픽**(개발책임자 요청): 현재 협업=presence(보는 사람)+커밋기반 수정완료. 구글 워크스페이스식 **실시간 필드 클릭/값 편집 가시화는 미구현** → brainstorming 신규 에픽.

### ⚠️ 이번 세션 프로세스 교훈 (집PC 엄수)
- **0수렴 단축 금지**: fix 후 반드시 **fresh 순차 듀얼 라운드(Opus 에이전트 + codex exec) 재실행** 후에만 머지. CI-green+코드리드로 "0수렴 선언" 금지(개발책임자 2회 적발 — Docker QA·0수렴 둘 다).
- **실 Docker 라이브 QA**: 실 상호작용 슬라이스는 standalone 기동+실 캡처. CI Testcontainers 만으로 "라이브 QA" 라 칭하지 말 것.
- **Codex MCP 세션한계**(-32000): `codex exec --sandbox <ro/ww> --model gpt-5.5 -c model_reasoning_effort=high "<프롬프트>" </dev/null`(리다이렉트 필수) 우회. 새 세션 시작 시 MCP 자동 회복.
- **CODEF 키 노출**: 데모·샌드박스 키가 채팅 노출됨 → **회전 검토**(gitignored .env 만 보관, 커밋·메모리 비포함 유지).

---

## ✅ 2026-06-29 세션 2 완료 (RestClient #531 family + DEV-3 date-bomb + CODEF 조사)

- **#664 머지**: DEV-3 활동로그 mock **date-bomb** 수정(시드 절대날짜→now 상대값). main Desktop Playwright hard gate 적색 해소(모든 PR 차단 P1).
- **#663 머지**: #531 RestClient 계약테스트 4종 + **warehouse 실 인증버그** 적발·fix(공개 endpoint X-Internal-Token-only→inventory `/internal/inventory/warehouses/{id}` 신설). Codex 듀얼리뷰가 Opus 미적발 운영버그(입고전표 창고명 공란) 단독 적발.
- **#665 머지**: **internal client auth파손 family 4건** 일괄 fix(개발책임자 "후속금지·모두해결" 지시). inventory→accounting 분개·notification→partner 알리고CSV·slip→notification 챗룸·slip→partner 차단목록 — 각 다운스트림 `/internal/` 엔드포인트 신설. **CI 5회 반복**이 로컬 Testcontainers npipe skip 이 가린 실결함 전부 적발(생성자 IT컨텍스트·accounting 누락@MockBean·timeout회귀·test-only생성자·@Autowired). family 전수 sweep clean(잔여 0).
- 🚩 **CODEF 결정·조사**: 개발책임자 **"전부 CODEF"**(2026-06-17 하이브리드 폐기, 오픈뱅킹/KFTC 비채택). CODEF 데모·샌드박스 키 발급 → **gitignored `services/accounting-service/.env`** 저장(커밋·메모리 비포함). 조사: `CodefClient` 6메서드 DRY_RUN mock 배선됨·실 API stub. 실연동=신규 Phase 11 에픽([[project_external_integration_research]]).
- 💰 **AWS 비용 답변**: 단일 m5.xlarge+RDS db.t3.medium 서울 = **₩40만/월(약정 시 ₩20~29만)**, 타사 ₩1억+₩100만/월 대비 압도적. 단 17서비스+ES+RabbitMQ 로 16GB 타이트(부하 시 m5.2xlarge 증설 검토).

### 🚧 대기 큐 (개발책임자 지시)
1. **README**: 최상단에 프로젝트 구조 + **DB 관계도(ER 다이어그램) 이미지** 추가.
2. **CODEF 실연동 에픽**(brainstorming→스펙→슬라이스): connectedId 등록+RSA·OAuth·stub→실 샌드박스 API.

---

## ✅ 에픽 task#24 (A2 그룹웨어 결재 일원화) — **완료** (A2-G1 BE + A2-G2 FE 머지)

자체 결재 chain ↔ 중앙 `approval_line_config` **일원화** (개발책임자 결정: A — 중앙 config 정의원 + 그룹웨어 인스턴스화, override 허용, 그룹/1인 지정 모두).

- ✅ **A2-G1 (BE 중앙 config 인스턴스화)**: PR #657 머지(`8d7450b2d`). approval_line_config 가 그룹웨어 documentType 수용·authorize/조회 일반화·V75 시드(GROUPWARE_EXPENSE_REPORT 작성자/부서장 GROUP/대표 USER)·ApprovalStep GROUP 모드(approverGroupId any-member)·CREATOR→requester USER 변환·per-doc override·opt-in. 실결함 적발: GROUP approve 409·identity spoofing·권한상승·IT false-green·V9 NOT NULL.
- ✅ **A2-G2 (FE 결재 일원화 노출)**: PR #659 머지(squash `7ec80eddd`). 결재라인 설정 그룹웨어 결재유형 결재선·생성 폼 config 미리보기+override 칩·StepView 비-admin 라벨·작성자 추론. 실결함 적발: BE계약 CREATOR→USER·**비-admin 페이지 admin 엔드포인트 403**·mock V75 불일치·**V77 대표=dev_master 자기결재 충돌**·dead code·템플릿 admin 호출·CREATOR-only 결재선. 라이브 BE QA(재빌드 A2-G1): GROUPWARE_EXPENSE_REPORT config 실존 확인.

에픽 메모리 `project_groupware_approval_unification.md`. spec `docs/superpowers/specs/2026-06-28-groupware-approval-unification-design.md`. plan `docs/superpowers/plans/2026-06-29-a2-g2-groupware-approval-fe.md`.

## ✅ OCR 메뉴 전수 삭제 — **완료** (PR #658 머지 `6abc7d859`)

개발책임자 지시: OCR 메뉴 모두 삭제, **추후 GAS(외부)→주문서 직접 전송 레거시 패턴으로 대체 예정**. 영수증 OCR(CLOVA)·발주서 업로드 OCR(Tesseract) 제거·V76(role_page_permissions hard delete + 5테이블 soft delete). 실결함 적발: V76 5테이블 패턴·CI Tesseract·ps1 UTF-16 손상 근본(.gitattributes CRLF)·credential guard. 메모리 `project_ocr_removal_gas_direct.md`.

## ✅ Phase 11 AWS 이식 준비 — PR #660 **머지 완료** + 회사 PC terraform 실증 통과

개발책임자 야간 지시 "AWS 이식 준비 — 바로 이식할 수 있도록". 기존 IaC(#152, May 11)를 **17 service 현행화 + 이식 준비 산출물** 보강. 메모리 `project_overnight_autonomous_aws_prep.md`·`project_phase11_aws.md`.

- **0수렴 달성**(Opus 0 / Codex 0, 부팅차단 기준 · **5 듀얼리뷰 반복**): Codex focused 재리뷰 "0건 — 0수렴 확인".
- **산출물**: IaC 17서비스 현행화(service_ports 실포트·17 ECR image·15 DB·max_conn 300) · 신규 `ecr.tf`·**`infrastructure/docker-compose.prod.yml`**(17 service+RDS/S3, config 유효)·`init-rds.sql`·**`infrastructure/terraform/CUTOVER.md`**(6단계 런북+체크리스트+수동 18항목)·`user_data.sh` 재작성·aws_s3_object 산출물 자동 업로드. 시크릿 평문 0→**Secrets Manager 전 일원화**·S3 첨부 5서비스 env·기존 hosted zone data source.
- **CI**: 앱 전 그린 · GitGuardian = PM false-positive 판정(Secrets Manager 참조·placeholder, 실 평문 0).

### ✅ 머지 게이트 충족 — 회사 PC terraform 실증 완료 (2026-06-29)
PR #660 은 **이미 머지됨** (`579835ef`, 2026-06-28 ewoo14). 집 PC 미설치로 미뤘던 terraform 검증을 **회사 PC 에서 실 CLI(terraform v1.15.7)로 직접 수행**:
1. `terraform init -backend=false` → AWS provider v5.100.0 / archive v2.8.0 설치 ✅
2. **`terraform validate`** → **"Success! The configuration is valid." ✅** · `terraform fmt -check` ✅
3. **`terraform plan`**(자동 tfvars) → 변수 배선·`data.archive_file` read·Outputs(api/arologis api·app·mobile) 계산 **구조 ready ✅** · 유일 차단 = `No valid credential sources`(실 AWS 자격 = 수동항목 M-1, 예상된 결과)

→ handoff 의 "validate 불가" 는 stale 였고 PR #660 머지는 건전(main IaC 유효)함을 실증. terraform CLI 는 scratchpad 에 설치(repo 무오염, init 산출물 모두 .gitignore).

### 🚧 다음 (Phase 11 실 이식 — 개발책임자 결정 필요)
실 AWS 계정 + tfvars 실값 + `terraform plan`/`apply` (CUTOVER.md 단계 1). 선행 수동 18항목(M-1~18: AWS 계정·tfvars 실값·Secrets Manager 시크릿 7종·SSH키·S3 backend 버킷·도메인 hosted zone 위임·ACM `*.arologis` SAN·로컬 PG→RDS 이관 등) — CUTOVER.md 기재. **실 계정 생성·비용(₩405K/월) 동반 → 개발책임자 착수 지시 대기.**

## 🚧 잔여 백로그 (2026-06-29 전수 검증 — 회사 PC 인계)

> 4소스(열린 이슈·메모리·코드·OPEN-ITEMS) 교차 검증. PR #661 백로그 정리를 본 PR(#662)에 통합하되, **메모리상 이미 완결/MOOT 항목은 제외**: collab presence 전 문서(#545/#546)·전표 ON_HOLD 보류(#324)·멀티 세트 동적가격(#19 MOOT).

### A. 즉시 착수 가능 (tracked 이슈)
- **#587** inventory + 전 서비스 public 엔드포인트 X-Internal-Token 403 갭 audit (AccountingClient 미fix·SlipClient는 #586 완료, 계약테스트 mock false-green). 규모 M.
- **#531** RestClient 실-HTTP 계약테스트 커버리지 갭 (H위험: inventory AccountingClient/SlipClient·accounting ProductClient NO-TEST. 패턴 `ProductAliasClientTest`). 규모 L.
- **Phase 11 AWS 실 cutover** — #660 머지·**회사 PC terraform v1.15.7 validate/plan 실증 완료(2026-06-29)**. 잔여 = 실 AWS 자격 `terraform apply` + 수동 18항목(CUTOVER.md M-1~18).

### B. 개발책임자 결정 대기 (정책 gate — 착수 전 결정)
- **OCR → GAS-direct 주문서 전송** — OCR 삭제(#658) 후속, 레거시 GAS 패턴 재사용.
- **결재 self-accept 정책** — 제안자=결정자 분리 강제 여부(신규 업무규칙).
- **슬립 soft-delete 복원 정책** — full vs 부분 restore.

### C. 후속/minor (비차단, 착수 전 코드 재확인)
- **세금계산서 FE 다운로드 wiring 점검** — BE 완비(엑셀/홈택스), FE 연결만 확인.
- **A2-G2 GROUP 비-admin 그룹명 lookup** — 현재 구조 라벨 폴백.
- **외부연동 실 API(NTS·KFTC DRY_RUN stub → 실)** — Phase 11 cutover 후.

## 완료된 큰 흐름 (이번 야간 자율 세션)
- ✅ A2 그룹웨어 결재 일원화 에픽(task#24) 완결 — A2-G1 BE + A2-G2 FE 표준 워크플로우(순차 듀얼리뷰·0수렴) 머지.
- ✅ OCR 메뉴 전수 삭제(#658).
- ✅ AWS 이식 준비(#660) 머지 + 회사 PC terraform validate/plan 실증 통과(2026-06-29).
- 순차 듀얼리뷰가 compile/unit 미검출 실결함 다수 적발(A2-G1 5·A2-G2 7·OCR 4·AWS 16+).

## ⚠️ 워크플로우 주의(박제)
- 매 단계 ScheduleWakeup 재자각·연속 mega턴 금지. 라운드마다 fix후 라이브QA(mock OFF)+스샷·각 라운드 즉시 독립 게시·fix후 0수렴 재리뷰·**듀얼리뷰 순차**(Opus 라운드=Opus fix / Codex 라운드=Codex fix)·단축금지.
- 마이그레이션 불변(V* in-place 금지, 신규 V만). page-code FE↔BE 일치·UUID/그룹ID 비노출·게이트웨이 단일 신원 권위(X-User-Role 미주입). 적용 마이그 불변(V75→V77 신규).
- Codex=`mcp__codex__codex`(리뷰 read-only / 수정 danger-full-access). PM 자동 머지: 0수렴+CI green 시 자율(개발책임자 '자율 계속' 승인). IaC는 terraform validate 게이트 추가.
- **IaC 머지는 실 terraform validate/plan 필수**(terraform CLI+AWS 계정). 집 PC 미설치 → 회사 PC 과제.
