package com.queuewise.security;

import com.queuewise.user.domain.User;
import java.util.Collection;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class QueueWiseUserPrincipal implements UserDetails {

    private final UUID userId;
    private final UUID organizationId;
    private final String username;
    private final String passwordHash;
    private final boolean enabled;
    private final Collection<? extends GrantedAuthority> authorities;

    protected QueueWiseUserPrincipal(UUID userId, UUID organizationId, String username,
                                     String passwordHash, boolean enabled,
                                     Collection<? extends GrantedAuthority> authorities) {
        this.userId = userId;
        this.organizationId = organizationId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.authorities = authorities;
    }

    public static QueueWiseUserPrincipal from(User user) {
        Collection<GrantedAuthority> authorities = user.getRoles().stream()
            .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role.getCode()))
                .toList();
        return new QueueWiseUserPrincipal(
                user.getId(),
                user.getOrganization().getId(),
                user.getEmail() != null ? user.getEmail() : user.getPhone(),
                user.getPasswordHash(),
                user.getStatus() == com.queuewise.user.domain.UserStatus.ACTIVE,
                authorities);
    }

    public UUID getUserId() { return userId; }
    public UUID getOrganizationId() { return organizationId; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return enabled; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
}
