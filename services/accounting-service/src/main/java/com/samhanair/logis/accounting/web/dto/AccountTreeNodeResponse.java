package com.samhanair.logis.accounting.web.dto;

import com.samhanair.logis.accounting.domain.AccountCategory;
import com.samhanair.logis.accounting.domain.ChartOfAccount;

/**
 * 계정 트리 1노드 응답. 트리 표시는 FE 가 parentCode 로 nest. UUID 노출 X (계정 코드만).
 */
public record AccountTreeNodeResponse(
        String code,
        String name,
        AccountCategory category,
        String categoryDisplayName,
        String parentCode,
        boolean isLeaf,
        int displayOrder
) {
    public static AccountTreeNodeResponse of(ChartOfAccount account) {
        return new AccountTreeNodeResponse(
                account.getCode(),
                account.getName(),
                account.getCategory(),
                account.getCategory().getDisplayName(),
                account.getParentCode(),
                account.isLeaf(),
                account.getDisplayOrder()
        );
    }
}
