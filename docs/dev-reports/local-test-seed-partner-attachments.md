# 거래처 첨부 파일 (사업자등록증 / 명함 등) — partner-service 보강

> branch: `feature/local-test-setup`
> 작업일: 2026-05-09
> 범위: partner-service — 거래처 첨부 파일 entity + REST + sample seed

---

## 1. 배경 및 목표

기존 `partner-service` 의 `Partner` entity 는 사업자번호 / 상호 / 주소 / 신용한도 등 텍스트 / 숫자 필드만 보유한다. 다음 사용자 요청을 수용하기 위해 별도 첨부 파일 도메인을 신규 추가:

> "거래처 정보는 사업자등록증 사본, 명함 같은 이미지 파일도 첨부할 수 있어야 함".

다른 BE agent 가 동일 시점에 `Partner.java` 의 27 필드 보강 작업을 진행 중이므로 본 작업은 **신규 entity (`PartnerAttachment`) + 신규 table** 로 분리하여 충돌을 회피했다. `Partner.java` / `PartnerSeeder.java` 등 기존 파일은 수정하지 않는다 (예외: `application.yml` 토글 + MinIO 설정 12 줄 추가, `build.gradle` MinIO 의존성 1 줄 추가).

## 2. 도메인 모델

### 2.1 `PartnerAttachment` entity (1:N — partner 1건당 N 첨부)

| 필드 | 타입 | 비고 |
|------|------|------|
| id | UUID PK | UuidGenerator |
| partnerId | UUID FK | `partners.id` 참조 |
| attachmentType | enum (5종) | DB CHECK 제약 + JPA `@Enumerated(STRING)` |
| fileName | VARCHAR(200) | 원본 파일명 (예: "삼성에어컨_사업자등록증.png") |
| fileSize | BIGINT | 바이트 |
| mimeType | VARCHAR(100) | image/png · image/jpeg · application/pdf |
| storageKey | VARCHAR(500) | MinIO object key (활성 unique) |
| storageUrl | VARCHAR(1000) | presigned URL 캐시 (만료 가능) |
| description | VARCHAR(500) | 비고 (선택) |
| uploadedBy | UUID | 업로더 employee UUID |
| uploadedAt | TIMESTAMP | 업로드 시각 |
| (BaseEntity 7 audit) | — | created/modified/deleted (at/by) + isDeleted |

도메인 메서드:

- `register(...)` 정적 factory — 8 필수값 가드
- `refreshStorageUrl(url)` — presigned URL 캐시 갱신
- `updateDescription(memo)` — 메모만 수정 (파일 메타 immutable)
- `softDelete(deleterUserId)` — BaseEntity.markDeleted 위임

### 2.2 `AttachmentType` enum (5 종)

| 값 | 의미 | 사용 예 |
|----|------|---------|
| `BIZ_LICENSE` | 사업자등록증 사본 | 회계 신고 / 세금계산서 발행 검증 |
| `BUSINESS_CARD` | 담당자 명함 | 영업 직원이 신규 거래처 첫 미팅 후 등록 |
| `TAX_INVOICE` | 세금계산서 사본 | 발행/수취 증빙 |
| `CONTRACT` | 거래/공급 계약서 PDF | 장기 공급 계약 보관 |
| `OTHER` | 그 외 | 사용자가 분류 미정 시 fallback |

신규 카테고리 추가 시 (1) enum, (2) Flyway migration CHECK 제약, (3) FE i18n label 매핑 모두 동기화 필요.

## 3. Flyway migration — `V3__create_partner_attachments.sql`

`partners.id` FK 참조. 활성 행 partial 인덱스 3개:

- `ix_partner_attachments_partner_id` — 거래처별 목록 조회
- `ix_partner_attachments_type` — 유형별 필터
- `ux_partner_attachments_storage_key_active` — MinIO 객체 멱등성 unique

## 4. REST 엔드포인트 (권한 매트릭스)

| 메서드 | 경로 | 권한 | 설명 |
|--------|------|------|------|
| POST | `/api/v1/partners/{partnerId}/attachments` | SALES / MANAGER / MASTER | multipart 업로드 (type / file / description) |
| GET | `/api/v1/partners/{partnerId}/attachments` | 모든 인증 사용자 | 거래처별 목록 (downloadUrl 미포함) |
| GET | `/api/v1/partners/attachments/{attachmentId}` | 모든 인증 사용자 | 상세 + presigned URL 발급 (1시간) |
| DELETE | `/api/v1/partners/attachments/{attachmentId}` | SALES / MANAGER / MASTER | soft-delete (MinIO 객체 보존) |

응답은 모두 `ApiResponse<T>` envelope 로 wrap (memory: D-P10-12 일관).

## 5. MinIO 연동 + presigned URL 정책

### 5.1 추상화 — `AttachmentStorage` interface

운영/dev 환경에서는 `MinioAttachmentStorage` 가 io.minio client 로 실제 업로드를 수행하고, MinIO 미가용 환경 (CI / 단위 테스트 / 워크스테이션 docker-compose 미기동) 에서는 `NoopAttachmentStorage` 가 fallback 으로 주입되어 application boot 가 실패하지 않는다.

토글 = `app.partner.minio.enabled` (default `false`).

### 5.2 presigned URL 정책

| 항목 | 값 | 근거 |
|------|----|------|
| 유효기간 | 1시간 (3,600초) | 사용자 다운로드 1회성 + 만료 후 재발급 패턴 |
| method | GET | 다운로드 전용 |
| bucket | `partner-attachments` | 토글 가능 (`app.partner.minio.bucket`) |
| 발급 시점 | (1) 업로드 직후 캐시 (2) 상세 조회 시 재발급 | 만료 후 재호출 패턴 |

