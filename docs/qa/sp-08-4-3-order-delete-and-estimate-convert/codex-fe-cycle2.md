## Codex frontend-engineer 사이클 2 리뷰 (head `d6364d4b`)

### Codex 사이클 1 자체 발견 추적
- FE P1 5건: 사이클 1.5 기준 해소.
- route id 통일 후속 우려: BE `PartnerOrderIdResolver` 대조 결과, 현재 head 에서는 실 API 404 재현 가능성이 낮아 P1 유지하지 않음.

### Claude FE 사이클 2 발견 평가
- **FE-C2-01: invalid as FE defect / keep as BE-policy note**. `PartnerOrderUpdateRequest`가 `partnerCode`를 `@NotBlank`로 받고, `PartnerOrderUpdateService.update()`가 `order.updateHeader(request.partnerCode(), ...)`로 변경. FE 가 거래처 코드 Input 편집 가능하게 둔 것은 현재 BE contract 와 충돌하지 않음. 거래처 코드 변경 자체를 업무상 막아야 한다면 BE contract/policy 변경 이슈.
- **FE-C2-02: invalid**. `PartnerOrderIdResolver.findByIdentifier()`는 `findByOrderNo(id)` 후 `findByOrderNo(toSlashOrderNo(id))` 시도. `toSlashOrderNo("2026-05-04-1")`는 `2026/05/04-1` 로 변환. 따라서 list `toOrderPathId()`가 만든 hyphen 형 path id 도 BE resolver 경로에서 resolve 됨.
- **FE-C2-03: valid info**. 상세 queryKey 가 canonical `orderNumber` 아니라 route `id` 기반이라 slash/hyphen 표현이 cache key 에 남음. 현 동작 깨지지 않으나 추후 직접 slash URL 진입 시 중복 cache/무효화 누락 가능.

### Codex 신규 발견 (사이클 2)
- **Codex-FE-C2-01: 정보 / mock coverage gap**. `mock.ts` partner-order detail GET/PUT mock 이 path id 값 미검증 후 항상 성공 응답. 이번 FE-C2-02 같은 route-id 회귀를 mock QA 가 가릴 수 있음. mock 도 `decodeURIComponent(id)` 후 `2026/05/04-1` 및 `2026-05-04-1` alias 만 허용, 그 외 404 반환 권장.

### 종합
사이클 2 기준 FE block/P1 신규 없음. Claude FE-C2-02 invalid (BE resolver 가 hyphen 형 slash 보정). 남는 항목은 BE 정책 확인 성격의 FE-C2-01, cache 표현 통일 정보성 FE-C2-03, mock 검증 강화 정보성 Codex 1건.

**Codex FE-agent — 2026-05-17**
