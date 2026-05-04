# UX Flow — Signature Slice C

본 문서는 4 시나리오 (기사 / 인수자 / 관리자 / 인쇄) 별 step-by-step UX flow 입니다.

---

## 시나리오 1 — 배송기사: 슬립 리스트 → 서명 캡처 → 인수자 공유

### 1.1 사전 조건
- 기사 (김기사) 가 SMS 로 받은 배치 링크 (`/d/{token}`) 진입한 상태
- 배치 토큰 유효 (배송일 +1일 만료 미도래)

### 1.2 step-by-step

| step | 사용자 동작 | 시스템 동작 | 화면 |
| --- | --- | --- | --- |
| 1 | 배치 페이지에서 한국전력 카드 [상세보기 →] tap | `/d/{token}/s/2026-05-05-1` 라우팅 | 배치 페이지 (Slice B 완료) |
| 2 | (자동) | `signature.js` dynamic import + 슬립 단건 fetch | 로딩 spinner ~300ms |
| 3 | (자동) | 슬립 read-only 카드 + 라인 + 합계 + 빈 canvas + 입력 input 표시 | 서명 페이지 (mock 01) |
| 4 | "인수자 정함" input 에 "김인수" 입력 | onChange — [서명 완료] 버튼 disabled 유지 (canvas 빈 상태) | 서명 페이지 |
| 5 | canvas 영역 손가락으로 서명 시작 | touchstart → 점선 placeholder hide, stroke 그리기 | 서명 페이지 |
| 6 | 서명 완료 (touchend) | `onChange(false)` → wrap 의 `is-empty` 클래스 제거, [서명 완료] 활성화 | 서명 페이지 |
| 7 | "다시" 하고 싶으면 [다시 서명] tap | `pad.clear()` + `is-empty` 클래스 부여 → [서명 완료] disabled | 서명 페이지 |
| 8 | [서명 완료] tap | 1) `toDataURL()` → PNG base64<br>2) `sha256Hex(base64)` → 64자 hex<br>3) POST `/public/batches/{token}/slips/2026-05-05-1/signature` body `{signerName, signaturePngBase64, clientHash}` | 버튼 spinner |
| 9 | (자동) | BE 200 응답 → `shareToken` 수신 → `/d/{token}/s/2026-05-05-1?signed=1` 또는 라우트 변경 | 서명 페이지 → 완료 페이지 |
| 10 | (자동) | ✓ 아이콘 + "서명 완료됨" + 메타 + [공유] 버튼 표시 | 완료 페이지 (mock 02) |
| 11 | [📤 인수자에게 공유] tap | `navigator.share({title:'출고전표 2026-05-05-1', text:'[삼한물류] 출고 인수증', url:'/share/{shareToken}'})` | 시스템 공유 시트 |
| 12 | 카톡 / SMS / AirDrop 등 선택 | 시스템 공유 완료 | 시스템 시트 |
| 13 | (Web Share 미지원 시) | clipboard 복사 + 토스트 "링크가 복사되었습니다" | 토스트 2초 |
| 14 | [목록으로] tap | `/d/{token}` 으로 돌아감, 한국전력 카드 옆에 ✓ 배지 표시 (Slice B 확장) | 배치 페이지 |

### 1.3 실패 케이스

| 실패 | UX |
| --- | --- |
| 네트워크 끊김 (POST 실패) | 토스트 "전송 실패. 다시 시도해주세요." + [서명 완료] 재활성화 (canvas 보존) |
| 토큰 만료 (410 응답) | 410 GONE 페이지 (Slice B 재사용) |
| BE hash mismatch (400) | 토스트 "검증 실패. 다시 서명해주세요." + canvas clear |
| BE 슬립 못 찾음 (404) | 동일 410 GONE (정보 노출 X) |

---

## 시나리오 2 — 인수자: share link 수신 → view → PNG 다운로드

