package com.samhanair.logis.user.repository;

import com.samhanair.logis.common.security.Role;
import com.samhanair.logis.user.domain.Employee;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Soft-delete is enforced at the entity level via @SQLRestriction on {@link Employee}. */
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    boolean existsByLoginId(String loginId);

    List<Employee> findAllByIdIn(Collection<UUID> ids);

    List<Employee> findAllByDepartment_Id(UUID departmentId);

    List<Employee> findAllByRoleSnapshot(Role role);

    List<Employee> findAllByDepartment_IdAndRoleSnapshot(UUID departmentId, Role role);
}
