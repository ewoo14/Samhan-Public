package com.samhanair.logis.partnerauth.repository;

import com.samhanair.logis.partnerauth.domain.PartnerAuth;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Soft-delete 필터는 entity 의 {@code @SQLRestriction} 으로 자동 적용. */
public interface PartnerAuthRepository extends JpaRepository<PartnerAuth, UUID> {

    Optional<PartnerAuth> findByBizNo(String bizNo);

    boolean existsByBizNo(String bizNo);
}