### 2.1 사전 조건
- 인수자 (한국전력 담당자) 가 카톡으로 `/share/{shareToken}` 링크 수신
- shareToken 유효 (+30일 미도래)

### 2.2 step-by-step

| step | 사용자 동작 | 시스템 동작 | 화면 |
| --- | --- | --- | --- |
| 1 | 카톡에서 링크 tap | iOS Safari / Android Chrome 새 탭 진입 | 인수자 view (mock 03) |
| 2 | (자동) | GET `/public/signatures/{shareToken}` → 슬립 read-only + 서명 PNG + 메타 | 인수자 view |
| 3 | (자동) | brand bar + 출고 인수증 카드 + 서명 viewer + [PNG 다운로드] 버튼 표시 | 인수자 view |
| 4 | 스크롤로 슬립 정보 확인 | (없음) | 인수자 view |
| 5 | [📥 PNG 다운로드] tap | `<a download="signature_2026-05-05-1.png" href="data:image/png...">` 자동 트리거 | 다운로드 시트 |
| 6 | (선택) URL 공유 | 카톡 다시 공유 가능 (만료 전까지) | (없음) |

### 2.3 실패 케이스

| 실패 | UX |
| --- | --- |
| shareToken 만료 (410) | 410 GONE 페이지 (Slice B 재사용) + "공유 링크가 만료되었습니다" 메시지 |
| shareToken 무효 (404) | 동일 410 (정보 노출 X) |
| 무효화된 서명 (200 + signed_at NULL) | "이 서명은 무효화되었습니다." + 무효화 사유는 노출 X (개인정보 보호) |

---

## 시나리오 3 — 관리자 (desktop): 서명 정보 확인 + 무효화

### 3.1 사전 조건
- 관리자 (이팀장 — MASTER) 가 desktop 앱 로그인
- SlipDetailPage (`/slips/{id}`) 진입

### 3.2 step-by-step (확인)

| step | 사용자 동작 | 시스템 동작 | 화면 |
| --- | --- | --- | --- |
| 1 | 슬립 리스트에서 한국전력 슬립 클릭 | `/slips/{id}` 라우팅 | SlipDetailPage |
| 2 | (자동) | 기존 슬립 헤더/라인/합계/라이프사이클 + 신규 [전자서명 정보] 카드 fetch | SlipDetailPage |
| 3 | 페이지 하단 [전자서명 정보] 카드 표시 | 서명 PNG 150×80 + 서명자명 + 시각 + 채널 + 검증코드 (64자 hex) + 공유링크 | SlipDetailPage |
| 4 | [📋 복사] tap (공유링크 옆) | clipboard 복사 + 토스트 "링크가 복사되었습니다" | 토스트 2초 |
| 5 | (MASTER) [⚠ 서명 무효화] 버튼 hover | tooltip "서명을 무효화합니다 (감사 로그 기록)" | SlipDetailPage |

### 3.3 step-by-step (무효화 — MASTER only)

| step | 사용자 동작 | 시스템 동작 | 화면 |
| --- | --- | --- | --- |
| 1 | [⚠ 서명 무효화] tap | confirm dialog open | Modal |
| 2 | "사유" textarea 에 "고객 요청으로 재서명 필요" 입력 (10자 이상) | onChange — [⚠ 무효화] 버튼 활성화 | Modal |
| 3 | [⚠ 무효화] tap | DELETE `/api/slips/{id}/signature?reason=...` | Modal spinner |
| 4 | (자동) | BE 200 응답 → 카드 reload → 서명 PNG / 메타 사라지고 "아직 서명되지 않았습니다" 표시 | SlipDetailPage |
| 5 | (자동) | 토스트 "서명이 무효화되었습니다." | 토스트 2초 |
| 6 | (백그라운드) | `slip_signature_audit` INSERT 1건 (action=INVALIDATE, reason=..., actor=이팀장) | (DB) |

### 3.4 권한별 분기

