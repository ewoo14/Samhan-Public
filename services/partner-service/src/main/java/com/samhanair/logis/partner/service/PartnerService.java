package com.samhanair.logis.partner.service;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.partner.domain.Partner;
import com.samhanair.logis.partner.dto.PartnerAdminRequest;
import com.samhanair.logis.partner.dto.PartnerInternalResponse;
import com.samhanair.logis.partner.repository.PartnerRepository;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
     * partnerCode N건 bulk lookup — Phase 9 W5 신규 (D-P9-16, BE 의견 3 채택).
     *
     * <p>dashboard-service 의 매출 집계 fan-out 단계에서 직렬 N회 RPC 회피용. 입력 컬렉션의 중복 코드는
     * Set 으로 정규화 (DB 조회 비용 절감). 미존재 코드는 결과에서 자동 누락 — 호출 측이 응답 partnerCode 로
     * 매칭하여 누락 분기 처리. 빈 컬렉션 시 빈 리스트 반환 (DB 조회 회피).
     *
     * <p>UUID 비공개 가드 — 응답 record 자체는 partnerId 를 보유하지만 internal endpoint 에서만
     * 노출되며, 호출 측 (dashboard) 이 사용자 응답 DTO 에 partnerId 를 첨부하지 않는다.
     *
     * @param partnerCodes 조회할 partnerCode 모음 (null/empty 시 빈 리스트, 중복 자동 정규화)
     * @return 매칭된 PartnerInternalResponse 리스트 (입력 순서 보장 X)
     */
    @Transactional(readOnly = true)
    public List<PartnerInternalResponse> findByCodes(Collection<String> partnerCodes) {
        if (partnerCodes == null || partnerCodes.isEmpty()) {
            return List.of();
        }
        Set<String> distinct = new HashSet<>(partnerCodes);
        distinct.removeIf(c -> c == null || c.isBlank());
        if (distinct.isEmpty()) {
            return List.of();
        }
        return partnerRepository.findAllByPartnerCodeIn(distinct).stream()
                .map(PartnerInternalResponse::from)
                .toList();
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
