# 종합견적서(estimate-app) 거래처·담당자 시트 → 사내 DB 전환 (G2)

> 에픽 = estimate-app 외부 Google Sheets 잔여 의존 제거([[project_sheets_to_db_full_migration]]). 본 슬라이스 = G2(거래처/담당자).
> 워크플로우 = Opus 4.8 계획/조기PR → Codex 개발 → Opus 5-agent(리뷰+fix+Docker 실QA 스크린샷) → Codex 5-agent(동) → 0에러까지 반복 → PM 종합+머지. Fable5 영구 제외.
> 작성: 2026-06-16 (Opus, step1).

## 1. 배경 / 목표

estimate-app(종합견적서, `clients/web/estimate-app/`)는 거래처(`getCustomers_()` → 시트 '거래처' 탭)와 담당자(`getManagers_()` → 시트 '담당자' 탭)를 여전히 Google Sheets 에서 읽는다. 이를 **사내 기존 DB** 로 전환한다.

**개발책임자 확정 (2026-06-16):**
- **거래처 = 우리 거래처 DB(partner-service)** 에서 가져온다.
- **담당자 = 우리 행정직원(user-service Employee, 대표실/행정팀/회계팀 등 [[project_arologis_desktop_backoffice]])** 을 의미한다.
- **"따로 추가는 무의미"** — 신규 데이터/엔티티 신설 없음. **기존 데이터를 그대로 읽는** 얇은 internal read 엔드포인트만 추가한다.

## 2. 스코프

### G2a — 거래처 → partner-service
estimate-app 의 `getCustomers_()` 시트 읽기를 partner-service internal 호출로 교체.

### G2b — 담당자 → user-service 행정직원
estimate-app 의 `getManagers_()` 시트 읽기를 user-service internal 호출로 교체.

## 3. 데이터 매핑 (정찰 확정)

### 거래처: 시트 컬럼 → 실제 소비 여부 → partner-service 필드
`getCustomers_()` 는 11컬럼을 읽지만 **다운스트림 실소비 = 8개**. 나머지는 vestigial → 마이그 제외.

| 시트 컬럼 | estimate-app 필드 | 실소비? | partner-service `Partner` 필드 | 비고 |
|---|---|---|---|---|
| 거래처코드 | `code` | ✅ | `partnerCode` | 목록/슬립 |
| 거래처명 | `name` | ✅ | `name` | |
| 사업자등록번호 | `bizno` | ✅ | `bizNo` | **dc-config 매칭 키(digits)** |
| 대표자명 | `rep` | ✅ | `representative` | 슬립 양식 |
| 주소 | `addr` | ✅ | `address` | 슬립 양식 |
| 전화번호 | `tel` | ✅ | `phone` | 슬립 양식 |
| 그룹 | `group` | ✅ | `partnerGroup1` (plain String) | 목록 필터 |
| 특이사항 | `note` | ✅ | `note` (TEXT) | |
| 싱글 할인 | `singleDiscount` | ❌ **vestigial** | — | 읽지만 미사용. dc-config 가 할인 단일 진실원 → **제외** |
| 담당자명(거래처시트) | `manager` | ❌ **discarded** | — | getCustomerDataAsync 가 버림(슬립 manager 는 partner-order 출처) → **제외** |
| 담당자연락처(거래처시트) | `managerTel` | ❌ **discarded** | — | **제외** |

→ partner-service `Partner` 엔티티가 8개 전부 보유(신규 컬럼 0). 할인은 기존대로 dc-config-service(`/internal/partner-dc-configs`, bizno digits 키)에서 별도 취득 — **본 슬라이스 무변경**.

### 담당자: 행정직원(user-service Employee)
- `담당자명` = `Employee.fullName`
- `담당자코드`(legacy empCd `EMP-0001~0019`) = `Employee.ecountCode` (V8/MIG-6, nullable)
- **담당자 = 우리 사원(영업/견적 담당)** — partner contacts 아님(정찰 확정). slip-bridge 가 `employeeCode: head.EMP_CD` → slip-service `PublishFromEstimateRequest.employeeCode`(자유문자열 max50, `Slip.requesterId` 스냅샷, **FK 검증 없음**).

## 4. BE 변경 (Codex 구현)

### partner-service (신규 read 엔드포인트 1, 신규 데이터 0)
- **`GET /internal/partners/list`** (`PartnerInternalController`): ACTIVE 거래처 목록. params `q`(선택, name/bizNo/partnerCode 부분일치), `limit`(기본/상한 5000), `page`(0-base, 기본 0). 인증 = X-Internal-Token(ROLE_MASTER, 기존 패턴).
- **신규 DTO `PartnerDirectoryResponse`** {partnerId(UUID), partnerCode, name, bizNo, representative, address, phone, group, note}. ⚠️ 기존 `PartnerInternalResponse`(slip/accounting 사용) **확장 금지** — 별도 DTO 로 blast radius 0.
- 서비스/repo 메서드(기존 `findAllByStatus`/`searchAdmin` 재사용 가능). UUID 비공개 가드는 internal 응답이라 무관(사용자 화면 직접 노출 X, estimate-app 은 bizno/partnerCode 로 식별).
- IT: 인증 401/403, 목록 shape, q 필터, ACTIVE 한정. (MockRestServiceServer 아닌 web-slice/Testcontainers.)