| Role | 카드 | [무효화] | 무효화 시도 시 |
| --- | --- | --- | --- |
| MASTER | 전체 | 표시 | 정상 |
| MANAGER | 전체 | 미표시 | (해당 없음) |
| TEAM_LEADER | 전체 read-only | 미표시 | (해당 없음) |
| WORKER | 카드 미표시 | 미표시 | (해당 없음) |
| 외부 / 비로그인 | (해당 없음 — 로그인 필요) | (해당 없음) | (해당 없음) |

---

## 시나리오 4 — 인쇄: DispatchView 인쇄 시 서명 PNG 자동 표시

### 4.1 사전 조건
- 관리자 (검수자) 가 DispatchView 페이지 (`/dispatches/{id}`) 진입
- 슬립 N건 중 일부는 서명 완료, 일부는 미서명

### 4.2 step-by-step

| step | 사용자 동작 | 시스템 동작 | 화면 |
| --- | --- | --- | --- |
| 1 | DispatchView 우상단 [🖨 인쇄] 클릭 | `window.print()` 호출 | 인쇄 미리보기 |
| 2 | (자동) | A4 portrait 양식 렌더 — 결재선 영역에 서명 PNG 조건부 삽입 | 인쇄 미리보기 |
| 3 | 슬립 1 (서명 있음) — 인수자 셀 안 PNG `<img>` (max-w 55mm × max-h 18mm) + "김인수" + "2026/05/05" | (없음) | 인쇄 미리보기 (mock 04) |
| 4 | 슬립 2 (서명 없음) — 인수자 셀 비어있음 (기존 Slice A 디자인 유지) | (없음) | 인쇄 미리보기 |
| 5 | [인쇄] 또는 [PDF 저장] | OS 인쇄 다이얼로그 | OS 다이얼로그 |

### 4.3 인쇄 매체 호환성

| 매체 | spec |
| --- | --- |
| A4 portrait | 60×30mm 인수자 셀 안에 PNG fit |
| Legal | (CSS 동일 — 셀 위치 자동 조정) |
| 일반 잉크젯 (사무용) | PNG 검은색 펜 stroke 명료 출력 |
| 일반 레이저 (사무용) | 동일 |
| PDF 저장 | `data:image/png;base64,...` data URI 가 PDF 안에 임베드 — 파일 사이즈 ↑ (~30KB/슬립) |

### 4.4 인쇄 양식 변경 비주얼 회귀

`feedback_print_design_iteration.md` 가드 적용:
- 결재선 영역 그리드 **변경 없음** (5칸 60×30mm 유지)
- 인수자 셀 안에 `<img>` + 메타 추가 — CSS-only 변경
- 기존 Slice A `--print-approval-*` / `--print-signature-*` 토큰 모두 재사용
- QA 검증: 서명 없는 슬립 인쇄 시 기존 Slice A 양식과 픽셀 동일 (visual diff 0)

---

## 5. 시나리오 간 데이터 흐름 (요약)

```
[기사 모바일]              [BE]                    [인수자 모바일]
   서명 + 인수자명 ──POST──► slip + audit INSERT
                              ↓
                        shareToken 발급
                              ↓
   shareToken 수신 ◄────────┘
   Web Share API ──────────────────────────────► 카톡 메시지
                                                       ↓
                                                  share link tap
                                                       ↓
                              ◄──── GET /share/{shareToken}
                              read-only 슬립 + PNG 응답
                                                       ↓
                                                  PNG 다운로드 가능

[관리자 desktop]           [BE]
   SlipDetailPage ────GET──► /api/slips/{id}/signature
                              ↓
                        서명 카드 표시
                              ↓
   [무효화] (MASTER only) ──DELETE──► slip 5필드 NULL + audit INSERT
                                       ↓
                                 카드 reload → 빈 상태

[관리자 desktop 인쇄]      [client-side only]
   DispatchView 인쇄 ──────► 슬립별 signaturePng 있으면 셀 안 <img>
                              없으면 빈 셀 (기존 Slice A)
```
