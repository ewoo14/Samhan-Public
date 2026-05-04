package com.samhanair.logis.auth.domain;

import com.samhanair.logis.common.entity.BaseEntity;
import com.samhanair.logis.common.security.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

/** Local user/account aggregate. Soft-deleted (filtered via @SQLRestriction). */
@Entity
@Getter
@Table(name = "accounts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class Account extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "login_id", nullable = false, unique = true, length = 50)
    private String loginId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    private Account(String loginId, String passwordHash, String displayName, Role role) {
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
        this.enabled = true;
    }

    public static Account create(String loginId, String passwordHash, String displayName, Role role) {
        return new Account(loginId, passwordHash, displayName, role);
    }

    public void markLogin(LocalDateTime now) {
        this.lastLoginAt = now;
    }
}
