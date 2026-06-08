package com.planmate.auth.entity;

import com.planmate.user.entity.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "auth_email_tokens")
public class AuthEmailTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, unique = true, length = 255)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AuthEmailTokenPurpose purpose;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant usedAt;

    protected AuthEmailTokenEntity() {
    }

    private AuthEmailTokenEntity(UserEntity user, String email, String tokenHash,
                                 AuthEmailTokenPurpose purpose, Instant expiresAt) {
        this.user = user;
        this.email = email;
        this.tokenHash = tokenHash;
        this.purpose = purpose;
        this.expiresAt = expiresAt;
    }

    public static AuthEmailTokenEntity create(UserEntity user, String email, String tokenHash,
                                              AuthEmailTokenPurpose purpose, Instant expiresAt) {
        return new AuthEmailTokenEntity(user, email, tokenHash, purpose, expiresAt);
    }

    public boolean isUsable(AuthEmailTokenPurpose expectedPurpose, Instant now) {
        return purpose == expectedPurpose && usedAt == null && expiresAt.isAfter(now);
    }

    public void markUsed(Instant usedAt) {
        this.usedAt = usedAt;
    }

    public Long getId() {
        return id;
    }

    public UserEntity getUser() {
        return user;
    }

    public String getEmail() {
        return email;
    }

    public AuthEmailTokenPurpose getPurpose() {
        return purpose;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

}
