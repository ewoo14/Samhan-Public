package com.samhanair.logis.partner.service;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.partner.domain.Partner;
import com.samhanair.logis.partner.dto.PartnerAdminRequest;
import com.samhanair.logis.partner.repository.PartnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 거래처 마스터 라이프사이클 관리 — 등록 / 조회 / 프로필 수정 / 상태 전이 / 삭제 (soft).
 *
 * <p>본 service 는 마스터 정보만 책임. 신용한도 / 미수금 갱신은 {@link PartnerCreditService} 가 담당
 * (history append-only 일관성 확보).
 */
@Service
@RequiredArgsConstructor
public class PartnerService {

    private final PartnerRepository partnerRepository;

    /**
     * 신규 거래처 등록.
     *
     * @param req partnerCode / bizNo / name 필수, 나머지 선택. partnerCode + bizNo 중복 시 409 CONFLICT.
     * @return 영속화된 Partner
     */
    @Transactional
    public Partner register(PartnerAdminRequest req) {
        partnerRepository.findByPartnerCode(req.partnerCode()).ifPresent(p -> {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 사용 중인 partnerCode: " + req.partnerCode());
        });
        partnerRepository.findByBizNo(req.bizNo()).ifPresent(p -> {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 사용 중인 사업자번호: " + req.bizNo());
        });
        Partner partner = Partner.register(req.partnerCode(), req.bizNo(), req.name(),
                req.address(), req.phone(), req.creditLimit());
        return partnerRepository.save(partner);
    }

    /**
     * partnerCode 로 거래처 단건 조회. 미존재 시 404.
     *
     * <p>본 메서드는 internal endpoint (slip-service M5 lookup) 와 admin endpoint 양쪽에서 사용.
     */
    @Transactional(readOnly = true)
    public Partner findByCode(String partnerCode) {
        return partnerRepository.findByPartnerCode(partnerCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "해당 코드의 거래처를 찾을 수 없습니다: " + partnerCode));
    }

    /**
     * 거래처 프로필 수정 (name / address / phone 만). partnerCode / bizNo 는 식별자 — 변경 불가.
     */
    @Transactional
    public Partner updateProfile(String partnerCode, PartnerAdminRequest req) {
        Partner partner = findByCode(partnerCode);
        partner.updateProfile(req.name(), req.address(), req.phone());
        return partner;
    }

    /**
     * 거래처 soft-delete. 활성 row partial unique index 가 partnerCode 재사용을 허용한다.
     */
    @Transactional
    public void delete(String partnerCode, String actorUserId) {
        Partner partner = findByCode(partnerCode);
        partner.markDeleted(actorUserId);
    }

    /** 거래 일시 중지. */
    @Transactional
    public void suspend(String partnerCode) {
        findByCode(partnerCode).suspend();
    }

    /** 거래 재개. */
    @Transactional
    public void activate(String partnerCode) {
        findByCode(partnerCode).activate();
    }

    /** 거래 종료. */
    @Transactional
    public void terminate(String partnerCode) {
        findByCode(partnerCode).terminate();
    }
}