### user-service (행정직원 목록 read projection)
- 기존 `GET /internal/users/search` 는 **빈 q → 빈 배열**(groupware picker 전용) + **ecountCode 미노출** → 그대로 부족.
- **`GET /internal/users/employees`** (`InternalUserController`) 신규: 활성 행정직원 목록. params `q`(선택), `limit`(기본 500 상한). 응답 `List<InternalEmployeeDirectoryResponse>` {userId(UUID), fullName, ecountCode(nullable), departmentName}. 인증 X-Internal-Token(ROLE_MASTER).
  - 기존 데이터(Employee) read projection 일 뿐 — **신규 데이터/엔티티 없음**(개발책임자 "따로 추가 무의미" 부합).
  - 대안(리뷰 검토): 기존 `/search` 에 ecountCode 1필드 추가 + blank-q 정책 분기. 단 groupware 계약 보존 위해 별도 엔드포인트 권장.
- IT: 인증, 목록 shape, ecountCode 포함.

## 5. FE 변경 — estimate-app (Codex 구현)

- **신규 `lib/directory.js`** (db-catalog.js 패턴 미러): axios + X-Internal-Token + base URL(SAMHAN_PARTNER_SERVICE_URL/USER_SERVICE_URL — `.env.example` 기준).
  - `fetchPartners()` → GET partner-service `/internal/partners/list` → 레거시 `getCustomers_()` shape {code,name,bizno,rep,addr,tel,group,note} 매핑.
  - `fetchManagers()` → GET user-service `/internal/users/employees` → {담당자명: fullName, 담당자코드: ecountCode||'', manager: fullName, empCd: ecountCode||''} 매핑.
- **code.js 배선**: `getCustomers_()`/`getManagers_()` 본문을 directory 호출로 교체(함수명·RPC 계약 보존). `getCustomerDataAsync()` 의 dc-config bizno 매칭·캐시(CUS_V6/MGR_V1 TTL)·`searchManagersByName_`/`findManagerByNameExact_` 그대로 동작(취득 리스트 위에서).
- **시트 의존 제거**: `sheetsToPreload` 에서 '거래처'/'담당자' 제거(해당 탭 더 이상 read 안 함).
- **graceful fallback**: 서비스 도달 실패 시 log + 빈 리스트(크래시 금지, 부트스트랩 계속). **시트 fallback 안 함**(목표=시트 의존 제거).

## 6. 위험 / 결정

1. **ecountCode 연속성**: 행정직원 ecountCode 가 legacy 담당자코드(EMP-0001~0019)와 포맷/값 상이 가능(eCount 임포터 채움, 정적 시드 없음). slip employeeCode 는 자유문자열 스냅샷 → **계약 비파손**. QA 에서 드롭다운 실제 채워짐 + 선택 코드 검증; 갭 시 개발책임자 보고(비차단).
2. **담당자 범위**: 행정직원 전체(활성) — legacy 큐레이션 ~19 보다 넓을 수 있음. 검색 가능. 필요 시 role/dept 필터는 후속(개발책임자 확인).
3. **거래처 목록 크기**: partner-service page/limit(5000 단위)로 끝 페이지까지 조회. parity = 전체 fetch + 클라이언트 필터(레거시 동일).
4. **group**: `partnerGroup1` plain String → 직매핑(enum 변환 불요).

## 7. QA 계획 (Docker 실서버 — 매 리뷰 라운드)

스택: estimate-app(:5183) + partner-service(:8095) + user-service(:8083) + dc-config(:8089) + product-service(:8084). 게이트웨이 우회(internal-token 직호출). ⚠️ partner-service(:8095, 거래처 directory)와 dc-config(:8089, 할인)는 **별개 서비스** — estimate-app 의 `PARTNER_SERVICE_URL` 은 레거시상 dc-config 지칭이므로 directory 는 `SAMHAN_PARTNER_SERVICE_URL`(실 partner-service) 사용.
- 거래처 드롭다운이 **partner-service DB** 에서 채워짐(시트 아님) — 실화면 캡처.
- 담당자 드롭다운이 **행정직원**에서 채워짐 — 실화면 캡처.
- dc-config bizno 매칭 유지(거래처 선택 → 할인 적용) — 실 견적 라인 캡처.
- '거래처'/'담당자' 시트 read 미발생(로그 확인).
- 가짜 금지([[feedback_no_fake_data_ever]]) — 실 시드·실 응답·실 화면.

## 8. 테스트 계획
- BE: partner-service `/internal/partners/list` IT + user-service `/internal/users/employees` IT(인증·shape·필터).
- FE: estimate-app jest — directory 매핑 + fallback(서비스 다운 시 빈 리스트).
- 변경 모듈 전체 test 완주 후 push([[feedback_changed_module_full_test_before_push]]).

## 9. 워크플로우 (고정)
Opus 계획/조기PR(본 spec) → Codex BE+FE 개발 + 개발사항 PR게시 → Opus 5-agent(리뷰+Opus직접fix+QA Docker 스크린샷 라운드게시) → Codex 5-agent(동, Codex직접fix) → 다음 리뷰어 0에러까지 Opus↔Codex 반복 → PM 종합+CI green+머지. 머지 게이트=error0·skip0+CI green+라운드별 실QA 스크린샷.
