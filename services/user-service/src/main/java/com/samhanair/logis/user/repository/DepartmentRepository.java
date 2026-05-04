package com.samhanair.logis.user.repository;

import com.samhanair.logis.user.domain.Department;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Departments are typically tiny — full-table fetch sorted by displayOrder is fine. */
public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    List<Department> findAllByOrderByDisplayOrderAsc();

    Optional<Department> findByCode(String code);
}
