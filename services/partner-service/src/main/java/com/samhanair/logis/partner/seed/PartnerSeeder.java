package com.samhanair.logis.partner.seed;

import com.samhanair.logis.partner.domain.Partner;
import com.samhanair.logis.partner.repository.PartnerRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stage 1 (master data) local-test seed — 거래처 50개.
 *
 * <p>출처:
 * <ul>
 *     <li>이카운트 거래처 4 탭 캡처 (docs/migration/ecount-reference/091522~091604) — 27 필드 매핑</li>
 *     <li>memory feedback_uuid_no_user_visibility — partnerCode "P-2026-NNNN" 사용자 노출, UUID 비공개</li>
 *     <li>memory project_korean_accounting — 한국 표준 계정과목 (registrationDate 회계 원장 기준일)</li>
 * </ul>
 *
 * <p><b>이중 가드</b>: {@code @Profile("dev")} + {@code app.partner.seed-test-data=true} 둘 다 만족 시만 실행.
 * 운영 / staging 환경 데이터 오염 방지. application.yml 의 default 값은 {@code false}.
 *
 * <p><b>Idempotency</b>: {@link PartnerRepository#existsByPartnerCode(String)} 로 partnerCode 중복 확인 후
 * 이미 존재하면 skip. 부분 시드 (예: 30/50) 후 재실행 시 누락 분만 생성.
 *
 * <p><b>도메인 구성 + native INSERT</b>: 필드 값은 {@link Partner#register} factory 와
 * {@code updateBusinessProfile} / {@code updateContactChannels} / {@code updateAddresses} /
 * {@code updateCreditPolicy} / {@code suspend} 등 도메인 메서드로 구성한다. 영속화는 deterministic
 * UUID 명시를 위해 {@link NamedParameterJdbcTemplate} native INSERT 를 사용해 Hibernate
 * {@code @UuidGenerator} random v4 덮어쓰기를 회피한다.
 *
 * <p>50개 회사명은 한국 가상 HVAC 협력사 큐레이션 (실제 회사명 상표 침해 금지 — "(주)서울에어컨" 등 가공).
 * SUSPENDED 5건 (seq % 10 == 0).
 */
@Component
@Profile("dev")
@ConditionalOnProperty(value = "app.partner.seed-test-data", havingValue = "true")
public class PartnerSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PartnerSeeder.class);

    /** 50개 거래처 마스터 row. seq 1~50 결정적. */
    private static final List<SeedRow> ROWS = List.of(
            new SeedRow(1, "(주)서울에어컨", "홍길동", "제조업", "공조설비", "VIP거래처", "수도권"),
            new SeedRow(2, "한국공조시스템(주)", "김철수", "제조업", "냉난방기기", "VIP거래처", "수도권"),
            new SeedRow(3, "부산냉난방테크", "박영수", "건설업", "공조설비시공", "VIP거래처", "영남권"),
            new SeedRow(4, "광주에어시스템", "이미영", "도소매", "에어컨도매", "일반거래처", "호남권"),
            new SeedRow(5, "대구HVAC솔루션", "최정호", "제조업", "공조설비", "VIP거래처", "영남권"),
            new SeedRow(6, "인천공조산업", "정수민", "제조업", "냉난방기기", "일반거래처", "수도권"),
            new SeedRow(7, "울산냉난방엔지니어링", "강민준", "건설업", "공조설비시공", "VIP거래처", "영남권"),
            new SeedRow(8, "수원에어컨센터", "조은영", "도소매", "에어컨소매", "일반거래처", "수도권"),
            new SeedRow(9, "대전공조테크", "윤서준", "제조업", "공조설비", "일반거래처", "충청권"),
            new SeedRow(10, "(주)성남에어시스템", "장지훈", "도소매", "에어컨도매", "신규거래처", "수도권"),
            new SeedRow(11, "고양냉난방주식회사", "임채은", "제조업", "냉난방기기", "VIP거래처", "수도권"),
            new SeedRow(12, "용인HVAC산업", "한도윤", "건설업", "공조설비시공", "일반거래처", "수도권"),
            new SeedRow(13, "안양공조에너지", "오시우", "제조업", "공조설비", "일반거래처", "수도권"),
            new SeedRow(14, "부천에어테크", "신예린", "도소매", "에어컨도매", "신규거래처", "수도권"),
            new SeedRow(15, "남양주냉난방", "권하준", "도소매", "에어컨소매", "일반거래처", "수도권"),
            new SeedRow(16, "춘천공조설비", "황지유", "건설업", "공조설비시공", "일반거래처", "강원권"),
            new SeedRow(17, "원주에어컨공업", "서민서", "제조업", "냉난방기기", "신규거래처", "강원권"),
            new SeedRow(18, "강릉HVAC솔루션", "남이안", "건설업", "공조설비시공", "일반거래처", "강원권"),
            new SeedRow(19, "청주공조에너지", "유주원", "제조업", "공조설비", "VIP거래처", "충청권"),
            new SeedRow(20, "(주)천안냉난방", "문건우", "도소매", "에어컨도매", "일반거래처", "충청권"),
            new SeedRow(21, "전주에어시스템", "백서아", "건설업", "공조설비시공", "일반거래처", "호남권"),
            new SeedRow(22, "군산공조산업", "송태민", "제조업", "냉난방기기", "신규거래처", "호남권"),
            new SeedRow(23, "목포냉난방엔지니어링", "노수아", "건설업", "공조설비시공", "일반거래처", "호남권"),
            new SeedRow(24, "여수HVAC테크", "구민찬", "도소매", "에어컨도매", "신규거래처", "호남권"),
            new SeedRow(25, "포항에어컨주식회사", "허다은", "제조업", "공조설비", "일반거래처", "영남권"),
            new SeedRow(26, "경주공조설비", "심하랑", "건설업", "공조설비시공", "일반거래처", "영남권"),
            new SeedRow(27, "김해냉난방테크", "양시윤", "도소매", "에어컨소매", "일반거래처", "영남권"),
            new SeedRow(28, "양산에어솔루션", "전지호", "제조업", "냉난방기기", "신규거래처", "영남권"),
            new SeedRow(29, "거제공조산업", "한라엘", "건설업", "공조설비시공", "일반거래처", "영남권"),
            new SeedRow(30, "(주)창원HVAC", "강로아", "제조업", "공조설비", "VIP거래처", "영남권"),
            new SeedRow(31, "마산냉난방기기", "조이든", "도소매", "에어컨도매", "일반거래처", "영남권"),
            new SeedRow(32, "진주에어시스템", "고하율", "건설업", "공조설비시공", "일반거래처", "영남권"),
            new SeedRow(33, "통영공조테크", "임루나", "도소매", "에어컨소매", "신규거래처", "영남권"),
            new SeedRow(34, "안동HVAC공업", "박이찬", "제조업", "냉난방기기", "일반거래처", "영남권"),
            new SeedRow(35, "구미에어컨산업", "홍하윤", "제조업", "공조설비", "VIP거래처", "영남권"),
            new SeedRow(36, "포천공조엔지니어링", "최라온", "건설업", "공조설비시공", "일반거래처", "수도권"),
            new SeedRow(37, "의정부냉난방", "장유나", "도소매", "에어컨도매", "신규거래처", "수도권"),
            new SeedRow(38, "동두천에어솔루션", "윤주안", "제조업", "냉난방기기", "일반거래처", "수도권"),
            new SeedRow(39, "양주공조설비", "정아인", "건설업", "공조설비시공", "일반거래처", "수도권"),
            new SeedRow(40, "(주)파주HVAC", "이서윤", "제조업", "공조설비", "VIP거래처", "수도권"),
            new SeedRow(41, "광명냉난방테크", "김라임", "도소매", "에어컨소매", "일반거래처", "수도권"),
            new SeedRow(42, "시흥에어컨공업", "박서후", "제조업", "공조설비", "일반거래처", "수도권"),
            new SeedRow(43, "하남공조산업", "강이도", "건설업", "공조설비시공", "신규거래처", "수도권"),
            new SeedRow(44, "구리에어시스템", "조하린", "도소매", "에어컨도매", "일반거래처", "수도권"),
            new SeedRow(45, "오산냉난방", "최태오", "제조업", "냉난방기기", "일반거래처", "수도권"),
            new SeedRow(46, "안성HVAC솔루션", "정나린", "건설업", "공조설비시공", "VIP거래처", "수도권"),
            new SeedRow(47, "이천공조에너지", "한이서", "제조업", "공조설비", "일반거래처", "수도권"),
            new SeedRow(48, "여주에어컨테크", "임지율", "도소매", "에어컨소매", "신규거래처", "수도권"),
            new SeedRow(49, "광양공조산업", "유다온", "건설업", "공조설비시공", "일반거래처", "호남권"),
            new SeedRow(50, "(주)순천냉난방", "오해린", "제조업", "냉난방기기", "VIP거래처", "호남권")
    );

    private final PartnerRepository partnerRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PartnerSeeder(PartnerRepository partnerRepository,
                         NamedParameterJdbcTemplate jdbcTemplate) {
        this.partnerRepository = partnerRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(String... args) {
        int created = 0;
        int skipped = 0;
        for (SeedRow row : ROWS) {
            String partnerCode = String.format("P-2026-%04d", row.seq());
            if (partnerRepository.existsByPartnerCode(partnerCode)) {
                skipped++;
                log.debug("Skipping seed (already present): {}", partnerCode);
                continue;
            }
            try {
                Partner partner = buildPartner(row, partnerCode);
                // PM 통합 fix — Stage 2/3/4 seeder 와 cross-service join 위해 deterministic UUID 강제 주입.
                // 이는 slip.partnerId / journal.partnerId 등이 partnerCode 기반 결정 UUID 와 매칭 되도록 함.
                insertPartnerNative(deterministicId("partner", partnerCode), partner);
                created++;
            } catch (RuntimeException ex) {
                log.error("Failed to seed partner {}: {}", partnerCode, ex.getMessage(), ex);
            }
        }
        log.info("PartnerSeeder created {} partners (skipped {}, total {})",
                created, skipped, ROWS.size());
    }

    /** seed row → 도메인 메서드 chain. */
    private Partner buildPartner(SeedRow row, String partnerCode) {
        int seq = row.seq();
        String bizNo = makeBizNo(seq);
        String phone = makePhone("02", seq);
        BigDecimal creditLimit = makeCreditLimit(seq);

        Partner partner = Partner.register(
                partnerCode,
                bizNo,
                row.name(),
                makeAddress1(seq, row.region()),
                phone,
                creditLimit);

        // 사업자 정보 (대표자/업태/종목/종사업장 — 10건만 종사업장 보유)
        String subBizNo = (seq % 5 == 0) ? String.format("%04d", seq) : null;
        partner.updateBusinessProfile(row.representative(), row.businessType(),
                row.industry(), subBizNo);

        // 연락처 (FAX/email/email2/mobile — email2 는 15건만)
        String email = "info" + seq + "@samhan-test.com";
        String email2 = (seq <= 15) ? "tax" + seq + "@samhan-test.com" : null;
        partner.updateContactChannels(makePhone("02", seq + 1000), email, email2, makeMobile(seq));

        // 주소 (배송지는 30건만)
        String zipCode1 = String.format("%05d", 10000 + seq * 7);
        String zipCode2 = (seq <= 30) ? String.format("%05d", 20000 + seq * 11) : null;
        String address2 = (seq <= 30) ? makeAddress2(seq, row.region()) : null;
        partner.updateAddresses(zipCode1, makeAddress1(seq, row.region()), zipCode2, address2);

        // 검색 키워드
        partner.updateSearchKeyword(row.name() + " " + bizNo + " " + phone);

        // 분류 + website (10건만 website)
        String website = (seq <= 10) ? "https://samhan-test-" + seq + ".co.kr" : null;
        partner.updateClassification(row.partnerGroup1(), row.partnerGroup2(), website);

        // 여신/단가 정책
        String salesPriceGroup = mapSalesPriceGroup(row.partnerGroup1());
        int creditPeriod = pickCyclic(seq, 30, 60, 90);
        int paymentDue = pickCyclic(seq, 30, 45, 60);
        BigDecimal outboundAdj = BigDecimal.valueOf(seq % 6).multiply(new BigDecimal("0.01"))
                .setScale(4, RoundingMode.HALF_UP); // 0~5%
        BigDecimal inboundAdj = BigDecimal.valueOf((seq + 3) % 6).multiply(new BigDecimal("0.01"))
                .setScale(4, RoundingMode.HALF_UP);
        partner.updateCreditPolicy(
                "기본설정", "기본설정", "기본설정", "기본설정",
                salesPriceGroup, "기본구매단가",
                outboundAdj, inboundAdj,
                creditPeriod, paymentDue);

        // 통화 / 출하대상 (40건 true / 10건 false)
        partner.changeCurrency("KRW");
        partner.changeShipmentTarget(seq % 5 != 0); // 10건 false (5,10,15,20,25,30,35,40,45,50)

        // 등록일자 — 2024-01-01 ~ 2026-04-30 분포
        partner.changeRegistrationDate(makeRegistrationDate(seq));

        // SUSPENDED 5건 (seq=10/20/30/40/50)
        if (seq % 10 == 0) {
            partner.suspend();
        }
        return partner;
    }

    // ============================================================
    // 결정적 generator (모든 값 seq 기반 — 같은 seed 재실행 시 동일 데이터)
    // ============================================================

    /**
     * 한국 사업자번호 10자리 결정적 생성 (XXX-XX-XXXXX 형식).
     * 검증식 미적용 (seed 데이터 — 실 NTS 검증 X). seq 별 unique 보장.
     */
    private static String makeBizNo(int seq) {
        // 100~999 범위 prefix + seq 기반 mid + tail
        int prefix = 100 + (seq * 13) % 900;     // 100~999
        int mid = (seq * 7) % 100;                // 0~99
        int tail = 10000 + (seq * 31) % 90000;    // 10000~99999
        return String.format("%03d-%02d-%05d", prefix, mid, tail);
    }

    private static String makePhone(String areaCode, int seq) {
        int mid = 1000 + (seq * 17) % 9000;
        int tail = 1000 + (seq * 41) % 9000;
        return String.format("%s-%04d-%04d", areaCode, mid, tail);
    }

    private static String makeMobile(int seq) {
        int mid = 1000 + (seq * 19) % 9000;
        int tail = 1000 + (seq * 47) % 9000;
        return String.format("010-%04d-%04d", mid, tail);
    }

    private static BigDecimal makeCreditLimit(int seq) {
        // 100만 ~ 5천만 결정적 분포 (seq 기반 5단계)
        long[] bands = {1_000_000L, 5_000_000L, 10_000_000L, 30_000_000L, 50_000_000L};
        return BigDecimal.valueOf(bands[seq % bands.length]);
    }

    private static String mapSalesPriceGroup(String partnerGroup1) {
        return switch (partnerGroup1) {
            case "VIP거래처" -> "VIP단가";
            case "신규거래처" -> "신규단가";
            default -> "일반단가";
        };
    }

    private static int pickCyclic(int seq, int a, int b, int c) {
        return switch (seq % 3) {
            case 0 -> a;
            case 1 -> b;
            default -> c;
        };
    }

    private static String makeAddress1(int seq, String region) {
        String city = switch (region) {
            case "수도권" -> "서울특별시 강남구 테헤란로";
            case "영남권" -> "부산광역시 해운대구 센텀중앙로";
            case "호남권" -> "광주광역시 서구 상무중앙로";
            case "충청권" -> "대전광역시 유성구 대학로";
            case "강원권" -> "강원도 춘천시 중앙로";
            default -> "서울특별시 종로구 종로";
        };
        return city + " " + (100 + seq) + "번길 " + (seq % 50 + 1);
    }

    private static String makeAddress2(int seq, String region) {
        String city = switch (region) {
            case "수도권" -> "경기도 화성시 동탄대로";
            case "영남권" -> "경상남도 김해시 김해대로";
            case "호남권" -> "전라북도 군산시 해망로";
            case "충청권" -> "충청남도 천안시 두정로";
            case "강원권" -> "강원도 원주시 단계로";
            default -> "경기도 성남시 분당구 정자로";
        };
        return city + " " + (200 + seq) + "번길 " + (seq % 30 + 1) + " (창고)";
    }

    private static LocalDate makeRegistrationDate(int seq) {
        // 2024-01-01 ~ 2026-04-30 = 851일 분포 (seq 1~50 균등)
        LocalDate base = LocalDate.of(2024, 1, 1);
        return base.plusDays((seq - 1) * 17L); // 17 일씩 → 50 * 17 = 850일 (2026-04-30 직전)
    }

    /** seed 행 정의. */
    private record SeedRow(
            int seq,
            String name,
            String representative,
            String businessType,
            String industry,
            String partnerGroup1,
            String region) {

        String partnerGroup2() {
            return region;
        }
    }

    /**
     * {@code samhan-seed:<type>:<key>} 결정적 UUID 도출 — Stage 1/2/3/4 seeder
     * 모두 동일 namespace 패턴 사용 (cross-stage 참조 정합).
     */
    private static UUID deterministicId(String type, String key) {
        return UUID.nameUUIDFromBytes(
                ("samhan-seed:" + type + ":" + key).getBytes(StandardCharsets.UTF_8));
    }

    /** Hibernate 의 {@code @UuidGenerator} 가 random UUID 부여하기 전에 결정 UUID 강제 주입. */
    private void insertPartnerNative(UUID id, Partner partner) {
        LocalDateTime now = LocalDateTime.now();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("partnerCode", partner.getPartnerCode())
                .addValue("bizNo", partner.getBizNo())
                .addValue("name", partner.getName())
                .addValue("address", partner.getAddress())
                .addValue("phone", partner.getPhone())
                .addValue("creditLimit", partner.getCreditLimit())
                .addValue("outstandingBalance", partner.getOutstandingBalance())
                .addValue("status", partner.getStatus().name())
                .addValue("subBizNo", partner.getSubBizNo())
                .addValue("representative", partner.getRepresentative())
                .addValue("businessType", partner.getBusinessType())
                .addValue("industry", partner.getIndustry())
                .addValue("fax", partner.getFax())
                .addValue("email", partner.getEmail())
                .addValue("email2", partner.getEmail2())
                .addValue("mobile", partner.getMobile())
                .addValue("zipCode1", partner.getZipCode1())
                .addValue("address1", partner.getAddress1())
                .addValue("zipCode2", partner.getZipCode2())
                .addValue("address2", partner.getAddress2())
                .addValue("searchKeyword", partner.getSearchKeyword())
                .addValue("partnerGroup1", partner.getPartnerGroup1())
                .addValue("partnerGroup2", partner.getPartnerGroup2())
                .addValue("website", partner.getWebsite())
                .addValue("currency", partner.getCurrency())
                .addValue("shipmentTarget", partner.getShipmentTarget())
                .addValue("salesType", partner.getSalesType())
                .addValue("purchaseType", partner.getPurchaseType())
                .addValue("receivableNoMgmt", partner.getReceivableNoMgmt())
                .addValue("payableNoMgmt", partner.getPayableNoMgmt())
                .addValue("outboundAdjustmentRate", partner.getOutboundAdjustmentRate())
                .addValue("inboundAdjustmentRate", partner.getInboundAdjustmentRate())
                .addValue("salesPriceGroup", partner.getSalesPriceGroup())
                .addValue("purchasePriceGroup", partner.getPurchasePriceGroup())
                .addValue("creditPeriodDays", partner.getCreditPeriodDays())
                .addValue("paymentDueDays", partner.getPaymentDueDays())
                .addValue("registrationDate", partner.getRegistrationDate())
                .addValue("transferInfo", partner.getTransferInfo())
                .addValue("note", partner.getNote())
                .addValue("managerName", partner.getManagerName())
                .addValue("createdAt", now)
                .addValue("createdBy", "system")
                .addValue("isDeleted", false);

        jdbcTemplate.update("""
                INSERT INTO partners (
                    id, partner_code, biz_no, name, address, phone,
                    credit_limit, outstanding_balance, status,
                    sub_biz_no, representative, business_type, industry,
                    fax, email, email2, mobile,
                    zip_code1, address1, zip_code2, address2,
                    search_keyword, partner_group1, partner_group2, website,
                    currency, shipment_target, sales_type, purchase_type,
                    receivable_no_mgmt, payable_no_mgmt,
                    outbound_adjustment_rate, inbound_adjustment_rate,
                    sales_price_group, purchase_price_group,
                    credit_period_days, payment_due_days, registration_date,
                    transfer_info, note, manager_name,
                    created_at, created_by, is_deleted
                ) VALUES (
                    :id, :partnerCode, :bizNo, :name, :address, :phone,
                    :creditLimit, :outstandingBalance, :status,
                    :subBizNo, :representative, :businessType, :industry,
                    :fax, :email, :email2, :mobile,
                    :zipCode1, :address1, :zipCode2, :address2,
                    :searchKeyword, :partnerGroup1, :partnerGroup2, :website,
                    :currency, :shipmentTarget, :salesType, :purchaseType,
                    :receivableNoMgmt, :payableNoMgmt,
                    :outboundAdjustmentRate, :inboundAdjustmentRate,
                    :salesPriceGroup, :purchasePriceGroup,
                    :creditPeriodDays, :paymentDueDays, :registrationDate,
                    :transferInfo, :note, :managerName,
                    :createdAt, :createdBy, :isDeleted
                )
                """, params);
    }

}
