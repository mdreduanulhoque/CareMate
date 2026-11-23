package com.carebridge.security;

import com.carebridge.user.Role;
import com.carebridge.user.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class CareBridgeUserPrincipal implements UserDetails {

    private final Long userId;
    private final String mobileNumber;
    private final String fullName;
    private final Role role;
    private final boolean active;
    private final List<GrantedAuthority> authorities;

    public CareBridgeUserPrincipal(User user) {
        this.userId = user.getId();
        this.mobileNumber = user.getMobileNumber();
        this.fullName = user.getFullName();
        this.role = user.getRole();
        this.active = user.isActive();
        this.authorities = List.of(new SimpleGrantedAuthority(user.getRole().authority()));
    }

    public Long getUserId() {
        return userId;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public String getFullName() {
        return fullName;
    }

    public Role getRole() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return String.valueOf(userId);
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
