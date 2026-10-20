package com.prashant.KhetWallah.user;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private UserRole role = UserRole.USER;

    @Column(nullable = false)
    private boolean suspended = false;

    @Column(name = "auth_version", nullable = false)
    private long authVersion = 0;

    public long getAuthVersion() { return authVersion; }

    protected AppUser() {
    }

    public AppUser(
            String displayName,
            String email,
            String passwordHash
    ) {
        this.displayName = displayName;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public Long getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public boolean isSuspended() {
        return suspended;
    }

    public void suspend() {
        if (role == UserRole.ADMIN) {
            throw new IllegalStateException("An admin account cannot be suspended");
        }
        suspended = true;
    }

    public void reinstate() {
        suspended = false;
    }

    void makeAdmin() {
        role = UserRole.ADMIN;
    }

    void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        this.authVersion = Math.incrementExact(this.authVersion);
    }
}
