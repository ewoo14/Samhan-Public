# 협업 코-에디팅 S3-2 — 견적(estimate) 메모 coedit

## 목적
S3-0 공용 토대 위에 견적(slip-service 동거) 상세 화면 '협업 메모' 실시간 동시편집 추가. slip/주문(S3-1) 패턴 1:1. 1차=메모 단일필드, 폼 셀=후속.

## 구현 범위 (최소 델타)
- **BE**(slip-service): `EstimateCollabController` coedit 3엔드포인트(`GET /coedit`·`POST /coedit/update`·`POST /coedit/awareness`) + `CollabCoeditService` 주입 + **견적 이중가드**(`@RequirePermission(estimates.list, VIEW/UPDATE)` + `EstimatePermissionGuard.checkView/checkEdit` + `X-User-Id`/`X-Is-System-Master`). estimateId=UUID(`existsById` 404). DTO 3종(`EstimateCoeditUpdate/AwarenessRequest`·`UpdatesResponse`). Flyway 0.
- **FE**(desktop): `EstimateCollaborationPanel` `CollaborativeTextField`('협업 메모', basePath=`/slips/estimates/{enc(id)}`) + 보조설명. `mock.ts` 견적 coedit 3핸들러. `EstimateCollaborationPanel.coedit.test.tsx`.

## 듀얼리뷰 (Opus ↔ Codex 0수렴)
- **Opus 5-agent**: FE 0·BE 0 BLOCKING·**DevOps BLOCKING 1**(ci.yml slip-it-core 가 `slip.estimate.it.collab.*` 미커버 → EstimateCollabIT 전체 **CI 미실행 false-green**)·**Design HIGH 2 반증**(page-code `estimates.list` FE/BE 일치 / presence 색상 FE `presenceColorFromUserId`=Java `hashCode`=BE `PresenceColor.fromUserId` 동일). Opus fix: ci.yml 필터 + IT 중복 stub 정리 + awareness null IT.
- **QA 메타 실증**: ci.yml 필터 fix 가 EstimateCollabIT 를 CI 실행시키자마자 awareness IT 의 seed(`estimate_no` 31자>VARCHAR(30)) 결함 즉시 적발 → seed 단축. *false-green 해소가 실결함을 잡음.*
- **Codex 라운드**: BE/FE 0수렴 + CI hardening(`EstimateCollabIT skipped=0 hard gate` + nightly `feat/coedit-*`·필터 정합).

## 계약 / 검증
- 게이트웨이 `/api/v1/slips/**` StripPrefix=2 → `/slips/estimates/{id}/collab/coedit` 정합(변경 0). `EstimateRevision` 직교(coedit=in-memory relay, snapshot 무생성). `ApiResponse` `$.data.updates`. UUID 비노출.
- **BE IT 18/18**(Testcontainers PG16) · **라이브 relay round-trip 12 PASS**(실 HTTP, slip-service 재빌드) · **vitest 6/6** · **CI green**(slip-it-core + skipped=0 hard gate). 데스크톱 패널 스샷 BLOCKED(로컬 Vite dev PWA 환경, 코드무관 — CI Desktop Playwright green). 증적 `docs/qa/coedit-s3-2-estimate/`.

## 후속
- **S3-3 회계전표 → S3-4 그룹웨어 결재 → S3-5 배차** → #16 종결 → #17 단가변동.
- 비블로킹(별도 트랙): 공용 `CollaborativeTextField` cosmetic(#fff·borderRadius·fontSize·helperText·presence 출처 통일) = 6패널 pre-existing.
