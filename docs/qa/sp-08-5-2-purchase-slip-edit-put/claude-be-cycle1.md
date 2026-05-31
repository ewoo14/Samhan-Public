## backend-engineer 사이클 1 리뷰 (head `1248cdc1`)

### 결함 표

| # | 심각도 | 위치 | 내용 |
|---|---|---|---|
| BE-1 | P1 | `SlipUpdateService.update` L57 | `before = summarize(slip)` 캡처 시점이 `updateHeader`/`replaceLines` 호출 이전, `after = summarize(slip)` 는 `saveAndFlush` 이후가 아닌 동일 인스턴스 기준. JPA dirty-read 없이 현재 동작은 정상이나 ordering 이 명시적으로 보장되지 않음. `from(saved)` 호출로 명확화 권고. |
| BE-2 | P1 | `SlipUpdateService.update` L79 catch | `try` 블록이 `updateHeader`+`replaceLines` 까지 포함하여 범위 과다. `validateLines` 가 이미 `BusinessException` 을 던지므로 `IllegalArgumentException` catch 는 도달 불가. `validateLines` 를 try 외부로 이동 + IAE catch 제거 권고. |
| BE-3 | P1 | `SlipUpdateController` `@RequestMapping("/slips")` | `SlipPublishController` 만 `/api/v1/slips`. context-path 없음. PR spec endpoint 가 `PUT /api/v1/slips/{id}` 라면 불일치. (검증: slip-service 컨벤션은 `/slips`. Publish 만 예외. **본 항목 invalid 가능성 — TM 평가**) |
| BE-4 | P2 | `SlipUpdateService.update` L57~72 | `INBOUND` guard 가 service L50, `Slip.updateHeader`, `Slip.replaceLines` 3곳 중복. 도메인 메서드 신뢰 시 service guard 제거 또는 service 단일 처리 권고. |
| BE-5 | P2 | `SlipUpdateRequest.LineRequest` | `quantity`/`unitPrice` Bean Validation 어노테이션 부재. `validateLines` 가 수동 검사. `@NotNull @Min(1) Integer quantity` / `@NotNull @DecimalMin("0") BigDecimal unitPrice` 추가 권고. |
| BE-6 | P2 | `SlipUpdateIT` L231 | `assertThat(logs).allMatch(log -> log.getRevisionNo() == 1)` — Integer 박싱 시 NPE 위험. `isEqualTo(1)` 명시적 언박싱 권고. |
| BE-N1 | Nit | `SlipUpdateService` L131 | `summarizeLines` `.toList().toString()` 으로 `[a,b]` 변환 — `|` 구분자와 혼용. `String.join(",")` 일관성. |
| BE-N2 | Nit | `SlipUpdateController` L43 | `actorId = new UUID(0L, 0L)` 폴백 audit 기록 — SP-08-4-2 동일 패턴 (Javadoc 보강). |

### 긍정 사항

- `orphanRemoval = false` + `markDeleted` soft-delete 패턴 컨벤션 부합
- `verifyVersion` `modifiedAt == null → createdAt` fallback SP-08-4-2 회고 반영
- `@MockBean` 6종 + lenient stub IT 격리 가이드 준수
- `SLIP_OPTIMISTIC_LOCK_CONFLICT(409)` / `SLIP_UPDATE_INVALID_LINE(422)` 명확 분리
- IT 8 case success/409/404/403×3/422/audit/non-inbound 커버

### 종합

사이클 2 필요. 핵심 BE-3 (경로 검증 필요), BE-2 (catch 범위), BE-1 (ordering 명확화), BE-5 (Bean Validation) 권고.

**backend-engineer agent — 2026-05-18**
