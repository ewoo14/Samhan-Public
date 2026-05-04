package com.samhanair.logis.product.service;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.product.domain.Category;
import com.samhanair.logis.product.repository.CategoryRepository;
import com.samhanair.logis.product.web.dto.CategoryResponse;
import com.samhanair.logis.product.web.dto.CreateCategoryRequest;
import com.samhanair.logis.product.web.dto.UpdateCategoryRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카테고리 트리 CRUD. 트리는 메모리에서 재귀 조립한다 (depth bounded by 운영 정책).
 * 자식이 존재하는 카테고리 삭제는 {@link ErrorCode#CONFLICT}.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> getTree() {
        List<Category> roots = categoryRepository.findByParentIsNullOrderByDisplayOrderAsc();
        List<CategoryResponse> tree = new ArrayList<>(roots.size());
        for (Category root : roots) {
            tree.add(buildSubtree(root));
        }
        return tree;
    }

    public CategoryResponse create(CreateCategoryRequest req) {
        if (categoryRepository.existsByCodeAndIsDeletedFalse(req.code())) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 사용 중인 카테고리 코드입니다: " + req.code());
        }
        Category parent = null;
        if (req.parentId() != null) {
            parent = categoryRepository.findById(req.parentId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "상위 카테고리를 찾을 수 없습니다"));
        }
        Category saved = categoryRepository.save(Category.create(req.code(), req.name(), parent, req.displayOrder()));
        return CategoryResponse.leaf(saved);
    }

    public CategoryResponse update(UUID id, UpdateCategoryRequest req) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "카테고리를 찾을 수 없습니다"));

        if (req.name() != null) {
            category.rename(req.name());
        }
        if (req.parentId() != null) {
            if (req.parentId().equals(id)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "자기 자신을 상위로 지정할 수 없습니다");
            }
            Category parent = categoryRepository.findById(req.parentId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "상위 카테고리를 찾을 수 없습니다"));
            category.changeParent(parent);
        }
        if (req.displayOrder() != null) {
            category.changeDisplayOrder(req.displayOrder());
        }
        return CategoryResponse.leaf(category);
    }

    public void delete(UUID id, String callerId) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "카테고리를 찾을 수 없습니다"));
        if (categoryRepository.existsByParent_Id(id)) {
            throw new BusinessException(ErrorCode.CONFLICT, "자식 카테고리가 있어 삭제할 수 없습니다");
        }
        category.markDeleted(callerId == null ? "system" : callerId);
    }

    private CategoryResponse buildSubtree(Category node) {
        List<Category> children = categoryRepository.findByParent_IdOrderByDisplayOrderAsc(node.getId());
        if (children.isEmpty()) {
            return CategoryResponse.leaf(node);
        }
        List<CategoryResponse> childResponses = new ArrayList<>(children.size());
        for (Category child : children) {
            childResponses.add(buildSubtree(child));
        }
        return CategoryResponse.withChildren(node, childResponses);
    }
}
