package com.samhanair.logis.inventory.service;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.inventory.domain.Warehouse;
import com.samhanair.logis.inventory.repository.WarehouseRepository;
import com.samhanair.logis.inventory.web.dto.AdminWarehouseListResponse;
import com.samhanair.logis.inventory.web.dto.CreateWarehouseRequest;
import com.samhanair.logis.inventory.web.dto.UpdateWarehouseRequest;
import com.samhanair.logis.inventory.web.dto.WarehouseResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 창고 마스터 CRUD + soft-delete. */
@Service
@Transactional
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;

    /**
     * 활성 창고 전체를 displayOrder ASC 로 반환한다 (soft-deleted 제외).
     *
     * @return 응답 DTO 리스트 (없으면 빈 리스트)
     */
    @Transactional(readOnly = true)
    public List<WarehouseResponse> listAll() {
        return warehouseRepository.findAllByIsDeletedFalseOrderByDisplayOrderAsc().stream()
                .map(WarehouseResponse::from)
                .toList();
    }

    /**
     * 창고 admin 검색 — Phase 10 P0-5.
     *
     * <p>q (code / name / address LIKE) + 페이지네이션. q 가 null/blank 시 미적용.
     * 활성 창고만 ({@code @SQLRestriction("is_deleted = false")}) 자동 필터.
     *
     * @param q 검색어 (옵션)
     * @param pageable 페이지 / 정렬
     * @return AdminWarehouseListResponse — items / total / page / size
     */
    @Transactional(readOnly = true)
    public AdminWarehouseListResponse searchAdmin(String q, Pageable pageable) {
        String normalized = (q == null || q.isBlank()) ? null : q.trim();
        return AdminWarehouseListResponse.from(
                warehouseRepository.searchAdmin(normalized, pageable));
    }

    /**
     * 단건 조회.
     *
     * @param id 창고 UUID
     * @return 응답 DTO
     * @throws BusinessException(NOT_FOUND) 창고 미발견
     */
    @Transactional(readOnly = true)
    public WarehouseResponse getOne(UUID id) {
        return WarehouseResponse.from(loadOrThrow(id));
    }

    /**
     * 새 창고를 생성한다. code 중복 검증 → 영속화. displayOrder 가 null 이면 0 으로 기본화.
     *
     * @param req CreateWarehouseRequest (code/name/type/address/displayOrder/description)
     * @return 생성된 창고 응답
     * @throws BusinessException(CONFLICT) 동일 code 의 활성 창고가 이미 존재할 때
     */
    public WarehouseResponse create(CreateWarehouseRequest req) {
        if (warehouseRepository.existsByCodeAndIsDeletedFalse(req.code())) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 사용 중인 창고 코드입니다: " + req.code());
        }
        int order = req.displayOrder() == null ? 0 : req.displayOrder();
        Warehouse saved = warehouseRepository.save(Warehouse.create(
                req.code(), req.name(), req.type(), req.address(), order, req.description()));
        return WarehouseResponse.from(saved);
    }

    /**
     * 부분 수정 — null 이 아닌 필드만 적용 (PATCH 시맨틱). code 변경은 미지원.
     *
     * @param id 창고 UUID
     * @param req UpdateWarehouseRequest (name/type/address/displayOrder/description, 모두 null 가능)
     * @return 갱신된 창고 응답
     * @throws BusinessException(NOT_FOUND) 창고 미발견
     */
    public WarehouseResponse update(UUID id, UpdateWarehouseRequest req) {
        Warehouse w = loadOrThrow(id);
        if (req.name() != null) {
            w.rename(req.name());
        }
        if (req.type() != null) {
            w.changeType(req.type());
        }
        if (req.address() != null) {
            w.changeAddress(req.address());
        }
        if (req.displayOrder() != null) {
            w.changeDisplayOrder(req.displayOrder());
        }
        if (req.description() != null) {
            w.editDescription(req.description());
        }
        return WarehouseResponse.from(w);
    }

    /**
     * Soft delete — 실제 row 는 보존하고 is_deleted=true 로 마킹 (BaseEntity.markDeleted 위임).
     *
     * @param id 창고 UUID
     * @param callerId 삭제자 user-id (null 이면 "system")
     * @throws BusinessException(NOT_FOUND) 창고 미발견
     */
    public void delete(UUID id, String callerId) {
        Warehouse w = loadOrThrow(id);
        w.markDeleted(callerId == null ? "system" : callerId);
    }

    Warehouse loadOrThrow(UUID id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "창고를 찾을 수 없습니다"));
    }
}
