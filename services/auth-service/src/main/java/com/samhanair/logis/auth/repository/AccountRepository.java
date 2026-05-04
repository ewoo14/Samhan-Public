package com.samhanair.logis.auth.repository;

import com.samhanair.logis.auth.domain.Account;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** JPA repository for {@link Account}. Soft-delete filter is applied at the entity level. */
public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);
}
