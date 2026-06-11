package com.planmate.user.entity;

import com.planmate.user.domain.UserAccount;
import com.planmate.user.domain.UserRole;
import com.planmate.user.domain.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, unique = true, length = 255)
    private String emailCanonical;

    @Column(nullable = false, length = 30)
    private String nickname;

    @Column(length = 2048)
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private UserStatus status;

    private Instant emailVerifiedAt;

    protected UserEntity() {
    }

    private UserEntity(String email, String emailCanonical, String nickname, UserRole role, UserStatus status) {
        this.email = email;
        this.emailCanonical = emailCanonical;
        this.nickname = nickname;
        this.role = role;
        this.status = status;
    }

    public static UserEntity createPendingLocalUser(String email, String emailCanonical, String nickname) {
        return new UserEntity(email, emailCanonical, nickname, UserRole.USER, UserStatus.PENDING_EMAIL_VERIFICATION);
    }

    public static UserEntity createOauthUser(
            String email,
            String emailCanonical,
            String nickname,
            boolean emailVerified,
            Instant now
    ) {
        UserEntity user = new UserEntity(email, emailCanonical, nickname, UserRole.USER, UserStatus.ACTIVE);
        if (emailVerified) {
            user.emailVerifiedAt = now;
        }
        return user;
    }

    public void verifyEmail(Instant verifiedAt) {
        this.emailVerifiedAt = verifiedAt;
        if (status == UserStatus.PENDING_EMAIL_VERIFICATION) {
            this.status = UserStatus.ACTIVE;
        }
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public void updateProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public void clearProfileImageUrl() {
        this.profileImageUrl = null;
    }

    public UserAccount toAccount() {
        return new UserAccount(id, email, nickname, role, status, emailVerifiedAt != null);
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getEmailCanonical() {
        return emailCanonical;
    }

    public String getNickname() {
        return nickname;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public UserRole getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

}
