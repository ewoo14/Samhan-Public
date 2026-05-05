package com.samhanair.logis.partnerauth.repository;

import com.samhanair.logis.partnerauth.domain.PartnerSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartnerSessionRepository extends JpaRepository<PartnerSession, UUID> {

    Optional<PartnerSession> findByJti(String jti);
}
