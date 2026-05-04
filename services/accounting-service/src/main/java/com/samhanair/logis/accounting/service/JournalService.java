package com.samhanair.logis.accounting.service;

import com.samhanair.logis.accounting.domain.Journal;
import com.samhanair.logis.accounting.domain.JournalLine;
import com.samhanair.logis.accounting.domain.JournalSourceType;
import com.samhanair.logis.accounting.domain.JournalStatus;
import com.samhanair.logis.accounting.repository.JournalRepository;
import com.samhanair.logis.accounting.web.dto.CreateJournalLineRequest;
import com.samhanair.logis.accounting.web.dto.CreateJournalRequest;
import com.samhanair.logis.accounting.web.dto.JournalDetailResponse;
import com.samhanair.logis.accounting.web.dto.JournalResponse;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 분개장 service — 신규 / 페이지 조회 / 단건 / post / reverse.
 *
 * <p>라이프사이클 표 (Layer 4 의무, Plan §2):
 * <pre>
 *   create        : (없음) → DRAFT
 *   post          : DRAFT → POSTED (라인 차/대 합계 일치 검증, postedAt/By 기입)
 *   reverse       : POSTED → REVERSED (역분개 신규 Journal 생성 + linkReversal)
 * </pre>
 *
 * <p>POSTED 이후 직접 수정 불가 (Q7 — audit safe).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class JournalService {

    private final JournalRepository journalRepository;
    private final JournalNumberService journalNumberService;
    private final AccountService accountService;

    /**
     * 분개 신규 생성 (DRAFT). 라인 1개 이상 + accountCode leaf 검증 + 라인별 debit/credit 도메인 가드.
     *
     * @param request 헤더 + 라인 묶음
     * @return DRAFT 신규 분개 단건
     */
    public JournalDetailResponse create(CreateJournalRequest request) {
        String journalNo = journalNumberService.next(request.journalDate());
        Journal journal = Journal.create(journalNo, request.journalDate(), request.description(),
                JournalSourceType.MANUAL, null);

        int lineNo = 1;
        for (CreateJournalLineRequest lineReq : request.lines()) {
            accountService.requireLeafAccount(lineReq.accountCode());
            JournalLine line = JournalLine.create(journal, lineNo++, lineReq.accountCode(),
                    lineReq.debitAmount(), lineReq.creditAmount(), lineReq.partnerId(),
                    lineReq.memo());
            journal.addLine(line);
        }

        Journal saved = journalRepository.save(journal);
        return JournalDetailResponse.of(saved);
    }

    /** 페이지 조회 — from/to 일자 범위 + status 필터 (status null 이면 전체). */
    @Transactional(readOnly = true)
    public Page<JournalResponse> list(LocalDate from, LocalDate to, JournalStatus status,
                                      Pageable pageable) {
        return journalRepository.findByDateRangeAndStatus(from, to, status, pageable)
                .map(JournalResponse::of);
    }

    /** 단건 조회 (라인 포함). */
    @Transactional(readOnly = true)
    public JournalDetailResponse getOne(UUID id) {
        Journal journal = findOrThrow(id);
        return JournalDetailResponse.of(journal);
    }

    /**
     * 게시 — DRAFT → POSTED. 도메인의 {@link Journal#post(String)} 호출 (차/대 합계 검증 포함).
     *
     * @param id 분개 UUID
     * @param actorUserId 게시자 user-id (header X-User-Id)
     * @return POSTED 분개 단건
     */
    public JournalDetailResponse post(UUID id, String actorUserId) {
        Journal journal = findOrThrow(id);
        journal.post(actorUserId);
        return JournalDetailResponse.of(journal);
    }

    /**
     * 역분개 — POSTED → REVERSED. 원분개를 REVERSED 마킹한 뒤 차/대 swap 한 신규 Journal 을
     * 같은 일자에 자동 생성하여 POST 까지 수행. 양 분개가 서로 reversedJournalId 로 연결.
     *
     * <p>부수효과 (단일 트랜잭션):
     * <ol>
     *   <li>원분개 status REVERSED + reversedJournalId = 신규 Journal UUID</li>
     *   <li>신규 Journal: 동일 journalDate / description "[역분개] {원 description}" / sourceType MANUAL /
     *       sourceRefId = 원분개 UUID / 라인 차/대 swap / status POSTED</li>
     * </ol>
     *
     * @param id 원분개 UUID
     * @param actorUserId 역분개 게시자 user-id
     * @return 신규 역분개 단건 (원분개 ID 는 reversedJournalId 로 추적)
     */
    public JournalDetailResponse reverse(UUID id, String actorUserId) {
        Journal original = findOrThrow(id);
        // 원분개 상태 검증은 markReversed 안에서.
        String reverseNo = journalNumberService.next(original.getJournalDate());
        String reverseDesc = "[역분개] "
                + (original.getDescription() == null ? original.getJournalNo() : original.getDescription());
        Journal reversal = Journal.create(reverseNo, original.getJournalDate(), reverseDesc,
                JournalSourceType.MANUAL, original.getId());

        int lineNo = 1;
        for (JournalLine origLine : original.getLines()) {
            JournalLine swapped = JournalLine.create(reversal, lineNo++, origLine.getAccountCode(),
                    origLine.getCreditAmount(),  // swap
                    origLine.getDebitAmount(),   // swap
                    origLine.getPartnerId(),
                    "[역분개] " + (origLine.getMemo() == null ? "" : origLine.getMemo()));
            reversal.addLine(swapped);
        }
        reversal.post(actorUserId);
        Journal savedReversal = journalRepository.save(reversal);

        original.markReversed();
        original.linkReversal(savedReversal.getId());

        return JournalDetailResponse.of(savedReversal);
    }

    private Journal findOrThrow(UUID id) {
        return journalRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "존재하지 않는 분개입니다: " + id));
    }
}
