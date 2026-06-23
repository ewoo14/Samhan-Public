package com.samhanair.logis.accounting.repository;

import com.samhanair.logis.accounting.domain.BankTransaction;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** 통장 입출금 거래 repository. */
public interface BankTransactionRepository
        extends JpaRepository<BankTransaction, UUID>, JpaSpecificationExecutor<BankTransaction> {

    boolean existsByExternalRefAndIsDeletedFalse(String externalRef);

    boolean existsByBankAccountLabelAndTransactedAtAndAmountAndExternalRefAndIsDeletedFalse(
            String bankAccountLabel,
            LocalDateTime transactedAt,
            BigDecimal amount,
            String externalRef);

    Optional<BankTransaction> findByExternalRefAndIsDeletedFalse(String externalRef);

    /** 매칭/해제 단건 식별 — V43 unique index 4-key 와 동일. */
    Optional<BankTransaction> findByBankAccountLabelAndTransactedAtAndAmountAndExternalRefAndIsDeletedFalse(
            String bankAccountLabel,
            LocalDateTime transactedAt,
            BigDecimal amount,
            String externalRef);

}
