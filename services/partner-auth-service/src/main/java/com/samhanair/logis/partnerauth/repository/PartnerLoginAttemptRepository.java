package com.samhanair.logis.partnerauth.repository;

import com.samhanair.logis.partnerauth.domain.PartnerLoginAttempt;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartnerLoginAttemptRepository extends JpaRepository<PartnerLoginAttempt, UUID> {

    List<PartnerLoginAttempt> findTop20ByBizNoOrderByAttemptedAtDesc(String bizNo);
}
