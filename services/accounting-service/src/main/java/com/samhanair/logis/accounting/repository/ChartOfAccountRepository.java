package com.samhanair.logis.accounting.repository;

import com.samhanair.logis.accounting.domain.AccountCategory;
import com.samhanair.logis.accounting.domain.ChartOfAccount;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** ChartOfAccount — 한국 표준 계정과목 (Flyway V1 시드, code PK VARCHAR(6)). */
public interface ChartOfAccountRepository extends JpaRepository<ChartOfAccount, String> {

    /** code 오름차순 — 트리 화면 기본 정렬. */
    List<ChartOfAccount> findAllByOrderByCodeAsc();

    /** 7-그룹 별 조회. */
    List<ChartOfAccount> findByCategoryOrderByCodeAsc(AccountCategory category);
}
