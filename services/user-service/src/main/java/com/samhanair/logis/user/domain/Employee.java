package com.samhanair.logis.user.domain;

import com.samhanair.logis.common.entity.BaseEntity;
import com.samhanair.logis.common.security.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/**
 * Employee aggregate. The {@code id} is the canonical user UUID — assigned (not generated)
 * so it matches {@code auth-service.accounts.id} 1:1. Soft-deleted via {@link SQLRestriction}.
 */
@Entity
@Getter
@Table(name = "employees")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class Employee extends BaseEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "login_id", nullable = false, length = 50)
    private String loginId;

    @Column(name = "full_name", nullable = false, length = 50)
    private String fullName;

    @Column(name = "job_title", nullable = false, length = 30)
    private String position;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_snapshot", nullable = false, length = 20)
    private Role roleSnapshot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(name = "is_team_lead", nullable = false)
    private boolean teamLead;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Column(name = "termination_date")
    private LocalDate terminationDate;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    private Employee(UUID id, String loginId, String fullName, String position, Role roleSnapshot,
                     Department department, boolean teamLead, LocalDate hireDate, String email, String phone) {
        this.id = id;
        this.accountId = id;
        this.loginId = loginId;
        this.fullName = fullName;
        this.position = position;
        this.roleSnapshot = roleSnapshot;
        this.department = department;
        this.teamLead = teamLead;
        this.hireDate = hireDate;
        this.email = email;
        this.phone = phone;
    }

    public static Employee create(UUID id, String loginId, String fullName, String position, Role roleSnapshot,
                                  Department department, boolean teamLead, LocalDate hireDate,
                                  String email, String phone) {
        return new Employee(id, loginId, fullName, position, roleSnapshot,
                department, teamLead, hireDate, email, phone);
    }

    public void changeFullName(String fullName) {
        this.fullName = fullName;
    }

    public void changePosition(String position) {
        this.position = position;
    }

    public void changeDepartment(Department department) {
        this.department = department;
    }

    public void setTeamLead(boolean teamLead) {
        this.teamLead = teamLead;
    }

    public void changeEmail(String email) {
        this.email = email;
    }

    public void changePhone(String phone) {
        this.phone = phone;
    }

    public void updateRoleSnapshot(Role roleSnapshot) {
        this.roleSnapshot = roleSnapshot;
    }

    public void terminate(LocalDate date) {
        this.terminationDate = date;
    }
}
