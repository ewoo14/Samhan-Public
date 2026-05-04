package com.samhanair.logis.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.product.domain.Category;
import com.samhanair.logis.product.repository.CategoryRepository;
import com.samhanair.logis.product.web.dto.CategoryResponse;
import com.samhanair.logis.product.web.dto.CreateCategoryRequest;
import com.samhanair.logis.product.web.dto.UpdateCategoryRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService service;

    private Category hvac;
    private UUID hvacId;
    private Category indoor;
    private UUID indoorId;

    @BeforeEach
    void setUp() {
        hvac = Category.create("HVAC", "공조 (HVAC)", null, 1);
        hvacId = UUID.randomUUID();
        ReflectionTestUtils.setField(hvac, "id", hvacId);

        indoor = Category.create("INDOOR", "실내기", hvac, 1);
        indoorId = UUID.randomUUID();
        ReflectionTestUtils.setField(indoor, "id", indoorId);
    }

    @Test
    void getTree_buildsNestedHierarchy() {
        Category leaf = Category.create("INDOOR_WALL", "벽걸이형", indoor, 1);
        UUID leafId = UUID.randomUUID();
        ReflectionTestUtils.setField(leaf, "id", leafId);

        when(categoryRepository.findByParentIsNullOrderByDisplayOrderAsc()).thenReturn(List.of(hvac));
        when(categoryRepository.findByParent_IdOrderByDisplayOrderAsc(hvacId)).thenReturn(List.of(indoor));
        when(categoryRepository.findByParent_IdOrderByDisplayOrderAsc(indoorId)).thenReturn(List.of(leaf));
        when(categoryRepository.findByParent_IdOrderByDisplayOrderAsc(leafId)).thenReturn(List.of());

        List<CategoryResponse> tree = service.getTree();

        assertThat(tree).hasSize(1);
        CategoryResponse root = tree.get(0);
        assertThat(root.code()).isEqualTo("HVAC");
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).code()).isEqualTo("INDOOR");
        assertThat(root.children().get(0).children()).hasSize(1);
        assertThat(root.children().get(0).children().get(0).code()).isEqualTo("INDOOR_WALL");
    }

    @Test
    void create_underParent_succeeds() {
        when(categoryRepository.existsByCodeAndIsDeletedFalse("OUTDOOR")).thenReturn(false);
        when(categoryRepository.findById(hvacId)).thenReturn(Optional.of(hvac));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> {
            Category arg = inv.getArgument(0);
            ReflectionTestUtils.setField(arg, "id", UUID.randomUUID());
            return arg;
        });

        CategoryResponse response = service.create(
                new CreateCategoryRequest("OUTDOOR", "실외기", hvacId, 2));

        assertThat(response.code()).isEqualTo("OUTDOOR");
        assertThat(response.parentId()).isEqualTo(hvacId);
        assertThat(response.displayOrder()).isEqualTo(2);
    }

    @Test
    void create_duplicateCode_throwsConflict() {
        when(categoryRepository.existsByCodeAndIsDeletedFalse("HVAC")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateCategoryRequest("HVAC", "중복", null, 0)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void update_changesParent_isMoveOperation() {
        Category newParent = Category.create("OUTDOOR", "실외기", hvac, 2);
        UUID newParentId = UUID.randomUUID();
        ReflectionTestUtils.setField(newParent, "id", newParentId);

        when(categoryRepository.findById(indoorId)).thenReturn(Optional.of(indoor));
        when(categoryRepository.findById(newParentId)).thenReturn(Optional.of(newParent));

        service.update(indoorId, new UpdateCategoryRequest("실내기 변경", newParentId, 9));

        assertThat(indoor.getName()).isEqualTo("실내기 변경");
        assertThat(indoor.getParent().getId()).isEqualTo(newParentId);
        assertThat(indoor.getDisplayOrder()).isEqualTo(9);
    }

    @Test
    void update_selfReferenceParent_throwsInvalidInput() {
        when(categoryRepository.findById(indoorId)).thenReturn(Optional.of(indoor));

        assertThatThrownBy(() -> service.update(indoorId,
                new UpdateCategoryRequest(null, indoorId, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT));
    }

    @Test
    void delete_withChildren_throwsConflict() {
        when(categoryRepository.findById(hvacId)).thenReturn(Optional.of(hvac));
        when(categoryRepository.existsByParent_Id(hvacId)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(hvacId, "user-1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void delete_leafCategory_marksDeleted() {
        when(categoryRepository.findById(indoorId)).thenReturn(Optional.of(indoor));
        when(categoryRepository.existsByParent_Id(indoorId)).thenReturn(false);

        service.delete(indoorId, "user-1");

        assertThat(indoor.getIsDeleted()).isTrue();
        assertThat(indoor.getDeletedBy()).isEqualTo("user-1");
    }

    @Test
    void delete_notFound_throwsNotFound() {
        UUID missing = UUID.randomUUID();
        when(categoryRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(missing, "user-1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
    }
}
