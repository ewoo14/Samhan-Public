package com.samhanair.logis.slip.repository;

import com.samhanair.logis.slip.domain.Slip;
import com.samhanair.logis.slip.domain.SlipStatus;
import com.samhanair.logis.slip.domain.SlipType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Slip 헤더 — 단건/필터 페이지 조회. partial unique 는 {@code slip_no} 컬럼에 적용 (V1 SQL). */
public interface SlipRepository extends JpaRepository<Slip, UUID> {

    /** 전표번호({@code yyyy/MM/dd-NNN}) 단건 조회. soft-delete 제외. */
    Optional<Slip> findBySlipNo(String slipNo);

    /** 상태별 페이지 조회. soft-delete 제외. */
    Page<Slip> findAllByStatusAndIsDeletedFalse(SlipStatus status, Pageable pageable);

    /** slipType 별 페이지 조회. soft-delete 제외. */
    Page<Slip> findAllBySlipTypeAndIsDeletedFalse(SlipType slipType, Pageable pageable);

    /** slipType + status 동시 필터 페이지. soft-delete 제외. */
    Page<Slip> findAllBySlipTypeAndStatusAndIsDeletedFalse(SlipType slipType, SlipStatus status, Pageable pageable);

    /** 활성 전체 페이지. soft-delete 제외. */
    Page<Slip> findAllByIsDeletedFalse(Pageable pageable);
}
