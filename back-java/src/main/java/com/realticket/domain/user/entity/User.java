package com.realticket.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_id", nullable = false, unique = true)
    private String loginId;

    @Column(name = "login_password")
    private String loginPassword;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    private UserRole role;

    @Column(name = "is_guest", nullable = false)
    private boolean guest;

    private User(String loginId, String loginPassword, UserRole role, boolean guest) {
        this.loginId = loginId;
        this.loginPassword = loginPassword;
        this.role = role;
        this.guest = guest;
    }

    public static User member(String loginId, String encodedPassword, UserRole role) {
        return new User(loginId, encodedPassword, role, false);
    }

    public static User guest(String loginId) {
        return new User(loginId, null, UserRole.USER, true);
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
