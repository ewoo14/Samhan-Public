package com.samhanair.logis.accounting.report;

import com.samhanair.logis.accounting.client.PartnerLookupClient;
import com.samhanair.logis.accounting.domain.AccountCategory;
import com.samhanair.logis.accounting.domain.ChartOfAccount;
import com.samhanair.logis.accounting.repository.ChartOfAccountRepository;
import com.samhanair.logis.accounting.repository.JournalLineRepository;
import com.samhanair.logis.accounting.repository.JournalLineRepository.PartnerAccountTotal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계정명세서 Service.
 *
 * <p>POSTED 분개 라인을 기준일 이하로 GROUP BY 계정×거래처 집계하여, 거래처별 잔액
 * 스냅샷을 만든다. 기본 계정은 채권(외상매출금/받을어음/단기대여금/미수금)과
 * 채무(외상매입금/지급어음/미지급금/미지급비용)이다.
 *
 * <p>부호 규칙:
 * <ul>
 *   <li>차변성 계정(자산/비용): 잔액 = 차변 - 대변</li>
 *   <li>대변성 계정(부채/자본/수익): 잔액 = 대변 - 차변</li>
 * </ul>
 *
 * <p>거래처 UUID 는 내부 집계에만 사용하고 응답에는 거래처명만 포함한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountStatementService {

    private static final String ETC_PARTNER_NAME = "기타";
    private static final String UNRESOLVED_PARTNER_NAME = "(미조회)";
    private static final String UNKNOWN_ACCOUNT_NAME = "미정의 계정";

    private static final List<String> RECEIVABLE_ACCOUNT_CODES =
            List.of("110", "111", "114", "120");
    private static final List<String> PAYABLE_ACCOUNT_CODES =
            List.of("201", "202", "210", "212");
    private static final List<String> DEFAULT_ACCOUNT_CODES = List.of(
            "110", "111", "114", "120", "201", "202", "210", "212"
    );

    private final JournalLineRepository journalLineRepository;
    private final ChartOfAccountRepository chartOfAccountRepository;
    private final PartnerLookupClient partnerLookupClient;

    /**
     * 계정명세서를 조회한다.
     *
     * @param asOfDate 기준일
     * @param accountCode 선택 계정코드. null/blank 이면 채권·채무 계정 전체
     * @return 계정×거래처 잔액 스냅샷
     */
    public AccountStatementResponse findStatement(LocalDate asOfDate, String accountCode) {
        if (asOfDate == null) {
            throw new IllegalArgumentException("asOfDate 는 필수입니다");
        }

        String requestedAccountCode = normalizeAccountCode(accountCode);
        List<String> accountCodes = requestedAccountCode == null
                ? DEFAULT_ACCOUNT_CODES
                : List.of(requestedAccountCode);

        Map<String, ChartOfAccount> accounts = accountMap(accountCodes);
        Map<AccountPartnerKey, PartnerAccountTotal> rows = rowsByKey(
                journalLineRepository.aggregateAccountStatementByAccountPartner(accountCodes, asOfDate));
        Map<UUID, String> partnerNames = resolvePartnerNames(rows.keySet().stream()
                .map(AccountPartnerKey::partnerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));

        Map<String, List<AccountStatementResponse.AccountSection>> sectionsByGroup = new LinkedHashMap<>();
        Map<String, AccountStatementResponse.AmountSummary> subtotalByGroup = new LinkedHashMap<>();

        for (String code : accountCodes) {
            ChartOfAccount account = accounts.get(code);
            AccountCategory category = categoryOf(account, code);
            BalanceDirection direction = directionOf(category);
            String groupCode = groupCodeOf(requestedAccountCode, code, direction);

            List<AccountStatementResponse.Line> lines = rows.keySet().stream()
                    .filter(key -> code.equals(key.accountCode()))
                    .sorted(Comparator
                            .comparing((AccountPartnerKey key) -> partnerDisplayName(key.partnerId(), partnerNames))
                            .thenComparing(key -> key.partnerId() == null ? "" : key.partnerId().toString()))
                    .map(key -> buildLine(key, accountNameOf(account), category, rows.get(key), partnerNames))
                    .filter(line -> line.balance().signum() != 0)
                    .toList();

            if (lines.isEmpty()) {
                continue;
            }

            AccountStatementResponse.AmountSummary subtotal = sum(lines);
            AccountStatementResponse.AccountSection section =
                    new AccountStatementResponse.AccountSection(
                            code,
                            accountNameOf(account),
                            category,
                            category.getDisplayName(),
                            direction,
                            direction.getDisplayName(),
                            lines,
                            subtotal
                    );
            sectionsByGroup.computeIfAbsent(groupCode, ignored -> new ArrayList<>()).add(section);
            subtotalByGroup.merge(groupCode, subtotal, AccountStatementResponse.AmountSummary::plus);
        }

        List<AccountStatementResponse.AccountGroup> groups = new ArrayList<>();
        AccountStatementResponse.AmountSummary total = AccountStatementResponse.AmountSummary.zero();
        for (Map.Entry<String, List<AccountStatementResponse.AccountSection>> entry : sectionsByGroup.entrySet()) {
            String groupCode = entry.getKey();
            List<AccountStatementResponse.AccountSection> sections = entry.getValue();
            BalanceDirection direction = sections.get(0).balanceDirection();
            AccountStatementResponse.AmountSummary subtotal =
                    subtotalByGroup.getOrDefault(groupCode, AccountStatementResponse.AmountSummary.zero());
            groups.add(new AccountStatementResponse.AccountGroup(
                    groupCode,
                    groupNameOf(groupCode, direction),
                    direction,
                    sections,
                    subtotal
            ));
            total = total.plus(subtotal);
        }

        return new AccountStatementResponse(
                asOfDate,
                requestedAccountCode,
                groups,
                total,
                LocalDateTime.now()
        );
    }

    private AccountStatementResponse.Line buildLine(AccountPartnerKey key,
                                                    String accountName,
                                                    AccountCategory category,
                                                    PartnerAccountTotal row,
                                                    Map<UUID, String> partnerNames) {
        BigDecimal debit = row == null ? BigDecimal.ZERO : row.getDebitTotal();
        BigDecimal credit = row == null ? BigDecimal.ZERO : row.getCreditTotal();
        BigDecimal increase = isDebitBalanceCategory(category) ? debit : credit;
        BigDecimal decrease = isDebitBalanceCategory(category) ? credit : debit;
        BigDecimal balance = computeBalance(category, debit, credit);
        return new AccountStatementResponse.Line(
                key.accountCode(),
                accountName,
                partnerDisplayName(key.partnerId(), partnerNames),
                BigDecimal.ZERO,
                increase,
                decrease,
                debit,
                credit,
                balance
        );
    }

    private Map<AccountPartnerKey, PartnerAccountTotal> rowsByKey(List<PartnerAccountTotal> rows) {
        Map<AccountPartnerKey, PartnerAccountTotal> map = new LinkedHashMap<>();
        for (PartnerAccountTotal row : rows) {
            map.put(new AccountPartnerKey(row.getAccountCode(), row.getPartnerId()), row);
        }
        return map;
    }

    private Map<String, ChartOfAccount> accountMap(List<String> accountCodes) {
        Map<String, ChartOfAccount> map = new LinkedHashMap<>();
        chartOfAccountRepository.findAllById(accountCodes)
                .forEach(account -> map.put(account.getCode(), account));
        return map;
    }

    private Map<UUID, String> resolvePartnerNames(Set<UUID> partnerIds) {
        if (partnerIds == null || partnerIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> resolved = partnerLookupClient.findByPartnerIdsBatch(new ArrayList<>(partnerIds));
        return resolved == null ? Map.of() : resolved;
    }

    private AccountStatementResponse.AmountSummary sum(List<AccountStatementResponse.Line> lines) {
        BigDecimal opening = BigDecimal.ZERO;
        BigDecimal increase = BigDecimal.ZERO;
        BigDecimal decrease = BigDecimal.ZERO;
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        BigDecimal balance = BigDecimal.ZERO;
        for (AccountStatementResponse.Line line : lines) {
            opening = opening.add(line.openingBalance());
            increase = increase.add(line.increase());
            decrease = decrease.add(line.decrease());
            debit = debit.add(line.debitTotal());
            credit = credit.add(line.creditTotal());
            balance = balance.add(line.balance());
        }
        return new AccountStatementResponse.AmountSummary(opening, increase, decrease, debit, credit, balance);
    }

    private BigDecimal computeBalance(AccountCategory category, BigDecimal debit, BigDecimal credit) {
        return isDebitBalanceCategory(category) ? debit.subtract(credit) : credit.subtract(debit);
    }

    private boolean isDebitBalanceCategory(AccountCategory category) {
        return switch (category) {
            case ASSET, COST_OF_SALES, SGA, INCOME_TAX -> true;
            case LIABILITY, EQUITY, REVENUE, NON_OPERATING -> false;
        };
    }

    private BalanceDirection directionOf(AccountCategory category) {
        return isDebitBalanceCategory(category) ? BalanceDirection.DEBIT : BalanceDirection.CREDIT;
    }

    private AccountCategory categoryOf(ChartOfAccount account, String accountCode) {
        if (account != null) {
            return account.getCategory();
        }
        if (accountCode != null && accountCode.startsWith("2")) {
            return AccountCategory.LIABILITY;
        }
        if (accountCode != null && accountCode.startsWith("3")) {
            return AccountCategory.EQUITY;
        }
        if (accountCode != null && accountCode.startsWith("4")) {
            return AccountCategory.REVENUE;
        }
        if (accountCode != null && accountCode.startsWith("5")) {
            return AccountCategory.COST_OF_SALES;
        }
        if (accountCode != null && accountCode.startsWith("8")) {
            return AccountCategory.SGA;
        }
        if (accountCode != null && accountCode.startsWith("9")) {
            return AccountCategory.NON_OPERATING;
        }
        return AccountCategory.ASSET;
    }

    private String groupCodeOf(String requestedAccountCode, String accountCode, BalanceDirection direction) {
        if (requestedAccountCode != null) {
            return direction == BalanceDirection.DEBIT ? "DEBIT_BALANCE" : "CREDIT_BALANCE";
        }
        if (RECEIVABLE_ACCOUNT_CODES.contains(accountCode)) {
            return "RECEIVABLE";
        }
        if (PAYABLE_ACCOUNT_CODES.contains(accountCode)) {
            return "PAYABLE";
        }
        return direction == BalanceDirection.DEBIT ? "DEBIT_BALANCE" : "CREDIT_BALANCE";
    }

    private String groupNameOf(String groupCode, BalanceDirection direction) {
        return switch (groupCode) {
            case "RECEIVABLE" -> "채권";
            case "PAYABLE" -> "채무";
            case "DEBIT_BALANCE" -> "차변성 계정";
            case "CREDIT_BALANCE" -> "대변성 계정";
            default -> direction.getDisplayName();
        };
    }

    private String accountNameOf(ChartOfAccount account) {
        return account == null ? UNKNOWN_ACCOUNT_NAME : account.getName();
    }

    private String partnerDisplayName(UUID partnerId, Map<UUID, String> partnerNames) {
        if (partnerId == null) {
            return ETC_PARTNER_NAME;
        }
        String name = partnerNames.get(partnerId);
        return name == null || name.isBlank() ? UNRESOLVED_PARTNER_NAME : name;
    }

    private String normalizeAccountCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim();
    }

    private record AccountPartnerKey(String accountCode, UUID partnerId) {
    }
}
