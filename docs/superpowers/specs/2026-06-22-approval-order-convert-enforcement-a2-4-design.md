# A2-4 — 주문(PARTNER_ORDER) 출고전환 결재 enforcement 설계

> A2 결재 에픽 확장(타 전표 순차, 개발책임자 "자율 선택"). 5 후보 병렬 정찰 결과 **주문이 B-게이트 최적합**(GOOD·HIGH·convert 단일 액션). 슬립(출고/입고) 패턴을 partner-order-service 의 출고전환 액션에 미러.
>
> 선행: [A2-3 spec](2026-06-22-approval-inbound-enforcement-a2-3-design.md)(authorize generic·ApprovalLineAuthorizeClient·게이트 패턴). [project_partner_order_status_model], [project_order_slip_conversion].

## 목표

거래처 주문(`PARTNER_ORDER`)의 **출고전환(convert-to-slip)** 액션을, 결재라인 설정의 PARTNER_ORDER 승인자(그룹∪개인)만 수행하게 한다. 동적 config 조회·**opt-in**(미설정 무중단)·system bypass·B 게이트(전환 로직 무변경 + 권한 게이트만). 회계/견적/배차/그룹웨어는 모델 불명확이라 후속.

## 선택 근거 (5 후보 정찰)
| 후보 | B게이트 | 사유 |
|---|---|---|
| **주문** | **GOOD** | convert-to-slip = 단일 비즈니스 액션(재고 결과·거래처 finality), 슬립 패턴 정확 미러. partner-order-service 에 loadBalanced RestClient·X-Internal-Token 인프라 존재 |
| 회계 | POOR | 작성자=게시자(ACCOUNTANT) 역할분리 약함, 월말마감 별도 |
| 견적 | POOR | send/accept = 거래처-facing 외부 응답, 명시체인 |
| 배차 | POOR | 복잡 상태머신·arologis 외부 회신 |
| 그룹웨어 결재 | — | 이미 자체 결재선(EXPLICIT) |

## 설계 (슬립 미러)

### 데이터 — V64 마이그레이션 (PARTNER_ORDER config 시드)
approval_line_config(action_key) 재사용. **신규 documentType=PARTNER_ORDER 2역할 시드**(fresh seed):
- 작성자(CREATOR, seq0, action_key=NULL)
- 승인자(GROUP, seq1, **action_key=`PARTNER_ORDER_CONVERT`**)
- action_key 직접·WHERE NOT EXISTS 멱등. V61~V63 불변. (슬립은 출고인/검수인 2승인역할이나 주문은 convert 단일 액션→승인자 1역할.)

### partner-order-service — convert 게이트
**ApprovalLineAuthorizeClient**(신규, slip `ApprovalLineAuthorizeClient` 미러 — loadBalanced RestClient `http://auth-service` + X-Internal-Token, 운영 생성자 **@Autowired** 명시 [[restclient-contract-test-false-green]] DI 가드 교훈):
- `authorize(documentType, actionKey, userId) → {configured, allowed}`(auth `/auth/internal/approval-line/authorize` generic, 변경 없음).
- `PartnerOrderConvertService.convert(...)`(개별) + 병합전환: **전환 로직 전**에 `if (isRealUser(actorUuid)) authorize("PARTNER_ORDER","PARTNER_ORDER_CONVERT", actorUuid)` → `configured && !allowed` 면 `BusinessException(FORBIDDEN, "주문 출고전환 권한이 없습니다 — 승인자 결재자(그룹/개인)만 전환할 수 있습니다")`. opt-in(configured=false→통과)·system bypass(actorUuid null/'system'/non-UUID→skip). 개별·병합 둘 다 동일 action_key.
- 컨트롤러 `@RequirePermission(sales.partner-order.convert)` 유지(opt-in 베이스).
- partner-order-service 에 InternalAuthProperties·loadBalancedRestClientBuilder 존재 확인(RestClientConfig). client TestConfiguration @MockBean 격리 + MockRestServiceServer 계약테스트 + **DI 가드 테스트**(라이브 빈 생성).

### FE — 주문 전표종류
`api/approvalLineConfigApi.ts` DOC_TYPES 에 `{ value:'PARTNER_ORDER', label:'주문' }` + mock `_mockApprovalLineConfigRoles` 에 PARTNER_ORDER 2행(작성자/승인자). 결재라인 설정 메뉴에 주문 노출(칩/순서/라벨 generic).

## opt-in 무중단
PARTNER_ORDER 승인자 미지정(시드 기본) → authorize `{configured:false}` → 게이트 skip → 기존 sales.partner-order.convert 권한자가 그대로 전환. 지정 시 그 그룹∪개인만.

## 테스트
- **auth**: V64 fresh probe(PARTNER_ORDER 2역할·action_key PARTNER_ORDER_CONVERT). ServiceTest PARTNER_ORDER 케이스 1건(generic 박제).
- **partner-order IT**(실HTTP·client @MockBean 격리 + ClientTest 계약 + DI 가드): convert 비결재자 403·결재자 200·opt-in 200·병합전환 동일. **기존 convert IT 회귀 0**(client @MockBean stub configured=false 기본). 외부 client 공유 @MockBean.
- **🐳 라이브 QA**: 주문 승인자 지정→그 사용자 convert 200·비결재 403·opt-in 200. 슬립 출고/입고 무회귀.

## 범위 밖
- 회계/견적/배차/그룹웨어(후속 개발책임자 지정), 주문 confirm(DRAFT→DRAFT 약한 액션)·hold·cancel 게이트, 결재 알림.

## 워크플로우
Codex 구현 → 🔵Opus 5-agent+QA(순차) → 🟣Codex 5-agent+QA(cross-check) → 라운드 fix(Opus=Opus직접/Codex=Codex) → **양쪽 0 수렴까지**(병렬 금지) → 머지. 매 라운드 라이브 캡처. 변경 모듈(auth+partner-order) **전체 test 완주 후 push**.
