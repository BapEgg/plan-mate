package com.planmate.auth.entity;

import com.planmate.user.entity.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "oauth_accounts")
public class OauthAccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(nullable = false, length = 255)
    private String providerUserId;

    @Column(length = 255)
    private String providerEmail;

    @Column(nullable = false)
    private boolean providerEmailVerified;

    protected OauthAccountEntity() {
    }

    private OauthAccountEntity(
            UserEntity user,
            String provider,
            String providerUserId,
            String providerEmail,
            boolean providerEmailVerified
    ) {
        this.user = user;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.providerEmail = providerEmail;
        this.providerEmailVerified = providerEmailVerified;
    }

    public static OauthAccountEntity create(
            UserEntity user,
            String provider,
            String providerUserId,
            String providerEmail,
            boolean providerEmailVerified
    ) {
        return new OauthAccountEntity(user, provider, providerUserId, providerEmail, providerEmailVerified);
    }

    public UserEntity getUser() {
        return user;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderUserId() {
        return providerUserId;
    }

    public String getProviderEmail() {
        return providerEmail;
    }

    public boolean isProviderEmailVerified() {
        return providerEmailVerified;
    }

}
