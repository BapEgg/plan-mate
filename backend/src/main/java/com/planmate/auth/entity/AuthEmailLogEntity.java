package com.planmate.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "auth_email_logs")
public class AuthEmailLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AuthEmailTokenPurpose purpose;

    @Column(length = 45)
    private String requestedIp;

    protected AuthEmailLogEntity() {
    }

    private AuthEmailLogEntity(String email, AuthEmailTokenPurpose purpose, String requestedIp) {
        this.email = email;
        this.purpose = purpose;
        this.requestedIp = requestedIp;
    }

    public static AuthEmailLogEntity create(String email, AuthEmailTokenPurpose purpose, String requestedIp) {
        return new AuthEmailLogEntity(email, purpose, requestedIp);
    }

}