### 5.3 docker-compose 호환

기존 `infrastructure/docker-compose.yml` 의 `samhan-minio` container (port 9000 / console 9001, root user `samhan` / pw `samhan_dev_pw`) 와 default 값이 일치 — 별도 인프라 추가 불필요.

## 6. 파일 가드

| 가드 | 값 | 동작 |
|------|----|------|
| 최대 크기 | 10MB | 초과 시 400 INVALID_INPUT |
| 허용 MIME | image/png · image/jpeg · application/pdf | 그 외 400 INVALID_INPUT |
| storageKey 충돌 | 활성 unique | 신규 UUID suffix 사용 (충돌 거의 0) |
| partnerId 미존재 | — | 404 NOT_FOUND |
| 파일명 path traversal | `/` `\` 치환 | sanitizeFileName |

## 7. Sample seed — `PartnerAttachmentSeeder`

### 7.1 활성 가드

- `@Profile("dev")` + `app.partner.seed-test-attachments=true` 동시 충족
- `@Order(20)` — `PartnerSeeder` 50건 생성 직후 실행
- application.yml default `false` — 운영/staging 데이터 오염 방지

### 7.2 분포 (총 75건)

| AttachmentType | 적용 partner seq | 건수 |
|----------------|------------------|------|
| BIZ_LICENSE | 1~30 | 30 |
| BUSINESS_CARD | 1~30 | 30 |
| TAX_INVOICE | 1~10 | 10 |
| CONTRACT | 1~5 | 5 |
| **합계** | | **75** |

### 7.3 결정성 UUID

- `id` = `UUID.nameUUIDFromBytes("samhan-seed:partner-attachment-id:" + partnerCode + ":" + type + ":" + seq)`
- `storageKey` = `partner-attachments/seed/{partnerCode}/{deterministic-uuid}.{png|pdf}`
- `uploadedBy` = `UUID.nameUUIDFromBytes("samhan-seed:partner-attachment-uploader:" + partnerCode)`
- 멱등성 → `existsByStorageKey` 가드 + 재실행 시 누락 분만 INSERT

### 7.4 placeholder 정책

- 실 파일은 MinIO 에 업로드하지 않음 — metadata 만 INSERT
- `storageUrl` 은 dummy URL (`http://localhost:9000/partner-attachments/...?seed=true`)
- `fileName` = "{거래처명}_{유형한글}_{seq}.{ext}" (예: "(주)서울에어컨_사업자등록증_1.png")
- `mimeType` = CONTRACT → `application/pdf`, 그 외 → `image/png`
- `fileSize` = 50KB ~ 500KB 결정성 분포

## 8. 단위 테스트 — `PartnerAttachmentTest`

JPA / Spring 부팅 없이 도메인 메서드 11건 검증 (JDK 17 한글 path 환경 PASS):

- register 정상 흐름 (1)
- 필수값 가드 7 (partnerId / type / fileName / fileSize / mimeType / storageKey / uploadedBy)
- updateDescription / refreshStorageUrl (3)
- softDelete → BaseEntity isDeleted/deletedBy/deletedAt (1)

## 9. 컴파일 검증

```bash
./gradlew :services:partner-service:compileJava :services:partner-service:compileTestJava
```

본 PR 의 모든 신규 코드 컴파일 통과. 단위 테스트 11건은 `gradle test` 로 실행 가능 (한글 path 환경에서는 `gradle assemble` + IDE 실행 권장 — feedback_korean_path_jdk).

## 10. 추후 보강 사항 (out-of-scope)

| 항목 | 우선순위 | 비고 |
|------|----------|------|
| 바이러스 스캔 (ClamAV / WAF) | High | 외부 사용자 업로드 시 필수 — 현재는 내부 영업직원만 사용 가정 |
| 사업자등록증 OCR + 사업자번호 자동 추출 | Medium | BIZ_LICENSE 업로드 시 partner.bizNo 자동 검증 |
| 명함 OCR + 담당자 정보 자동 추출 | Medium | BUSINESS_CARD 업로드 시 contact 자동 등록 |
| 첨부 retention 정책 + MinIO lifecycle rule | Medium | 5년 후 자동 archive (감사 보관 기간) |
| 업로드 진행률 (chunked upload) | Low | 10MB 제한 환경에서는 불필요 |
| 썸네일 자동 생성 | Low | 화면 미리보기 성능 개선 |

## 11. 변경 파일 목록

신규:

- `services/partner-service/src/main/java/.../domain/AttachmentType.java`
- `services/partner-service/src/main/java/.../domain/PartnerAttachment.java`
- `services/partner-service/src/main/java/.../repository/PartnerAttachmentRepository.java`
- `services/partner-service/src/main/java/.../service/AttachmentStorage.java`
- `services/partner-service/src/main/java/.../service/MinioAttachmentStorage.java`
- `services/partner-service/src/main/java/.../service/NoopAttachmentStorage.java`
- `services/partner-service/src/main/java/.../service/PartnerAttachmentService.java`
- `services/partner-service/src/main/java/.../web/PartnerAttachmentController.java`
- `services/partner-service/src/main/java/.../web/dto/PartnerAttachmentResponse.java`
- `services/partner-service/src/main/java/.../seed/PartnerAttachmentSeeder.java`
- `services/partner-service/src/main/resources/db/migration/V3__create_partner_attachments.sql`
- `services/partner-service/src/test/java/.../domain/PartnerAttachmentTest.java`
- `docs/dev-reports/local-test-seed-partner-attachments.md` (본 문서)

수정 (최소):

- `services/partner-service/src/main/resources/application.yml` — 토글 1 + MinIO 설정 6
- `services/partner-service/build.gradle` — `io.minio:minio:8.5.12` 의존성 1 줄
