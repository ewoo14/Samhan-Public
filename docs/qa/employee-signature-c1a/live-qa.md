# C1a 라이브 실서버 QA — 사원 서명 저장소·인증 경로 (user-service)

> 2026-06-21 · PR #547 · **실 jar standalone 부팅 + 실 Postgres** (가짜·목업 없음, [[feedback_no_fake_data_ever]])
>
> C1a는 BE 전용 슬라이스(사용자 UI 없음 — UI는 C2). 따라서 라이브 캡처 = **실서버 HTTP 엔드포인트 응답**.
> 관리자 PATCH/DELETE 엔드포인트는 게이트웨이+auth 스택 권한 경로가 필요해 본 라이브에서는 실 Testcontainers IT 12건으로 검증(아래),
> 자체 토큰 인증인 내부 배치 엔드포인트(`/internal/**`)는 standalone 라이브 캡처.

## 부팅 (실 Postgres + Flyway V1..V10)
```
Flyway: Migrating schema "public" to version "10 - add employee signature"
Flyway: Successfully applied 10 migrations to schema "public", now at version v10
Hibernate ddl-auto=validate: (스키마-엔티티 검증 통과 — 오류 없음)
Tomcat started on port 18083
Started UserServiceApplication in 12.165 seconds
```
→ **V10가 실 부팅에서 적용되고, Employee 엔티티(서명 4필드)가 실 Postgres 스키마와 정확히 일치**(validate 통과).

## 픽스처 (실 PNG)
- Python zlib 생성 유효 1x1 PNG, **70 bytes**, sha256 `4ff6ab670a58c14270e034e2090d9a432caa263a14e0a25785386b0c12f880b5`
- dev_master · dev_warehouse 에 서명 등록(UPLOAD), dev_manager 미등록(생략 케이스 실증용)

## POST /internal/users/signatures (X-Internal-Token)
| 케이스 | 요청 | 결과 |
|---|---|---|
| 배치 조회(3 ids) | master+warehouse(등록)+manager(미등록) | **200**, data 2건, 각 `signaturePngBase64="data:image/png;base64,iVBORw0KGgoAAAAN…"`(len 118) + signedAt, **manager 생략** |
| 토큰 누락 | header 없음 | **403** |
| 잘못된 토큰 | `X-Internal-Token: WRONG` | **401** |
| userIds 51개(>50) | 51 UUID | **400** (`@Size(max=50)` 라이브 검증 — 듀얼리뷰 fix) |
| 빈 userIds | `[]` | **200** `{"success":true,"code":"OK","data":{}}` |

## 관리자 엔드포인트 (실 Testcontainers Postgres IT, skipped=0)
- `AdminUserSignatureControllerIT` **12건**: PATCH 업로드 200 / 해시불일치 400 / 비PNG 422 / 50KB초과 422 / 재등록 교체 / dataURI passthrough / 90KB초과 400 / 미존재 404 / DELETE 204(+audit INVALIDATE 행) / DELETE 미등록 409 / DELETE 빈·누락 reason 400
- `UserPermissionControllerIT` 73건: 서명 PATCH(UPDATE)/DELETE(DELETE) 권한 deny 403 매트릭스 포함

## 결론
실 jar + 실 Postgres + Flyway V10 + ddl validate + 실 Tomcat + InternalTokenFilter 인증 + 실 HTTP로 C3 소비 계약(`Map<UUID, {signaturePngBase64(data URI), signedAt}>`) + 미등록 생략 + 토큰 게이트 + 배치 상한을 라이브 실증. BLOCKER 0.
