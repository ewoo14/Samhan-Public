## Codex Designer 사이클 2 리뷰 (head `67791758`)

### Findings

- **P1 유지: UUID/내부 ID 노출 fallback 제거 필요**
  `SalesPartnerOrderDetailPage.tsx`의 page title/badge가 `query.data?.orderNumber ?? id`를 사용합니다. 로딩 중 또는 조회 실패 전 단계에서 route `id`가 UUID면 사용자 화면에 내부 ID가 노출될 수 있습니다. 본 repo의 UUID 비공개 규칙상 `orderNumber`가 없을 때는 빈 값/`조회 중`/`주문번호 확인 중` 같은 안전한 문구로 대체하는 편이 맞습니다.

- **P2로 조정: D-C2-1 409 reload 후 성공 피드백 부재는 유효**
  `handleConflictReload()`는 refetch 후 banner를 제거하고 form sync까지 수행하지만, 사용자는 "최신 내용 불러오기" 완료 여부를 명확히 확인하기 어렵습니다. 다만 실패/차단 수준은 아니므로 P1보다는 P2 UX 보완으로 보는 것이 적절합니다.

- **Nit 유효: D-C2-2 line table key 안정성**
  `key={`${line.modelCode}-${index}`}`는 표시 테이블에서는 영향이 작지만, edit modal에서는 `modelCode` 입력 중 key가 바뀌어 row remount/focus 흔들림 가능성이 있습니다. 서버 line id가 없다면 local stable key를 별도로 갖는 편이 좋습니다.

- **Nit 유효: readOnly Input 시각 cue 부재**
  DS `Input`은 `readOnly` prop은 pass-through되지만 `Input.module.css`에 `:read-only` 스타일이 없습니다. 상세 화면의 읽기 전용 필드가 편집 가능 input처럼 보입니다. `:read-only:not(:disabled)` cue를 DS 또는 sales scope에 추가 권장합니다.

### Non-blocking / Over-engineering

- D-C2-3, D-C2-4 PNG mock 문구/주석은 QA artifact 품질 Nit로만 봅니다. 제품 UI 영향은 없습니다.
- D-C1 inline style magic number 잔존은 유효하지만 release blocker는 아닙니다. `marginTop: 12`, `fontSize: 11`, `gridColumn` 등을 module class/token으로 옮기면 충분합니다.
- 색 대비, focus state, role guard 버튼 숨김, 모바일 1-column form, 인쇄 양식 영향은 현재 범위에서 큰 문제를 못 봤습니다.

**Codex Designer-agent — 2026-05-17**
