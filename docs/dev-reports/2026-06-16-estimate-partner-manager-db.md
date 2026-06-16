# 종합견적서(estimate-app) 거래처·담당자 시트 → 사내 DB 전환 (G2)

> 슬라이스 = 에픽 "estimate-app 외부 시트 잔여 제거(G1+G2)" 의 G2. PR #491. 2026-06-16.
> 워크플로우 = Opus 4.8 계획/조기PR → Codex 개발 → Opus 5-agent 리뷰+fix+실QA → Codex 5-agent 교차+fix+실QA → 수렴(Opus) → PM 머지. (Opus↔Codex 2모델, Fable5 영구 제외.)

## 1. 목표
estimate-app 이 아직 Google Sheets('거래처'/'담당자' 탭)에서 읽던 거래처·담당자를 **사내 기존 DB** 로 전환.

**개발책임자 확정(2026-06-16):** 거래처 = 우리 거래처 DB(partner-service), 담당자 = 우리 행정직원(user-service Employee). "따로 추가는 무의미" — 신규 데이터/엔티티 없이 기존 데이터를 읽는 얇은 internal read 엔드포인트만.

## 2. 변경 요약

### BE — partner-service (거래처)
- `PartnerDirectoryResponse`(신규 DTO, 8필드: partnerId/partnerCode/name/bizNo/representative/address/phone/group/note) + `from(Partner)`.
- `GET /internal/partners/list?q=&limit=&page=` (`PartnerInternalController`) — ACTIVE 거래처, q=partnerCode/name/bizNo 부분일치, limit cap 5000, page 0-base. ROLE_MASTER/X-Internal-Token.
- `PartnerService.listDirectory(q,limit,page)` + `PartnerRepository.searchDirectory`(JPQL, `CAST(:q AS string)` null-safe, partnerCode asc).
- 기존 `PartnerInternalResponse` 무변경(slip/accounting 계약 보존).

### BE — user-service (담당자=행정직원)
- `InternalEmployeeDirectoryResponse`(신규 DTO: userId/fullName/ecountCode/departmentName).
- `GET /internal/users/employees?q=&limit=` (`InternalUserController`) — 활성 직원(`isDeleted=false AND terminationDate IS NULL`), blank q=전체, ecountCode+부서. ROLE_MASTER.
- `EmployeeRepository.searchEmployeeDirectory`(LEFT JOIN FETCH department).

### FE — estimate-app
- `lib/directory.js`(신규) — axios + X-Internal-Token, `fetchPartners`(페이지 순회, PARTNER_MAX_PAGES=20)/`fetchManagers` → legacy getter shape 매핑, HTTP 실패→빈배열 graceful. base URL = `SAMHAN_PARTNER_SERVICE_URL`(실 partner-service :8095)·`USER_SERVICE_URL`.
- `code.js` — `preloadDirectoryCache_()`(CUS_V6/MGR_V1 캐시 prefetch, 빈결과 비캐싱) 를 bootstrap·getCustomerDataAsync·sendOrderFromUi·initDcConfigFromNotion 에서 호출. `getCustomers_`/`getManagers_` 는 동기 캐시 read(계약 보존). `sheetsToPreload` 에서 '거래처'/'담당자' 제거.
- `.env.example`·`infrastructure/render/render.yaml` 에 `SAMHAN_PARTNER_SERVICE_URL`/`USER_SERVICE_URL` 정합.

## 3. 다모델 리뷰 경위
- **Opus 5-agent(라운드 A)**: 보안·JPQL CLEAN. **P1**(getCustomers_/getManagers_ 캐시 read 전용화로 CUS_V6 TTL 10분 만료 시 등록 거래처 "미등록거래처" 회귀, data·FE 독립 적발) + **P2 4**(죽은 custRec.manager 폴백 / limit 기본 2000≠5000 / searchEmployeeDirectory 활성=terminationDate 미제외 / **directory.js 가 PARTNER_SERVICE_URL=dc-config:8089 재사용→배포 시 404**[Docker 실QA 단독 적발]). 전부 Opus 직접 fix.
- **Codex 5-agent(라운드 B)**: **P2 2**(거래처 7천+건 limit 5000 단일 fetch 절단→page 순회 / render.yaml env 누락) + Opus fix 전부 CLEAN. Codex 직접 fix.
- **수렴(라운드 C, Opus 2-agent)**: 둘 다 CONVERGED/CLEAN — 0 P1/P2. 머지 게이트 충족.

## 4. QA (Docker 실서버, 가짜 0)
- BE 실 curl: `/internal/partners/list` 실 거래처(UTF-8 정상·Korean q 200·토큰누락 403), `/internal/users/employees` 행정직원(부서)·403.
- 페이지네이션 실증: page=0 첫 `000011111111` ≠ page=1 첫 `0004`, estimate-app RPC 총 **7034건**(>5000 cap 해소).
- estimate-app RPC `getCustomerDataAsync` → 실 거래처 + dc-config bizno 매칭 142건 보존.
- 실 화면 캡처: `docs/qa/estimate-partner-manager-db/screenshots/` (동양/한울 → partner-service DB 실 거래처 자동완성).

## 5. 비차단 후속
1. **ecountCode 커버리지**: DEV-SEED 직원 ecountCode=null(실 eCount 임포트 시 채워짐). slip employeeCode 자유문자열이라 비차단.
2. **담당자 검색 FE 미배선**: estimate-app UI 가 담당자 검색 RPC 현재 미호출(엔드포인트는 정확 인프라, FE 배선 후속).
3. **transient mid-loop 부분 truncation**: 페이지 2+ 에서 일시 HTTP 오류 시 partial 리스트 10분 캐싱(허용 tradeoff, 현 규모 저위험).
4. **거래처 볼륨**: MAX_PAGES 20 × 5000 = 10만 cap(초과 시 로그). 현 7034건 안전.

## 6. 핵심 파일
```
services/partner-service/.../dto/PartnerDirectoryResponse.java (신규)
services/partner-service/.../controller/PartnerInternalController.java
services/partner-service/.../service/PartnerService.java
services/partner-service/.../repository/PartnerRepository.java
services/partner-service/.../it/PartnerInternalControllerIT.java
services/user-service/.../web/dto/InternalEmployeeDirectoryResponse.java (신규)
services/user-service/.../web/InternalUserController.java
services/user-service/.../repository/EmployeeRepository.java
services/user-service/.../it/InternalUserSearchControllerIT.java
clients/web/estimate-app/lib/directory.js (신규)
clients/web/estimate-app/lib/code.js
clients/web/estimate-app/test/directory.test.js + calc-fidelity.test.js
clients/web/estimate-app/scripts/qa-g2-partner-capture.mjs (신규, QA 재현)
clients/web/estimate-app/.env.example + infrastructure/render/render.yaml
docs/superpowers/specs/2026-06-16-estimate-partner-manager-db.md
docs/qa/estimate-partner-manager-db/screenshots/01·02
```
