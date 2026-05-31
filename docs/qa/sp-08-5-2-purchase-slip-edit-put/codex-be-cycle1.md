### Codex BE 사이클 1 2a 리뷰 (head `a29bc83e`)

#### Claude 발견 평가

| Claude 항목 | Codex 평가 | 사유 |
|---|---|---|
| BE-1 | valid + fix 정합 | before 변경 전, after saveAndFlush saved 기준 캡처 — audit ordering 명확 |
| BE-2 | valid + fix 정합 | validateLines try 외부 이동 + IAE catch 제거. flush 중심 축소 |
| BE-3 | invalid 동의 | gateway `slip-service-v1` `/api/v1/slips/**` StripPrefix=2 — `/slips` 컨벤션 맞음. Publish historical 예외. dev-report L20 명시 |
| BE-4 | valid, fix 부분 부정합 | service guard 제거 OK 그러나 도메인 메서드가 `requireEditable()` 후 INBOUND check → 비-INBOUND + 비편집 시 403 대신 409 가능. P2 신규 참조 |
| BE-5 | valid, fix 부정합 | Bean Validation + `@Valid @RequestBody` 결합 — quantity=0 라인 422 SLIP_UPDATE_INVALID_LINE 계약 깨질 가능 → MethodArgumentNotValidException 400. P1 신규 참조 |
| BE-6 | valid + fix 정합 | extracting().containsOnly(1) AssertJ Integer == 제거 |
| BE-N1 | valid + fix 정합 | String.join(",") 명시 |
| BE-N2 | valid + fix 정합 | zero UUID audit Javadoc 보강 |
| QA D-01 | valid + fix 정합 | $.data.version=1 단언. @Version saveAndFlush 자동 반영 |
| QA D-02 | valid + fix 정합 | 수치 8 정정 |
| QA D-03 | over-engineering but harmless | 주석 변경만 |
| DevOps MINOR-2 | valid + fix 정합 | MICROS truncation. SP-08-4-2 미적용이지만 보수적 개선 |

#### Codex 자체 신규 발견 (BE 영역)

- **P1**: `SlipUpdateController.java:42`, `SlipUpdateRequest.java:48`, `GlobalExceptionHandler.java:27` — Bean Validation + `@Valid @RequestBody` 결합으로 quantity=0 라인이 service `validateLines()` 도달 전 `MethodArgumentNotValidException` → `INVALID_INPUT` 400 응답 가능. 기존 422 SLIP_UPDATE_INVALID_LINE 계약 보존 필요. **수정 권고**: GlobalExceptionHandler 에서 `MethodArgumentNotValidException` 의 `LineRequest` field 위반을 `SLIP_UPDATE_INVALID_LINE` 422 로 매핑하거나, `LineRequest` Bean Validation 제거 후 service 수동 검증 유지.

- **P2**: `Slip.java:658`, `Slip.java:686` — service INBOUND guard 제거 후 도메인 메서드가 `requireEditable()` 먼저 호출. OUTBOUND + DRAFT/SAVED 가 아닌 상태에서 403 대신 409 가능. **수정 권고**: service guard 복구 또는 도메인 메서드 ordering 변경 (slipType check → requireEditable 순서).

- **Nit**: `SlipUpdateRequest.java:40` — Javadoc lineId nullable 언급하지만 record 에 lineId 필드 없음. 문서 제거.

#### 종합

사이클 2 필요. BE-5 Bean Validation 계약 깨짐 (P1) + BE-4 guard ordering (P2) 해소 필수.
