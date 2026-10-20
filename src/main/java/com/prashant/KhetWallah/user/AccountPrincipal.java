package com.prashant.KhetWallah.user;

import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;

/** Carries only a credential generation, never the recovery code. */
public final class AccountPrincipal extends User {
    private static final long serialVersionUID = 1L;
    private final long authVersion;

    public AccountPrincipal(AppUser user) {
        super(user.getEmail(), user.getPasswordHash(), !user.isSuspended(), true, true, true,
                AuthorityUtils.createAuthorityList("ROLE_" + user.getRole().name()));
        authVersion = user.getAuthVersion();
    }

    public long getAuthVersion() { return authVersion; }
}
