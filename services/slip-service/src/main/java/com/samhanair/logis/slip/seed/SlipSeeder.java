package com.samhanair.logis.slip.seed;

import com.samhanair.logis.slip.domain.DeliveryTag;
import com.samhanair.logis.slip.domain.Slip;
import com.samhanair.logis.slip.domain.SlipLine;
import com.samhanair.logis.slip.domain.SlipStatus;
import com.samhanair.logis.slip.domain.SlipType;
import com.samhanair.logis.slip.repository.SlipRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * feature/local-test-setup Stage 2 — Slip 100건 + SlipLine ~300건 시드.
 *
 * <p>활성 조건 (이중 가드):
 * <ul>
 *   <li>{@link Profile @Profile("dev")} — local/dev 프로파일 한정</li>
 *   <li>{@link ConditionalOnProperty}({@code app.slip.seed-test-data=true}) — toggle 명시적 ON</li>
 * </ul>
 *
 * <p>분포:
 * <ul>
 *   <li>slipType: OUTBOUND 60 / INBOUND 30 / OUTBOUND+RETURN_RENTAL 10 (RETURN tag 는 INBOUND 전용 enum)</li>
 *   <li>status: DRAFT 10 / SAVED 15 / SENT 10 / ACCEPTED 10 / PROCESSING 10 / INSPECTING 10 /
 *       COMPLETED 10 / SHIPPING 5 / DELIVERED 10 / CONFIRMED 5 / REJECTED 5 = 100</li>
 *   <li>deliveryTag: DAY 50 (OUTBOUND) / STACK 10 (OUTBOUND, "NIGHT" 대체) / RETURN_RENTAL 10 (OUTBOUND) / null 30 (INBOUND)</li>
 *   <li>날짜: 2026-01-01 ~ 2026-05-09 분포 (일자별 1~5건, slipNo 결정적 채번)</li>
 * </ul>
 *
 * <p>도메인 메서드만 사용 — {@link Slip#createOutbound}, {@link Slip#createInbound},
 * {@link Slip#save}, {@link Slip#send}, {@link Slip#accept}, {@link Slip#process},
 * {@link Slip#complete}, {@link Slip#inspect}, {@link Slip#ship}, {@link Slip#deliver},
 * {@link Slip#confirm}, {@link Slip#reject}. 잘못된 전이 시도 시 BusinessException(CONFLICT) 던짐.
 *
 * <p>idempotency: {@code SlipRepository.findBySlipNo} EXISTS 체크 + 중복 시 skip. 안전 재실행.
 * UUID 비공개 가드 — 모든 외부 식별자는 slipNo / partnerCode / productCode 사용.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(value = "app.slip.seed-test-data", havingValue = "true")
@Order(20)
public class SlipSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SlipSeeder.class);

    /** Stage 1 partner 결정성 UUID prefix — partnerCode 가변. */
    private static final String PARTNER_UUID_PREFIX = "samhan-seed:partner:";
    /** Stage 1 product 결정성 UUID prefix — modelName 가변. */
    private static final String PRODUCT_UUID_PREFIX = "samhan-seed:product:";

    /** V2 시드 본사창고 UUID — slip 의 출고/입고 창고. */
    private static final UUID HQ_WAREHOUSE_ID =
            UUID.fromString("11111111-1111-1111-1111-000000000001");

    /** Stage 1 partner 시드 개수 (P-2026-0001 ~ P-2026-0050). */
    private static final int PARTNER_COUNT = 50;
    /** Stage 1 product 시드 개수 (TEST-MODEL-0001 ~ TEST-MODEL-0100). */
    private static final int PRODUCT_COUNT = 100;
    /** Stage 1 partner 비공개 식별자 패턴. */
    private static final String PARTNER_CODE_PATTERN = "P-2026-%04d";
    /** Stage 1 product 비공개 식별자 패턴. */
    private static final String PRODUCT_MODEL_NAME_PATTERN = "TEST-MODEL-%04d";

    /** OrgChartSeeder 16명 employee loginId 풀 — requesterId / acceptor / inspector 순환. */
    private static final List<String> EMPLOYEE_LOGIN_IDS = List.of(
            "kimmiseon", "janyeonggu", "obyeongseung", "hongjisu",
            "kimgicheol", "simmigwang", "jeongminguk", "leejiyong",
            "gyeonjinseong", "parkeunwoo", "sinhyeonmin",
            "leeseongmi", "heoyujin", "rahaeram", "kimeunji", "parkjisu");

    /** loginId → 한국어 이름 매핑 (requesterName / acceptorName 캐싱용). */
    private static final Map<String, String> EMPLOYEE_NAMES = Map.ofEntries(
            Map.entry("kimmiseon", "김미선"),
            Map.entry("janyeonggu", "장영구"),
            Map.entry("obyeongseung", "오병승"),
            Map.entry("hongjisu", "홍지수"),
            Map.entry("kimgicheol", "김기철"),
            Map.entry("simmigwang", "심미광"),
            Map.entry("jeongminguk", "정민국"),
            Map.entry("leejiyong", "이지용"),
            Map.entry("gyeonjinseong", "견진성"),
            Map.entry("parkeunwoo", "박은우"),
            Map.entry("sinhyeonmin", "신현민"),
            Map.entry("leeseongmi", "이성미"),
            Map.entry("heoyujin", "허유진"),
            Map.entry("rahaeram", "라해람"),
            Map.entry("kimeunji", "김은지"),
            Map.entry("parkjisu", "박지수"));

    /** 30건만 채울 프로젝트명 풀 — DAY/SAVED/SENT 등 일부 슬립에서 순환. */
    private static final List<String> PROJECT_NAMES = List.of(
            "삼성 강남점 신규", "LG 부산 리뉴얼", "현대 분당 본사", "SK 광화문 사옥",
            "신세계 강남점", "롯데 잠실 타워", "GS 칼텍스 사옥", "한화 본사 리모델링",
            "포스코 송도", "농협 본관", "KB 여의도 사옥", "신한 본점",
            "우리은행 본사", "삼성생명 강남", "한국전력 본관", "현대제철 당진",
            "대우조선 거제", "두산중공업 창원", "LG화학 대전", "롯데케미칼 울산",
            "SK이노베이션 울산", "GS칼텍스 여수", "현대오일 울산", "S-Oil 온산",
            "포스코건설 송도", "삼성물산 강남", "현대건설 계동", "대우건설 신문로",
            "DL E&C 광화문", "GS건설 종로");

    /** 10명 기사 풀 — DeliveryBatchSeeder 와 동일. */
    private static final List<String> DRIVER_NAMES = List.of(
            "김배송", "이운송", "박물류", "최운반", "정수송",
            "강택배", "조이동", "윤보내", "임가져", "한받기");

    /** Slice B 자동 그룹화의 키. SHIPPING+ 단계 OUTBOUND 슬립이 batch 와 매핑. */
    private static final String DRIVER_PHONE_PATTERN = "010-1000-%04d";

    private final SlipRepository slipRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public SlipSeeder(SlipRepository slipRepository) {
        this.slipRepository = slipRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        log.info("[SlipSeeder] Stage 2 시드 시작 — 100 slip + ~300 line + 11 status 분포");

        List<SlipSpec> specs = buildSpecs();
        if (specs.size() != 100) {
            throw new IllegalStateException("SlipSpec 분포 검증 실패 — 기대 100, 실제 " + specs.size());
        }

        Map<LocalDate, Integer> seqByDate = new HashMap<>();
        int created = 0;
        int skipped = 0;

        // 시드 슬립을 생성 순서대로 (DRAFT → CONFIRMED) 처리하기 위해 정렬 안 함 — spec idx 순.
        for (SlipSpec spec : specs) {
            LocalDate slipDate = computeSlipDate(spec.idx());
            int seqNo = seqByDate.merge(slipDate, 1, Integer::sum);
            String slipNo = formatSlipNo(slipDate, seqNo);

            if (slipRepository.findBySlipNo(slipNo).isPresent()) {
                skipped++;
                continue;
            }

            try {
                Slip slip = buildAndTransition(spec, slipNo, slipDate, seqNo);
                slipRepository.save(slip);
                created++;
            } catch (RuntimeException ex) {
                log.error("[SlipSeeder] 시드 실패 slipNo={} status={} : {}",
                        slipNo, spec.targetStatus(), ex.getMessage());
                throw ex;
            }
        }
        log.info("[SlipSeeder] 완료 — 신규 {}건, skip {}건 (총 {}건)",
                created, skipped, created + skipped);
    }

    /**
     * 100건 spec 분포 빌드 — slipType / deliveryTag / targetStatus 의 결정적 조합.
     * 분포 합계 검증을 위해 명시 ArrayList 빌드 (Map 기반 Map.of() 가독성 trade-off 회피).
     *
     * <p>비-CONFIRMED 단계 슬립은 type 별로 균등 배분. SHIPPING/DELIVERED 는 OUTBOUND 한정.
     * REJECTED 는 SENT/ACCEPTED/INSPECTING 단계에서 reject() 호출 — 본 시드는 ACCEPTED 단계에서 reject.
     */
    private List<SlipSpec> buildSpecs() {
        List<SlipSpec> specs = new ArrayList<>(100);
        int idx = 0;

        // ---- DAY tag OUTBOUND 50건 (DRAFT 5 + SAVED 8 + SENT 4 + ACCEPTED 4 + PROCESSING 4
        //                              + INSPECTING 4 + COMPLETED 4 + SHIPPING 5 + DELIVERED 7
        //                              + CONFIRMED 4 + REJECTED 1 = 50)
        idx = appendN(specs, idx, 5, SlipType.OUTBOUND, DeliveryTag.DAY, SlipStatus.DRAFT);
        idx = appendN(specs, idx, 8, SlipType.OUTBOUND, DeliveryTag.DAY, SlipStatus.SAVED);
        idx = appendN(specs, idx, 4, SlipType.OUTBOUND, DeliveryTag.DAY, SlipStatus.SENT);
        idx = appendN(specs, idx, 4, SlipType.OUTBOUND, DeliveryTag.DAY, SlipStatus.ACCEPTED);
        idx = appendN(specs, idx, 4, SlipType.OUTBOUND, DeliveryTag.DAY, SlipStatus.PROCESSING);
        idx = appendN(specs, idx, 4, SlipType.OUTBOUND, DeliveryTag.DAY, SlipStatus.INSPECTING);
        idx = appendN(specs, idx, 4, SlipType.OUTBOUND, DeliveryTag.DAY, SlipStatus.COMPLETED);
        idx = appendN(specs, idx, 5, SlipType.OUTBOUND, DeliveryTag.DAY, SlipStatus.SHIPPING);
        idx = appendN(specs, idx, 7, SlipType.OUTBOUND, DeliveryTag.DAY, SlipStatus.DELIVERED);
        idx = appendN(specs, idx, 4, SlipType.OUTBOUND, DeliveryTag.DAY, SlipStatus.CONFIRMED);
        idx = appendN(specs, idx, 1, SlipType.OUTBOUND, DeliveryTag.DAY, SlipStatus.REJECTED);

        // ---- STACK tag OUTBOUND 10건 ("NIGHT" 대체 — STACK 야적 도 OUTBOUND-only).
        //   DRAFT 1 / SAVED 2 / ACCEPTED 1 / PROCESSING 1 / INSPECTING 1 / COMPLETED 1
        //   / DELIVERED 2 / REJECTED 1 = 10
        idx = appendN(specs, idx, 1, SlipType.OUTBOUND, DeliveryTag.STACK, SlipStatus.DRAFT);
        idx = appendN(specs, idx, 2, SlipType.OUTBOUND, DeliveryTag.STACK, SlipStatus.SAVED);
        idx = appendN(specs, idx, 1, SlipType.OUTBOUND, DeliveryTag.STACK, SlipStatus.ACCEPTED);
        idx = appendN(specs, idx, 1, SlipType.OUTBOUND, DeliveryTag.STACK, SlipStatus.PROCESSING);
        idx = appendN(specs, idx, 1, SlipType.OUTBOUND, DeliveryTag.STACK, SlipStatus.INSPECTING);
        idx = appendN(specs, idx, 1, SlipType.OUTBOUND, DeliveryTag.STACK, SlipStatus.COMPLETED);
        idx = appendN(specs, idx, 2, SlipType.OUTBOUND, DeliveryTag.STACK, SlipStatus.DELIVERED);
        idx = appendN(specs, idx, 1, SlipType.OUTBOUND, DeliveryTag.STACK, SlipStatus.REJECTED);

        // ---- RETURN_RENTAL tag OUTBOUND 10건 (사용자 spec "OUTBOUND + RETURN tag" — RETURN 은 INBOUND-only enum,
        //   RETURN_RENTAL=반납 이 OUTBOUND 회수 의미로 적합).
        //   DRAFT 1 / SAVED 1 / SENT 2 / ACCEPTED 1 / PROCESSING 1 / INSPECTING 1
        //   / COMPLETED 1 / DELIVERED 1 / REJECTED 1 = 10
        idx = appendN(specs, idx, 1, SlipType.OUTBOUND, DeliveryTag.RETURN_RENTAL, SlipStatus.DRAFT);
        idx = appendN(specs, idx, 1, SlipType.OUTBOUND, DeliveryTag.RETURN_RENTAL, SlipStatus.SAVED);
        idx = appendN(specs, idx, 2, SlipType.OUTBOUND, DeliveryTag.RETURN_RENTAL, SlipStatus.SENT);
        idx = appendN(specs, idx, 1, SlipType.OUTBOUND, DeliveryTag.RETURN_RENTAL, SlipStatus.ACCEPTED);
        idx = appendN(specs, idx, 1, SlipType.OUTBOUND, DeliveryTag.RETURN_RENTAL, SlipStatus.PROCESSING);
        idx = appendN(specs, idx, 1, SlipType.OUTBOUND, DeliveryTag.RETURN_RENTAL, SlipStatus.INSPECTING);
        idx = appendN(specs, idx, 1, SlipType.OUTBOUND, DeliveryTag.RETURN_RENTAL, SlipStatus.COMPLETED);
        idx = appendN(specs, idx, 1, SlipType.OUTBOUND, DeliveryTag.RETURN_RENTAL, SlipStatus.DELIVERED);
        idx = appendN(specs, idx, 1, SlipType.OUTBOUND, DeliveryTag.RETURN_RENTAL, SlipStatus.REJECTED);

        // ---- INBOUND 30건 (no tag, INBOUND 단계는 SHIPPING/DELIVERED 미지원).
        //   DRAFT 3 / SAVED 4 / SENT 4 / ACCEPTED 4 / PROCESSING 4 / INSPECTING 4
        //   / COMPLETED 4 / CONFIRMED 1 / REJECTED 2 = 30
        idx = appendN(specs, idx, 3, SlipType.INBOUND, null, SlipStatus.DRAFT);
        idx = appendN(specs, idx, 4, SlipType.INBOUND, null, SlipStatus.SAVED);
        idx = appendN(specs, idx, 4, SlipType.INBOUND, null, SlipStatus.SENT);
        idx = appendN(specs, idx, 4, SlipType.INBOUND, null, SlipStatus.ACCEPTED);
        idx = appendN(specs, idx, 4, SlipType.INBOUND, null, SlipStatus.PROCESSING);
        idx = appendN(specs, idx, 4, SlipType.INBOUND, null, SlipStatus.INSPECTING);
        idx = appendN(specs, idx, 4, SlipType.INBOUND, null, SlipStatus.COMPLETED);
        idx = appendN(specs, idx, 1, SlipType.INBOUND, null, SlipStatus.CONFIRMED);
        idx = appendN(specs, idx, 2, SlipType.INBOUND, null, SlipStatus.REJECTED);

        return specs;
    }

    private int appendN(List<SlipSpec> specs, int idx, int count,
                        SlipType type, DeliveryTag tag, SlipStatus status) {
        for (int i = 0; i < count; i++) {
            specs.add(new SlipSpec(idx, type, tag, status));
            idx++;
        }
        return idx;
    }

    /**
     * Slip 1건을 spec 에 따라 build + transition.
     * 도메인 메서드만 사용 (createOutbound/createInbound, save, send, accept, process, inspect,
     * complete, ship, deliver, confirm, reject).
     */
    private Slip buildAndTransition(SlipSpec spec, String slipNo, LocalDate slipDate, int seqNo) {
        int partnerSeq = (spec.idx() % PARTNER_COUNT) + 1;
        String partnerCode = String.format(PARTNER_CODE_PATTERN, partnerSeq);
        UUID partnerId = deterministicUuid(PARTNER_UUID_PREFIX + partnerCode);
        String partnerName = "거래처-" + partnerCode;

        String requesterLoginId = EMPLOYEE_LOGIN_IDS.get(spec.idx() % EMPLOYEE_LOGIN_IDS.size());
        String memo = baseMemo(spec, slipNo);

        Slip slip;
        if (spec.type() == SlipType.OUTBOUND) {
            slip = Slip.createOutbound(slipNo, slipDate, seqNo,
                    HQ_WAREHOUSE_ID, null,
                    partnerId, partnerName, spec.tag(), memo, requesterLoginId);
        } else {
            slip = Slip.createInbound(slipNo, slipDate, seqNo,
                    HQ_WAREHOUSE_ID,
                    partnerId, partnerName, spec.tag(), memo, requesterLoginId);
        }

        // SHIPPING+ 단계 OUTBOUND 슬립은 driver 정보 필요 (ship() 후 도메인은 driver 검증 X 지만
        // SMS/링크발송 흐름 정합성을 위해 driver 정보 미리 set).
        if (spec.type() == SlipType.OUTBOUND
                && reachesShipping(spec.targetStatus())) {
            int driverSeq = (spec.idx() % DRIVER_NAMES.size()) + 1;
            slip.setDriverContact(
                    DRIVER_NAMES.get(driverSeq - 1),
                    String.format(DRIVER_PHONE_PATTERN, driverSeq));
        }

        // 라인 추가 — DRAFT 단계에서 1~5개. 결정적 = (spec.idx() % 5) + 1.
        int lineCount = (spec.idx() % 5) + 1;
        for (int li = 0; li < lineCount; li++) {
            int productSeq = ((spec.idx() * 7 + li * 3) % PRODUCT_COUNT) + 1;
            String modelName = String.format(PRODUCT_MODEL_NAME_PATTERN, productSeq);
            UUID productId = deterministicUuid(PRODUCT_UUID_PREFIX + modelName);
            String productName = "테스트제품-" + modelName;
            String specification = sampleSpecification(productSeq);
            int quantity = ((spec.idx() + li) % 10) + 1;  // 1~10
            BigDecimal unitPrice = computeUnitPrice(productSeq);
            String note = li == 0 ? "Stage 2 시드" : null;

            SlipLine line = SlipLine.create(slip, productId, productName, modelName,
                    specification, quantity, unitPrice, note);
            slip.addLine(line);
        }

        // 도메인 메서드 chain 으로 target status 까지 transition.
        applyTransitions(slip, spec);

        return slip;
    }

    /**
     * spec.targetStatus() 까지 도메인 메서드로 단계별 전이.
     * 잘못된 전이는 도메인이 BusinessException(CONFLICT) 를 던지므로 시드 빌드 자체가 실패.
     */
    private void applyTransitions(Slip slip, SlipSpec spec) {
        SlipStatus target = spec.targetStatus();
        if (target == SlipStatus.DRAFT) {
            return;
        }

        slip.save();
        if (target == SlipStatus.SAVED) return;

        slip.send();
        if (target == SlipStatus.SENT) return;

        if (target == SlipStatus.REJECTED) {
            // 다양화: ACCEPTED 단계까지 진전 후 reject (사용자 spec "memo 에 [반려: {사유}] prepend").
            slip.accept(EMPLOYEE_LOGIN_IDS.get((spec.idx() + 1) % EMPLOYEE_LOGIN_IDS.size()));
            slip.reject("재고 불일치 — 수량 재확인 필요");
            return;
        }

        slip.accept(EMPLOYEE_LOGIN_IDS.get((spec.idx() + 1) % EMPLOYEE_LOGIN_IDS.size()));
        if (target == SlipStatus.ACCEPTED) return;

        slip.process();
        if (target == SlipStatus.PROCESSING) return;

        slip.complete();   // PROCESSING → INSPECTING (도메인 의미상 "출고완료=검수단계 진입")
        if (target == SlipStatus.INSPECTING) return;

        slip.inspect(EMPLOYEE_LOGIN_IDS.get((spec.idx() + 2) % EMPLOYEE_LOGIN_IDS.size()));
        if (target == SlipStatus.COMPLETED) return;

        // OUTBOUND 만 SHIPPING/DELIVERED 단계 진입 가능 (INBOUND 는 COMPLETED → CONFIRMED 직행).
        if (spec.type() == SlipType.OUTBOUND) {
            slip.ship();
            if (target == SlipStatus.SHIPPING) return;

            slip.deliver();
            if (target == SlipStatus.DELIVERED) return;
        }

        slip.confirm();
        // CONFIRMED 가 최종.
    }

    private static boolean reachesShipping(SlipStatus target) {
        return target == SlipStatus.SHIPPING
                || target == SlipStatus.DELIVERED
                || target == SlipStatus.CONFIRMED;
    }

    /**
     * 슬립 날짜 결정성 — 2026-01-01 ~ 2026-05-09 (129일) 분포. idx % 129 일자 offset.
     * 같은 일자에 1~5건 spread (seqByDate 가 자동 채번).
     */
    private static LocalDate computeSlipDate(int idx) {
        LocalDate base = LocalDate.of(2026, 1, 1);
        int dayOffset = idx % 129;
        return base.plusDays(dayOffset);
    }

    /** "yyyy/MM/dd-NNN" 포맷. SlipNumberSequence 미경유 — 시드 결정적 채번. */
    private static String formatSlipNo(LocalDate slipDate, int seqNo) {
        return String.format("%04d/%02d/%02d-%03d",
                slipDate.getYear(), slipDate.getMonthValue(), slipDate.getDayOfMonth(), seqNo);
    }

    /**
     * 시드 메모 — 30건은 프로젝트명, 10건은 감리주소 표시, 나머지는 일반 메모.
     */
    private String baseMemo(SlipSpec spec, String slipNo) {
        StringBuilder sb = new StringBuilder("[Stage 2 시드] ");
        if (spec.idx() < 30) {
            sb.append("프로젝트=").append(PROJECT_NAMES.get(spec.idx()));
        } else if (spec.idx() < 40) {
            // 감리주소 10건 — 메모에 임베드 (별도 컬럼 없음 — 도메인 미보강 가드).
            sb.append("감리주소=서울시 강남구 테헤란로 ").append(100 + spec.idx()).append("길 ").append(spec.idx() + 1);
        } else {
            sb.append("표준 시드 슬립 idx=").append(spec.idx());
        }
        sb.append(" / 인수자=010-").append(String.format("%04d", 1000 + spec.idx() % 9000))
          .append("-").append(String.format("%04d", spec.idx() * 13 % 9000 + 1000));
        return sb.toString();
    }

    /**
     * 샘플 규격 — productSeq 기반 결정적 표본 (사용자 피드백 #4 Slice A 의 specification 컬럼).
     */
    private static String sampleSpecification(int productSeq) {
        String[] samples = {"220V", "380V", "4HP", "Φ80×L1200", "5kW",
                            "DC24V", "AC110V", "30A", "50Hz", "60Hz"};
        return samples[productSeq % samples.length];
    }

    /**
     * 결정적 단가 — productSeq 기반. 100,000 ~ 1,099,000 범위.
     * Stage 1 product.outboundPrice 시드 부재 시 fallback 결정성.
     */
    private static BigDecimal computeUnitPrice(int productSeq) {
        long base = 100_000L + (productSeq * 9973L) % 1_000_000L;
        // 1,000 단위로 round.
        return BigDecimal.valueOf((base / 1000L) * 1000L);
    }

    /** Type-3 (name-based MD5) UUID. */
    private static UUID deterministicUuid(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 시드 spec — slipType / deliveryTag / 최종 status 의 결정적 조합.
     *
     * @param idx       0~99 글로벌 순서 (slipDate / partnerCode / productSeq 결정 source)
     * @param type      OUTBOUND / INBOUND
     * @param tag       DAY / STACK / RETURN_RENTAL / null (INBOUND)
     * @param targetStatus 도메인 메서드 chain 으로 도달할 최종 status
     */
    private record SlipSpec(int idx, SlipType type, DeliveryTag tag, SlipStatus targetStatus) {}
}
