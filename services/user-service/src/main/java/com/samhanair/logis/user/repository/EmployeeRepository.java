package com.samhanair.logis.user.repository;

import com.samhanair.logis.common.security.Role;
import com.samhanair.logis.user.domain.Employee;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;

/**
 * Soft-delete is enforced at the entity level via @SQLRestriction on {@link Employee}.
 *
 * <p>W10-6 known-issues fix — 모든 Employee 반환 finder 에 {@code @EntityGraph(attributePaths = "department")}
 * 적용. Controller 직렬화 시점 ({@code EmployeeResponse.from()} → {@code e.getDepartment().getId()}) 은
 * Hibernate Session 종료 후이므로, LAZY proxy 그대로 두면 {@code LazyInitializationException}
 * (could not initialize proxy ... no Session) 발생. EntityGraph 로 fetch join 강제하여 직렬화
 * 시점 lazy 접근 자체를 제거 + N+1 회피.
 */
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    boolean existsByLoginId(String loginId);

    @Override
    @NonNull
    @EntityGraph(attributePaths = "department")
    List<Employee> findAll();

    @Override
    @NonNull
    @EntityGraph(attributePaths = "department")
    Optional<Employee> findById(@NonNull UUID id);

    @EntityGraph(attributePaths = "department")
    List<Employee> findAllByIdIn(Collection<UUID> ids);

    @EntityGraph(attributePaths = "department")
    List<Employee> findAllByDepartment_Id(UUID departmentId);

    @EntityGraph(attributePaths = "department")
    List<Employee> findAllByRoleSnapshot(Role role);

    @EntityGraph(attributePaths = "department")
    List<Employee> findAllByDepartment_IdAndRoleSnapshot(UUID departmentId, Role role);
}
