# 거래처 마이그레이션 매핑 표 (이카운트 → SamhanLogis partner-service)

> Source: `docs/migration/ecount-reference/091522~091604.png` (거래처 4 탭 캡처)
> Target: [Partner.java](../../../services/partner-service/src/main/java/com/samhanair/logis/partner/domain/Partner.java) (27 필드 호환 완료)
> 작성일: 2026-05-13

---

## 1. Excel 컬럼 매핑

이카운트 Excel 다운로드 시 한글 헤더 그대로 출력됩니다. 아래 표대로 자동 매핑:

### 1-1. 기본 정보 탭

| 이카운트 Excel 헤더 | Partner 필드 | DB 컬럼 | 변환/검증 |
|---|---|---|---|
| 거래처코드 | `partnerCode` | `partner_code` | NOT NULL, 활성 행 unique partial index |
| 사업자등록번호 | `bizNo` | `biz_no` | `-` 제거, 10자리 검증 |
| 거래처명 | `name` | `name` | NOT NULL |
| 대표자명 | `representative` | `representative` | nullable |
| 업태 | `businessType` | `business_type` | nullable |
| 종목 | `industry` | `industry` | nullable |
| 종사업장번호 | `subBizNo` | `sub_biz_no` | nullable, 4자리 |
| 등록일자 | `registrationDate` | `registration_date` | `LocalDate` 파싱 |

### 1-2. 연락처 탭

| 이카운트 Excel 헤더 | Partner 필드 | DB 컬럼 | 변환/검증 |
|---|---|---|---|
| 전화번호 | `phone` | `phone` | 정규화 (예: `031-1234-5678`) |
| 휴대전화 | `mobile` | `mobile` | nullable |
| FAX | `fax` | `fax` | nullable |
| 이메일 | `email` | `email` | nullable |
| 이메일2 | `email2` | `email2` | nullable (정산/세무 담당) |
| 홈페이지 | `website` | `website` | nullable |

### 1-3. 주소 탭

| 이카운트 Excel 헤더 | Partner 필드 | DB 컬럼 | 변환/검증 |
|---|---|---|---|
| 우편번호1 | `zipCode1` | `zip_code1` | 본사 |
| 주소1 | `address1` | `address1` | 본사 |
| 우편번호2 | `zipCode2` | `zip_code2` | 배송지 |
| 주소2 | `address2` | `address2` | 배송지 |
| (legacy 통합 주소) | `address` | `address` | `address1` 복사 (V1 호환) |

### 1-4. 거래 정책 탭

| 이카운트 Excel 헤더 | Partner 필드 | DB 컬럼 | 변환/검증 |
|---|---|---|---|
| 거래처분류1 | `partnerGroup1` | `partner_group1` | (VIP거래처/일반거래처/신규거래처) |
| 거래처분류2 | `partnerGroup2` | `partner_group2` | (수도권/영남권/호남권/충청권) |
| 통화 | `currency` | `currency` | default `KRW` |
| 출하대상 | `shipmentTarget` | `shipment_target` | Y/N → boolean |
| 판매유형 | `salesType` | `sales_type` | default `기본설정` |
| 구매유형 | `purchaseType` | `purchase_type` | default `기본설정` |
| 매출계정관리 | `receivableNoMgmt` | `receivable_no_mgmt` | default `기본설정` |
| 매입계정관리 | `payableNoMgmt` | `payable_no_mgmt` | default `기본설정` |
| 출고조정률 | `outboundAdjustmentRate` | `outbound_adjustment_rate` | 0.00~0.05 |
| 입고조정률 | `inboundAdjustmentRate` | `inbound_adjustment_rate` | 0.00~0.05 |
| 판매단가그룹 | `salesPriceGroup` | `sales_price_group` | (VIP단가/일반단가/신규단가) |
| 구매단가그룹 | `purchasePriceGroup` | `purchase_price_group` | (기본구매단가) |
| 여신한도 | `creditLimit` | `credit_limit` | `BigDecimal`, NOT NULL (0=신용거래불가) |
| 여신기간(일) | `creditPeriodDays` | `credit_period_days` | 30/60/90 분포 |
| 결제기한(일) | `paymentDueDays` | `payment_due_days` | 30/45/60 분포 |

