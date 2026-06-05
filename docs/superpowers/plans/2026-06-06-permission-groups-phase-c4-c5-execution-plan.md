# 권한그룹 Phase C4+C5 실행 계획 (전 서비스 인증 마이그레이션, 최고위험)

> 2026-06-06 작성. PM 준비(개발책임자집중 세션 실행용). **자율 머지 보류** — 사유 §5.
> 상위 spec: `2026-06-05-permission-groups-phase-c-fixed-role-removal-design.md` §4 C4/C5.

## 1. 왜 C4+C5 를 묶어서 보는가 (결합 분석)
- **C4** = `PermissionAspect.isMasterBypass` 를 `role=="MASTER"` → `is_system_master` 기반으로 전환.
- 그런데 `PermissionAspect`(shared/security)는 **요청 헤더(X-User-Role, X-User-Id)만** 본다(DB 조회 안 함). is_system_master 를 알려면:
  1. **JWT 클레임**에 `is_system_master` 추가 (`JwtTokenProvider`, shared) — 로그인(auth-service) 발급 시 account 의 systemMaster 그룹 멤버십으로 결정.
  2. **게이트웨이**가 클레임 → `X-Is-System-Master` 헤더 주입.
  3. **전 14서비스 HeaderAuthenticationFilter** 가 헤더 수용(+ SecurityContext 반영).
  4. **PermissionAspect.isMasterBypass** 가 새 헤더 사용.
- → C4 자체가 이미 JWT/헤더/전 서비스 필터 변경 = **C5 의 핵심 인프라**. 따라서 C4·C5 는 한 흐름의 auth 토큰 마이그레이션.

## 2. 안전 전제 (behavior-preserving 근거)
- C3a(#405)가 **role 변경 시 빌트인 role-group 자동 동기화** 완료 + V44 가 기존 계정 배속 → **`is_system_master 그룹(100) 멤버십` ⟺ `role=="MASTER"` 가 항상 성립**(불변식). 이 불변식이 C4 behavior-preserving 의 토대.
- 단, 이 불변식을 **런타임 전수 검증**(전 계정 role==MASTER ⟺ group100 active)하는 마이그레이션 가드(V47) 선행 필요.

## 3. 슬라이스 분해 (additive-first, 락아웃 최소화)
1. **C4-1 (additive, 저위험)**: JWT 에 `isSystemMaster` 클레임 **추가만**(아무도 소비 안 함) + 게이트웨이가 `X-Is-System-Master` 헤더 **주입만**. 전 서비스 필터는 **unknown 헤더 무시**(기존 동작 불변). 머지 후 토큰/헤더에 새 필드 존재만 확인. **behavior 변화 0**.
2. **C4-2 (flip, 고위험)**: `PermissionAspect.isMasterBypass` 를 `X-Is-System-Master==true` 로 전환(role=="MASTER" 폴백 **병행 유지** — `isMaster = headerSystemMaster || role=="MASTER"`). 둘 다 참이어야 정상(불변식). **전 서비스 실QA**: 각 서비스에서 MASTER 200 bypass + 비-MASTER 403 확인.
3. **C4-3 (정리)**: 불변식 실QA 안정 후 role=="MASTER" 폴백 제거.
4. **C5-1**: accounts.role 을 deprecated(읽기 전용) — 쓰기 경로(updateAccountRole)는 group sync 만, role 컬럼은 group 에서 파생/보존.
5. **C5-2 (최고위험)**: X-User-Role 헤더 제거 — 전 서비스 @PreAuthorize(hasRole) 잔존(INTERNAL 등)·HeaderAuthenticationFilter 정리. JWT role 클레임 제거. **전 서비스 동시 실QA + 롤백**.

## 4. 슬라이스별 검증 의무
- 각 슬라이스: 전 14서비스 빌드+JUnit + **Docker 풀스택 실QA**(게이트웨이 통한 MASTER/비-MASTER 매트릭스). C4-2/C5-2 는 특히 전 서비스 동시.
- 롤백: 각 슬라이스 revert 가능하게 additive→flip→cleanup 순. C5-2 전 DB 백업.
- [[feedback_enforcement_real_http_test]] · [[feedback_qa_docker_real_test]] · [[feedback_no_fake_data_ever]].

## 5. 🚨 자율 머지 보류 사유 (PM 판단, 2026-06-06 야간)
- spec §6: "C4/C5 는 전 서비스 인증 핵심 = 집중 세션 + 단계별 실QA 필수. 한 세션 강행 금지(락아웃 리스크)."
- 개발책임자 취침 중 → 전 서비스 인증 락아웃(예: isMasterBypass flip 버그 → 전 MASTER 차단, 또는 X-User-Role 제거 회귀 → 전 서비스 403) 발생 시 **대응 불가**.
- 기능 목표(동적 그룹/위임)는 Phase A/B 로 **이미 달성**. C4/C5 는 enum 물리제거/정리 = 긴급도 낮음.
- → **C4-1(additive)부터는 저위험이나, flip(C4-2)·C5 는 개발책임자 입회 집중 세션 권장.** 본 계획서로 즉시 착수 가능하게 준비 완료.

## 6. 개발책임자 결정 필요 항목
- (a) C4-1 additive 슬라이스를 야간 자율 진행할지(저위험) vs 전체 집중 세션 대기.
- (b) Phase C3 Option B(그룹 배속 UI 가 role 드롭다운 대체) 채택 여부 — 별개 UX 결정.
- (c) C5 시점(accounts.role 물리 제거)·롤백 윈도우.
