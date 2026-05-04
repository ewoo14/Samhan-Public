package com.samhanair.logis.product.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 제품 판매 상태. soft-delete 와 직교(orthogonal): 삭제는 마스터 데이터 보존을 위해
 * {@code is_deleted} 플래그로, 단종은 별도 enum 으로 관리한다 (개발책임자 결재).
 */
@Getter
@RequiredArgsConstructor
public enum ProductStatus {
    ACTIVE("판매중"),
    DISCONTINUED("단종");

    private final String displayName;
}
