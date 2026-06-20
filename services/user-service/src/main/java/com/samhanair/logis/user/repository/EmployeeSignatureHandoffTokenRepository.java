package com.samhanair.logis.user.repository;

import com.samhanair.logis.user.domain.EmployeeSignatureHandoffToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 핸드오프 토큰 lookup — soft-delete 는 @SQLRestriction 으로 엔티티 레벨 강제. */
public interface EmployeeSignatureHandoffTokenRepository
        extends JpaRepository<EmployeeSignatureHandoffToken, UUID> {

    /** 공개 제출 토큰 검증 — base64url 토큰 단건 lookup. */
    Optional<EmployeeSignatureHandoffToken> findByToken(String token);

    /** 공개 제출 토큰의 사원 id 만 projection 조회 — 토큰 엔티티를 영속성 컨텍스트에 올리지 않는다. */
    @Query("select t.employeeId from EmployeeSignatureHandoffToken t where t.token = :token")
    Optional<UUID> findEmployeeIdByToken(@Param("token") String token);

    /** 공개 제출 single-use 경합 방지 — 토큰 행을 잠근 뒤 usedAt 를 확인한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM EmployeeSignatureHandoffToken t WHERE t.token = :token")
    Optional<EmployeeSignatureHandoffToken> findByTokenForUpdate(@Param("token") String token);

    /** 재발급 시 동일 사원 미사용 토큰 무효화 대상 조회. */
    List<EmployeeSignatureHandoffToken> findAllByEmployeeIdAndUsedAtIsNull(UUID employeeId);
}