---

## 2. 자동 계산 / 시스템 필드

| 필드 | 처리 |
|---|---|
| `id` (UUID) | `@UuidGenerator` 신규 발급 (이카운트 코드는 `partnerCode` 로 보존) |
| `outstandingBalance` | 초기 적재 시 0, 추후 MIG-6 회계 전표 적재 시 누적 계산 (PartnerCreditHistory 와 동기) |
| `status` | default `ACTIVE` (이카운트에 "거래중지" 플래그 있으면 `SUSPENDED`) |
| `searchKeyword` | `{name} {bizNo} {phone}` 자동 생성 (PartnerSeeder 패턴 차용) |
| `createdAt` / `updatedAt` | BaseEntity audit (Flyway INSERT 시점 = 마이그레이션 일자) |
| `createdBy` / `updatedBy` | `migration-ecount@samhan` |
| `isDeleted` | `false` |

---

## 3. PII 마스킹 적용

```java
// EcountPartnerImporter.java (예정)
private String maskRepresentativeRrn(String raw) {
  // YYMMDD-1234567 → YYMMDD-1******
  if (raw == null || !raw.matches("\\d{6}-\\d{7}")) return raw;
  return raw.substring(0, 8) + "******";
}
```

> ※ 이카운트 거래처 마스터에 주민번호 컬럼이 별도로 있는지 캡처 재확인 필요. 대부분 사업자등록증 PDF 첨부로 관리. **만약 컬럼 없으면 본 마스킹 로직 불필요** (PoC 시 확정).

---

## 4. 검증 SQL (QA Agent 작성 예정)

```sql
-- (1) 행 수 일치 — staging vs target
SELECT
  (SELECT COUNT(*) FROM staging.ecount_partner_raw)            AS raw_rows,
  (SELECT COUNT(*) FROM samhan_partner.partners WHERE is_deleted = false) AS imported_rows;

-- (2) biz_no 중복 검증
SELECT biz_no, COUNT(*) FROM samhan_partner.partners
WHERE is_deleted = false
GROUP BY biz_no HAVING COUNT(*) > 1;

-- (3) NULL 필수 필드 검증
SELECT * FROM samhan_partner.partners
WHERE partner_code IS NULL OR biz_no IS NULL OR name IS NULL;

-- (4) 여신한도 합계 일치 (Excel SUM vs DB SUM)
SELECT SUM(credit_limit) FROM samhan_partner.partners WHERE is_deleted = false;
```

---

## 5. 미해결 / 사용자 확인 사항

- [ ] 이카운트 Excel 의 실제 헤더 명칭이 위 표와 일치하는지 (PoC 다운로드 후 확인)
- [ ] 대표자 주민번호 컬럼 존재 여부 (없으면 마스킹 로직 skip)
- [ ] `partnerCode` 가 이카운트 코드 그대로인지, 신규 발급할지 (default: 이카운트 코드 유지 → 운영 cutover 시 슬립 번호 충돌 방지)
- [ ] 거래처분류1/2 코드 체계 (이카운트는 자유입력 가능 — 정형화 필요시 매핑 테이블 작성)

---

## 6. 첨부파일 (Out of scope for MIG-1)

본 PoC 는 **메타데이터만** 처리합니다. 첨부 (사업자등록증/명함/계약서/세금계산서) 는 [README §2-B](README.md#2-b-첨부파일-사업자등록증명함계약서-마이그레이션-전략) 에 따라:

- PoC/dev: 생략 (필요 시 dummy 이미지 1~2장 시드)
- 운영 cutover: 매출 상위 30~50개 거래처만 사용자 수동 업로드
- 운영 중: 나머지는 lazy migration (필요 시점 보강)

자동 크롤링이 필요해지는 시점에 별도 슬라이스 MIG-1B 로 신설.
