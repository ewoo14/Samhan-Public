### Codex FE 사이클 1 2a 리뷰 (head `a29bc83e`)

#### Claude 발견 평가

| 항목 | Codex 평가 | 사유 |
|---|---|---|
| F-1 | valid + fix 정합 | `SlipDetailPage.tsx:1793,1799` 라벨/aria-label 입금예정일 통일 |
| F-2 | valid + fix 정합 | Modal onClose isPending return + 취소 버튼 pending disabled 이중 가드 |
| F-3 | valid + 대부분 정합 | `purchaseUpdatedAt` state + sync + conflict reload 갱신. PUT body `?? slip.updatedAt` fallback. modal open 중 SSE 동기화 차단 의도 정상 |
| F-4 | valid + fix 정합 | `purchaseIsConflict` boolean — 409 true, 422/reload 성공 false |
| F-5 | valid + 부분 fix | `addPurchaseLine`/`removePurchaseLine` modal 내부 state 만. 출고 라인 영향 없음. **P1 신규 참조** |
| F-6 | valid + fix 정합 | mode INBOUND + 권한 + SAVED/DRAFT 모두 요구 — INSPECTING/CONFIRMED/CANCELED 노출 차단 |

#### Designer fix 평가 (FE 영역)

| 항목 | Codex 평가 | 사유 |
|---|---|---|
| D-C1-1 | valid + fix 정합 | `.warning-banner` 클래스 + tokens.css scale 등록. runtime fallback 의존 제거 |
| D-C1-2 | valid + fix 정합 | `purchase-edit-field` 분리 + CSS 추가 |
| D-C1-3 | valid + fix 정합 | inline → `.td-right` 이동 |
| D-C1-5 | valid + fix 정합 | `<Spinner size="md" label="불러오는 중">` design-system export 정합 |

#### Codex 자체 신규 발견 (FE 영역)

- **P1**: `SlipDetailPage.tsx:1914` `addPurchaseLine()` 신규 라인 `productId: ''` 빈 문자열. modal 에 product lookup/선택 UI 없음 → 사용자가 `productName/modelName` 입력해도 PUT body 에 `productId: ''` 전송. BE `LineRequest.productId @NotNull UUID` 라 deserialization/validation 실패 가능. **수정 권고**: product lookup UX 추가 또는 본 PR 에서 "행 추가" 제거 후 기존 라인 복제/삭제만 허용.

- **Nit**: `purchaseUpdateMutation.onSuccess` 에서 `setPurchaseIsConflict(false)` 명시 호출 누락. 성공 경로 reset 의도 명확화 + modal open 초기화 시 함께 reset 권고.

- **정보**: read-only 정적 검토 — typecheck/lint 미실행. Spinner import/props 및 canDirectEditPurchase type-level 문제 없음.

#### 종합

사이클 2 필요. F-5 "행 추가" productId 빈 문자열 저장 실패 가능 (P1). 해소 후 재검토.
