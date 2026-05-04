package com.samhanair.logis.product.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samhanair.logis.product.ProductServiceApplication;
import com.samhanair.logis.product.domain.Category;
import com.samhanair.logis.product.repository.CategoryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Category 엔티티의 partial unique (code) 와 self-FK (parent_id) 의 DB-level 검증.
 *
 * <p>주의: "자식 존재 시 부모 삭제 차단" 비즈니스 룰은 application-level (CategoryService)
 * 에서 BusinessException 으로 처리되므로 여기서는 DB 레벨의 FK 자체만 검증한다 (Plan §3.4).
 */
@SpringBootTest(classes = ProductServiceApplication.class)
@Transactional
class CategoryRepositoryIT extends AbstractPostgresIT {

    @Autowired
    private CategoryRepository categoryRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void partialUniqueIndex_code_allowsReuseAfterSoftDelete() {
        Category first = categoryRepository.save(Category.create("TEST", "테스트", null, 100));
        categoryRepository.flush();

        first.markDeleted("test");
        categoryRepository.save(first);
        categoryRepository.flush();
        entityManager.clear();

        // 동일 code 재등록 — partial unique (WHERE is_deleted = FALSE) 덕분에 가능.
        Category reborn = categoryRepository.save(Category.create("TEST", "테스트(재)", null, 101));
        categoryRepository.flush();

        assertThat(reborn.getId()).isNotNull();
        assertThat(reborn.getId()).isNotEqualTo(first.getId());
    }

    @Test
    void selfFkAndDeleteSemantics() {
        Category parent = categoryRepository.save(Category.create("PARENT", "부모", null, 10));
        Category child = categoryRepository.save(Category.create("CHILD", "자식", parent, 11));
        categoryRepository.flush();

        // DB-level: 부모 행을 강제로 hard delete 하면 자식의 parent_id FK 가 위반된다.
        // (본 슬라이스는 soft-delete 만 사용하므로 실제 운영 경로는 아니지만, FK 자체가 살아있는지 검증)
        assertThatThrownBy(() -> {
            entityManager.createNativeQuery("DELETE FROM categories WHERE id = :id")
                    .setParameter("id", parent.getId())
                    .executeUpdate();
            entityManager.flush();
        }).isInstanceOfAny(
                DataIntegrityViolationException.class,
                org.springframework.orm.jpa.JpaSystemException.class,
                org.hibernate.exception.ConstraintViolationException.class
        );

        // 자식이 정상적으로 부모와 연결돼 있는지도 함께 검증.
        Category fetchedChild = categoryRepository.findById(child.getId()).orElseThrow();
        assertThat(fetchedChild.getParent()).isNotNull();
        assertThat(fetchedChild.getParent().getId()).isEqualTo(parent.getId());
    }
}
