## Codex qa-tester 사이클 1 리뷰 (head `97afca70`)

### Claude QA 발견 평가

- **P1-01 PNG 03 내용 오류: valid.** 실제 `03-from-estimate-success.png`는 제목과 파일명은 success인데 본문이 `409 Conflict` / `PARTNER_ORDER_FROM_ESTIMATE_ALREADY_CONVERTED`. dev-report §4의 "from-estimate success"와 불일치.
- **P1-02 CANCELED 422 IT case 누락: valid.** 정책 §8은 `CONFIRMED / CANCELED` 삭제 불가인데 `PartnerOrderDeleteIT`는 `CONFIRMED`만 검증. 실제 서비스는 `DELETABLE_STATUSES = DRAFT / CONFIRMING`이라 `CANCELED`도 422가 맞지만 회귀 테스트 없음.
- **P2-01 AlreadyConvertedReturns409 첫 요청 body 미검증: valid.** `PartnerOrderFromEstimateIT.testFromEstimateAlreadyConvertedReturns409`의 첫 요청은 `201`만 보고 생성 응답의 `source`/라인/상태 미검증.
- **P2-02 TOCTOU 이중 체크 dead code: partially valid.** "완전 dead code" 표현은 약함. `snapshot.estimateId()`가 path id와 다르면 의미가 생길 수 있음. 다만 현재 계약상 동일 ID가 정상이며, 두 번째 조회만으로는 unique index race를 완전히 닫지 못함. P2 유지.
- **Nit-01 PNG 한글 깨짐: valid.** 01~04 모두 한글이 깨져 QA 증적 품질 낮음.
- **Nit-02 testDeleteSuccess findById assertion 의미 약함: valid but minor.** `@SQLRestriction` 때문에 `findById` empty는 soft delete 필터 확인으로 의미는 있지만, 바로 아래 raw SQL 검증이 핵심이라 assertion 메시지 중복/혼동 가능.

### Codex 신규 발견

- **P2 신규: FROM_ESTIMATE audit IT 검증 누락.** dev-report §2는 `FROM_ESTIMATE` audit 기록을 구현 범위로 적고 서비스도 `new ChangeEntry("FROM_ESTIMATE", null, snapshot.estimateNumber())`를 호출하지만, `PartnerOrderFromEstimateIT`는 audit log 저장을 검증하지 않음. DELETE audit은 별도 테스트가 있으므로 변환 audit도 symmetry 있게 추가.

### 종합

Claude QA 6건 중 5건 valid, 1건 partially valid. 정책과 실제 코드의 핵심 정합성은 맞음: 문서는 `CONFIRMED / CANCELED` 삭제 불가, 코드는 `DRAFT / CONFIRMING`만 허용이라 결과적으로 `CANCELED`도 차단. 차단 사유를 테스트로 고정하지 않은 점이 문제.

**Codex QA-agent — 2026-05-17**
