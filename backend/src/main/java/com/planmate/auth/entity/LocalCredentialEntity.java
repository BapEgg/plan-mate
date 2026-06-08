package com.planmate.auth.entity;

import com.planmate.user.entity.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "local_credentials")
public class LocalCredentialEntity {

    @Id
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @Column(nullable = false, unique = true, length = 50)
    private String loginId;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false)
    private Instant passwordUpdatedAt;

    @Column(nullable = false)
    private int failedLoginCount;

    private Instant lockedUntil;

    protected LocalCredentialEntity() {
    }

    private LocalCredentialEntity(UserEntity user, String loginId, String passwordHash, Instant passwordUpdatedAt) {
        this.user = user;
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.passwordUpdatedAt = passwordUpdatedAt;
    }

    public static LocalCredentialEntity create(UserEntity user, String loginId, String passwordHash, Instant now) {
        return new LocalCredentialEntity(user, loginId, passwordHash, now);
    }

    public void changePassword(String passwordHash, Instant changedAt) {
        this.passwordHash = passwordHash;
        this.passwordUpdatedAt = changedAt;
        this.failedLoginCount = 0;
        this.lockedUntil = null;
    }

    public UserEntity getUser() {
        return user;
    }

    public Long getUserId() {
        return userId;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

}
