# 미해결/후속/백로그 전수 재확인 (2026-06-19)

> 개발책임자 요청: 이전 중단·백로그·후속(미해결) 전체 재확인. 메모리(30+)·코드(services+clients)·docs·열린 PR/Issue 4소스 병렬 스윕 + PM 검증.
>
> 🚨 **검증 원칙**: 세션 내내 docs/스펙이 실제 구현보다 뒤처짐(F5·1번·3번·품목고도화·슬4 모두 기달성/moot). 아래는 **stale 제거 + 검증한 실제 잔여**만.

## 0. 상태 요약
- **열린 PR = 0, 열린 Issue = 0** (clean).
- **2026-06-09 종합견적서-audit P0 블로커 = 해소됨(검증)**: snapshot 엔드포인트(QuoteSnapshotController+V36)·전표발행(InternalSlipPublishController /from-estimate + slip-bridge.js) 실재. 비즈로직=F1~F4/슬3-1 정정. 시트직접=#507 db+전면치환 진행. **현 블로커 아님** — 그 audit은 수식빌더/estimate-app 인식 전 문서.

---

## A. 정책 gate — 개발책임자 결정 대기 (구현 전 필요)
| 항목 | 출처 | 내용 |
|---|---|---|
| 멀티 세트 동적가격(#19) | basic-vs-estimate | 상업멀티 구성품 합산 동적화=견적금액 변동 → go/no-go |
| 결재 self-accept 정책 | global-collab slip §5 | 제안자=결정자 분리 강제 여부(신규 업무규칙) |
| 슬립 soft-delete 복원 정책 | partner-order restore | full vs 부분 restore 선택지 |
| 전표/주문 ON_HOLD(보류) 추가 | partner-order-status | 보류 상태+리스트 필터(별도 슬라이스, 업무규칙) |

## B. 후속 슬라이스 — 설계됨·미구현 (스케줄 가능, 비차단)
| 항목 | 출처 | 규모 |
|---|---|---|
| §7 collab presence(동시 접속자+색상) | global-collab-epic | M |
| 재고조회 모달(Phase 2.6d 후속) | inventory-lookup-modal | M |
| estimate-app 사양맵(SPEC_DETAIL_MAP) 시트→DB 승격 | estimate-spec-data-sources | M |
| 메뉴 5대분류 + 부서게이트/권한필터 | item-exposure-menu-5cat | L |
| 담당자 검색 estimate-app FE 배선(인프라 완비) | estimate-partner-manager-db | S |
| 배차 상세 query key 통일·MODIFICATION 상태 필터 확장 | dispatch-modification | S |
| 데스크톱 주문/전표 구성품 사양 표시(BE 재사용) | estimate-spec-data-sources | S |
| 회계 메뉴 갭 13건(미리보기 표준화 후속) | global-collab-epic | L |

## C. 외부 연동 — Phase 11 / 외부 의존 (stub 존재, 실 연동 대기)
| 항목 | 파일 | 상태/결정 |
|---|---|---|
| **전자세금계산서** | accounting ETaxClientImpl | ✅ **개발책임자 2026-06-19: ASP/NTS 직접발급 폐기 → 엑셀 다운로드(GAS 방식). BE(TaxInvoiceBatch/HometaxExportService) 완비.** NTS stub(ETaxClient) 불요·정리 대상. FE 다운로드 wiring만 점검 |
| 법인계좌 입출금 | accounting KftcClientImpl(DRY_RUN) | 리서치 진행: 오픈뱅킹+CODEF 하이브리드, **자격/단가/한도 견적 미확정**(Phase 11) |
| 영수증 OCR | slip ReceiptOcrClientImpl | Clova OCR 실 API 미구현(Phase 11) |
| SMTP/SMS 발송 | auth NotificationStub | dev console만 → 운영 전 실발송(Phase A6) |
| 알리고 주소록 | notification MockAligoAddressBookClient | dryRun → 실 API spec 확정 후(PR-F2) |

## D. 운영 전 필수 (보안/cutover)
| 항목 | 출처 | 내용 |
|---|---|---|
| 이카운트 API 키 회전 | sp-08-legacy-gas-parity | git 히스토리 잔존 자격 회전(보안 필수) |
| NotificationStub 제거 | auth-service | 운영 전 삭제(평문 token 로그 노출) |
| Phase 11 prod cutover | kst-timezone / phase11-aws | prod compose TZ/RDS 세션/기존데이터 KST + AWS 인프라 |

## E. 테스트/인프라 부채 (비차단)
| 항목 | 출처 |
|---|---|
| 데스크톱 vitest CI 정식 연동(orderNo.test.ts mock gate 우회) | print-preview-standardization |
| admin-hr `/admin/users` PermissionGuard KNOWN-GAP(testIgnore 격리) | admin-hr-guard.spec |
| ERV joinCols NUMBER 타입 잠재버그(현 데이터 0건) | spec-aware-input |
| arologis-desktop 기사 수동 CRUD stub(BE 미구현, UI 안내만) | arologis.ts |
| mobile-staff 견적 사진 첨부 stub(Phase 12) | SalesEstimatePhotoScreen |

## F. 재확인 필요 (audit-era, 해소 추정 — 착수 전 검증)
| 항목 | 출처 | 비고 |
|---|---|---|
| design-system @font-face(Pretendard) 누락 | 2026-05-19 fe-audit | 5주 경과, 해소 추정 — 검증 후 판단 |
| arologis-mobile Pretendard OTF 누락 | 2026-05-19 fe-audit | 동상 |

---

## 진행 중 (in-flight, 2026-06-19)
- **삼한이 마스코트**: 자산 최적화 완료(docs/character → design-system samhani.webp 70KB+png), 공용 MascotLoader/EmptyState 컴포넌트+적용 진행 예정(개발책임자 '데스크톱+웹 전체').
- **전자세금계산서 엑셀**: BE 완비, FE 다운로드 wiring 점검 진행 가능.

## 권고 (PM)
1. **정책 gate(A) 4건** = 개발책임자 결정 시 즉시 진행 가능.
2. **후속 슬라이스(B)** = 비차단, 우선순위 지정 대기.
3. **외부연동(C)** = 세금계산서 엑셀 확정으로 ASP/NTS 불요, 법인계좌만 견적 대기.
4. **운영 전(D)** = Phase 11 cutover 시 일괄(키 회전은 별도 즉시 가능).
