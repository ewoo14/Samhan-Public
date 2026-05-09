# 07-부록 / 01. FAQ — 자주 묻는 질문 (Stage 3)

> **대상 독자**: 모든 직원 (역할별 + 도메인별)
> **갱신 패턴**: 신규 질문 발견 시 본 문서 추가 — IT 관리자 / 개발팀 협의

---

## 색인

- [§1. 일반](#1-일반)
- [§2. 로그인 / 권한](#2-로그인--권한)
- [§3. 영업](#3-영업)
- [§4. 창고](#4-창고)
- [§5. 회계](#5-회계)
- [§6. 모바일](#6-모바일)
- [§7. arologis 배차](#7-arologis-배차)
- [§8. 데이터 / 백업](#8-데이터--백업)
- [§9. 운영 / IT 관리자](#9-운영--it-관리자)

---

## 1. 일반

**Q1. Samhan Public 시스템의 운영 시간은?**
A. 24시간 — desktop 어플 / 모바일 어플 항시 사용 가능. 단, 점심 (12~13시) / 마감 (17~18시) 시간대는 서버 부하 집중. 야간 정기 점검 (월 1회 02~04시 KST) 시 사전 공지.

**Q2. 회사 PC 외에서 접속 가능한가요?**
A. desktop 어플은 사내 PC + 회사 발급 노트북 만 (방화벽). 모바일 어플은 외부 가능 (LTE / 5G). PARTNER 계정은 외부 PWA 만.

**Q3. 신규 직원이 시스템을 처음 사용할 때 무엇부터 봐야 하나요?**
A. [00-시작하기/01. 로그인](../00-시작하기/01-로그인.md) → [02. 메인 화면](../00-시작하기/02-메인-화면.md) → [03. 역할별 권한](../00-시작하기/03-역할별-권한.md) 순서. 본인 ROLE 에 맞는 도메인 매뉴얼 추가.

**Q4. 매뉴얼이 영문으로 되어있는 경우?**
A. 모든 매뉴얼은 한국어 작성 의무 (`feedback_korean_commits.md` 가드). 영문 발견 시 본 저장소 Issue 또는 개발팀 보고.

**Q5. 시스템 명칭이 갑자기 변경되었습니다 ("삼한로지스" → "Samhan Public").**
A. 2026-05-09 회사 결정 — 정식 명칭은 **Samhan Public** (회사명 = (주)삼한공조시스템). 이전 명칭 (삼한로지스) 은 deprecated.

**Q6. 회사 대표 이름은?**
A. 김미선 (MASTER ROLE 보유). `feedback_user_title.md` 가드 — "대표" 호칭은 김미선 대표에게만 사용.

**Q7. 시스템 사용 교육은?**
A. IT 관리자 또는 개발팀 주관 — 신규 직원 입사 시 1회. 추가 교육은 부서장 요청.

**Q8. 매뉴얼에 오류가 있는 경우?**
A. 본 저장소 Issue 발행 또는 단톡방 보고. 신속히 PR 정정.

---

## 2. 로그인 / 권한

**Q9. 비밀번호를 분실했습니다.**
A. 자가 reset 미구현 (P0-2). IT 관리자에게 reset 요청 → 임시 비밀번호 발급 → 부서장 대면 확인. → [06-트러블슈팅/01. 로그인 실패](../06-트러블슈팅/01-로그인-실패.md) §1.

**Q10. 비밀번호를 5번 잘못 입력해도 잠기지 않습니다.**
A. 정상 — 현재 잠금 미구현 (P0-2). Phase 11 진입 전 P0-2 fix 시 활성.

**Q11. ROLE 이 잘못 부여되었습니다.**
A. MASTER (김미선) 만 ROLE 변경 가능 — `PATCH /users/employees/{id}/role`. 본인 → 부서장 → MASTER 요청 chain.

**Q12. 한 사용자가 SALES + ACCOUNTANT 겸직 가능?**
A. 1 user = 1 role. 두 계정 발급 또는 MANAGER 권한 (영업/회계 모두 결재 가능).

**Q13. 로그인 후 메뉴가 안 보입니다.**
A. JWT 캐시 — 로그아웃 → 재로그인. 미해결 시 → [06-트러블슈팅/02. 화면 표시 안됨](../06-트러블슈팅/02-화면-표시-안됨.md) §3.

**Q14. PARTNER / DRIVER 가 desktop 에 접속 가능?**
A. 불가. PARTNER = 거래처 PWA (`https://order.samhan-air.com`), DRIVER = 모바일 어플 / SMS share token URL.

---

## 3. 영업

**Q15. 거래처 4 탭 등록 화면이 안 보입니다.**
A. P0-6 미구현. 임시 우회 — backend API 직접 호출 또는 DB INSERT. → [01-영업/01. 거래처 등록](../01-영업/01-거래처-등록.md) §4.

**Q16. 슬립을 발행했는데 창고에 안 보입니다.**
A. SENT 단계까지 진행 필요 — DRAFT → SAVED → **SENT** → 창고 list 노출. → [01-영업/03. 슬립 발행](../01-영업/03-슬립-발행.md) §3.

**Q17. 슬립 발송 후 취소 가능?**
A. DRAFT / SAVED / SENT 단계만 cancel 가능. ACCEPTED 이후는 MANAGER 거절 (REJECTED).

**Q18. 견적서를 발행하고 싶습니다.**
A. Stage 4 미구현 안내 → [01-영업/06. 견적서](../01-영업/06-견적서.md) (P2-1 catalog 매핑).

**Q19. 거래처가 주문을 두 번 클릭했는데 슬립이 1건만 발행됐습니다.**
A. 정상 — idempotency-key 동작 (`POST /partner-orders/{id}/confirm` header). → [01-영업/05. 거래처 주문](../01-영업/05-거래처-주문.md) §4.

**Q20. 매출 마감 메뉴가 없습니다.**
A. P2-4 미구현 → [02-창고/04. 매출 마감](../02-창고/04-매출-마감.md) (P2-4 미구현 안내).

---

## 4. 창고

**Q21. 출고 시 재고 부족 (409 CONFLICT) 메시지가 떴습니다.**
A. 5가지 옵션 — 부분 출고 / 영업 분할 발행 / 입고 / 창고이동. → [02-창고/02. 출고 처리](../02-창고/02-출고-처리.md) §2-2.

**Q22. 입고 후 재고가 안 늘어납니다.**
A. inventory-service 다운 또는 inbound complete 미호출. IT 관리자 / [02-창고/01. 입고 처리](../02-창고/01-입고-처리.md) §6 참조.

**Q23. 입고 검수 UI 가 안 보입니다.**
A. P0-9 미구현 (Stage 2 신규 발견). 임시 우회 — DRAFT → DELIVERED → CONFIRMED 단순 경로 사용.

**Q24. 재고 실사를 어떻게 하나요?**
A. P2-6 미구현 → [02-창고/05. 재고 실사](../02-창고/05-재고-실사.md) (미구현 안내).

**Q25. 부분 출고 (분할 발행) 가능?**
A. P0 미구현 (slip-service §7.4). 임시 — 영업이 슬립을 분할 발행 (수동).

**Q26. 창고 간 이동 (transfer) 절차?**
A. INVENTORY ROLE 만 가능. 8 status workflow → [02-창고/03. 재고 조회](../02-창고/03-재고-조회.md) §7.

---

## 5. 회계

**Q27. 시산표 외 보고서가 안 보입니다.**
A. P0-1 미구현 — 17 보고서 중 14건 미구현. Phase 11 진입 전 P0-1 fix PR 4개 권고. 현재는 시산표 (`/accounting/balances`) + 분개 list 만.

**Q28. 세금계산서 발행은?**
A. 미구현 (P0-4). 외부 e-Tax (NTS Hometax) 직접 발행. → [06-트러블슈팅/03. 인쇄 안됨](../06-트러블슈팅/03-인쇄-안됨.md) §3.

**Q29. 슬립 confirm 후 자동 분개가 생성되었나요?**
A. 정상 — slip-service 가 confirm 시 accounting-service 호출 → JournalEntry 자동 생성. 분개 list 에서 확인.

**Q30. 한국 표준 계정과목 코드는?**
A. 100/200/300/400/500/800/900 체계 (`project_korean_accounting.md`). 시드 (`AccountSeeder` dev profile) 자동 생성.

**Q31. 월말 마감 자동화 가능?**
A. P2-3 미구현. 현재는 회계 직원이 수동 분개 + 시산표 검증.

---

## 6. 모바일

**Q32. 어플은 어디서 다운로드?**
A. 사내 배포 페이지 (IT 관리자 안내). Apple App Store / Google Play 미공개 (사내 어플).

**Q33. iOS / Android 모두 지원?**
A. 네 — RN Expo. iOS 14+ / Android 10+ 권장.

**Q34. GPS 권한 거부하면 사용 불가?**
A. INTERNAL DRIVER 는 GPS 필수. 거부 시 운영팀 escalation.

**Q35. 어플이 종료되어도 GPS 가 보고됩니까?**
A. Android 10+ "항상 허용" 권한 + 배터리 최적화 제외 시 가능. iOS 는 어플 종료 시 미작동.

**Q36. 핸드폰을 분실했습니다.**
A. IT 관리자 즉시 보고 — 본인 계정 disable. → [06-트러블슈팅/04. 모바일 접속 오류](../06-트러블슈팅/04-모바일-접속-오류.md) FAQ Q5.

**Q37. 영업 native 앱은?**
A. P1-4 미구현. 임시 — desktop 어플 또는 legacy v4 webview.

---

## 7. arologis 배차

**Q38. 카카오톡 배차 메시지를 시스템에 어떻게 입력하나요?**
A. 현재 P1-5 admin UI 미구현 — backend API 직접 호출 (`POST /admin/arologis/dispatches/parse-kakao` 미리보기 → `POST /admin/arologis/dispatches` 저장). → [05-arologis/01. 카카오톡 배차](../05-arologis/01-카카오톡-배차.md).

**Q39. 자동 매칭 0건이 나옵니다.**
A. DriverMatcher 가 placeholder. `application.yml` `app.arologis.matcher.active=mock` 설정 확인.

**Q40. 인성데이타 퀵프로그램 통합은 언제?**
A. W10-2 시점에 InsungQuickDriverMatcher 활성. 5만 프리랜서 풀 자동 매칭. 본 PR 시점에서는 아직 통합 미완료.

**Q41. 배차 담당자 (DISPATCH) ROLE 은?**
A. 별도 ROLE 미존재 — 현재 MANAGER 가 겸직 (`@PreAuthorize("hasAnyRole('MASTER','MANAGER')")`).

**Q42. EXTERNAL driver 에 어떻게 알림이 가나요?**
A. 본 시스템 push 채널 없음. 인성데이타 자체 / 카톡 / SMS 외부 채널.

---

## 8. 데이터 / 백업

**Q43. 데이터를 잘못 삭제했습니다.**
A. Soft Delete 만 — DB 에 row 보존. 개발팀 SQL `UPDATE ... SET deleted_at=NULL` 복원 가능.

**Q44. 백업은 어떻게 되나요?**
A. Phase 11 후 RDS auto backup (default 7일, 권장 30일). 본 PR 시점 dev 환경 = 미설정.

**Q45. 데이터를 Excel 로 export 가능?**
A. 일부 화면 ✅ (P1-6 일부 구현). 미구현 화면 = 백엔드 API 응답 직접 처리 (개발팀 우회).

**Q46. 회계 audit 보존 기간?**
A. 한국 일반기업회계기준 — 10년 의무. RDS 백업 + S3 Glacier 이원화 (Phase 11 후 P0-8 PR).

---

## 9. 운영 / IT 관리자

**Q47. 시스템 다운 시 누구에게 연락?**
A. 1차 IT 관리자 (사내 IT 부서). 30분 이상 미해결 시 개발팀 escalation.

**Q48. service 한 개만 다운된 경우 다른 메뉴 사용 가능?**
A. 가능 — service-per-DB 구조. 영향받은 service 의 메뉴만 차단. → [06-트러블슈팅/05. 기타](../06-트러블슈팅/05-기타.md) §1-4.

**Q49. Phase 11 AWS migration 일정?**
A. 본 PR 회고 시점 = 33주 로드맵의 Week 11. Phase 11 진입 전 P0 14개 슬라이스 fix 의무 — `missing-features-catalog.md` §6.

**Q50. 시스템 비용은 얼마?**
A. Phase 11 AWS Seoul 단일 환경 = 월 ₩405K (정상가). 자세한 내역: `project_phase11_aws.md`.

**Q51. 새 service 를 추가하고 싶습니다.**
A. 33주 로드맵의 Phase 별 추가. 즉시 추가 = 개발팀 협의 + Plan + 5-team agent dispatch (`feedback_multi_agent_team_pattern.md` 가드).

**Q52. 모니터링 도구는?**
A. Phase 11 후 CloudWatch + Health Check Lambda + RDS Performance Insights. dev 환경 = docker logs 만.

**Q53. 한국 시간대 (KST) 정확한가요?**
A. 모든 service `TZ=Asia/Seoul`. 차이 발견 시 개발팀 보고.

---

## 10. 관련 문서

- 누락 catalog: `docs/manual/inventory/missing-features-catalog.md`
- 도메인 용어 정의: [02. 용어집](02-용어집.md)
- 단축키: [03. 단축키](03-단축키.md)
- Phase 11 환경: `project_phase11_aws.md`
- 한국 회계: `project_korean_accounting.md`
- 본 매뉴얼 색인: [README](../README.md)
