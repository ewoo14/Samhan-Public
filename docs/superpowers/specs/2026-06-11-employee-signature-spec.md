# 사원 서명 등록 — 결재란 스탬프 (대기 슬라이스, 정찰 완료 박제)

> 상태: **대기** (2026-06-11 새벽 — 개발책임자 결정으로 품목관리 고도화 슬라이스 선행). 정찰 완료분 박제 — 착수 시 본 문서 기준.
> 근거: [[project_slip_shipout_print_form]] 슬라이스 C, #458 결재란 placeholder 이월.

## 정찰 결론 (2026-06-11 05:50)

| 항목 | 사실 |
|---|---|
| Employee 엔티티 | user-service `domain/Employee.java` — 서명 컬럼 없음. 최신 Flyway **V9**. Account(auth-service) 와 1:1 (accountId nullable) |
| 결재란 placeholder | `DispatchView.tsx` RoleCell(59-82행) — `signaturePng` prop 기성(undefined), 서명(위)+이름(아래) 구조. 5칸: 담당부서/작성자(ownerFullName)/출고인(dispatcher)/검수인(inspector)/결제예정일. `OutboundView.tsx` 푸터 출고인 [인] placeholder. 인수자 서명(slip.signaturePng)은 별개 기성 |
| 사원등록 화면 | `routes/admin/UsersPage.tsx` (/admin/users) — 서명 필드 없음. 권한 admin.users (MANAGER/MASTER) |
| SignaturePad | design-system `SignaturePad.tsx` 기성 (canvas, toDataURL/clear/isEmpty — 모바일 인수자 서명 검증됨). desktop 직접 import 가능 |
| #459 패턴 | SupplierProfile 인감 (BYTEA + PNG magic + SHA-256 + 200KB + PUT/DELETE + deny IT) — **100% 복제 가능** |
| 인쇄 배선 갭 | SlipDetailResponse 에 dispatcher/inspector 서명 PNG 미포함 — slip-service → user-service 서명 조회 배선 필요 |

## 구현 계획 (착수 시)

1. **BE user-service**: V10 `employees.signature_png BYTEA + signature_hash VARCHAR(64)` + Employee.registerSignature/clearSignature 도메인 메서드 + `PUT·DELETE /admin/users/{id}/signature` (admin.users UPDATE, #459 decodePngAndValidate 패턴 복제) + deny IT.
2. **BE slip-service**: SlipDetailResponse 에 `dispatcherSignaturePng`/`inspectorSignaturePng` (user-service 내부 조회 배선 — ownerFullName 기존 lookup 경로 확장. 작성자 칸도 ownerSignaturePng 포함 검토).
3. **FE**: UsersPage 편집 모달에 SignaturePad 섹션 (서명 그리기 → dataURL → SHA-256 → PUT / 삭제) + DispatchView RoleCell signaturePng 배선 + OutboundView 출고인 [인] 대체 + mock 동형 + TC.
4. **QA**: 실 등록 → 출고전표 인쇄 결재란 스탬프 실반영 캡처 + 권한 deny.

비스코프: 본인 서명 self-service (admin 외 등록 주체 확장) — 정책 확인 필요 시 개발책임자.
