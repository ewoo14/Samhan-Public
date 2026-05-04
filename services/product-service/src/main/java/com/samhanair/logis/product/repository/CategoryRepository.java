package com.samhanair.logis.product.repository;

import com.samhanair.logis.product.domain.Category;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Soft-delete is enforced at the entity level via @SQLRestriction on {@link Category}. */
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    Optional<Category> findByCode(String code);

    boolean existsByCodeAndIsDeletedFalse(String code);

    /** 루트 카테고리 (parent_id IS NULL) — 트리 빌드 진입점. */
    List<Category> findByParentIsNullOrderByDisplayOrderAsc();

    /** 특정 부모의 직속 자식들 — 트리 재귀 빌드시 사용. */
    List<Category> findByParent_IdOrderByDisplayOrderAsc(UUID parentId);

    /** 자식 존재 여부 — 카테고리 삭제 가능 판정. */
    boolean existsByParent_Id(UUID parentId);
}
