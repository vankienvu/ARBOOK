package com.arbook.backend.security;

import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.arbook.backend.user.entity.User;
import com.arbook.backend.user.entity.UserStatus;

public record AppUserDetails(
        Long id,
        String fullName,
        String email,
        String password,
        UserStatus status,
        Collection<? extends GrantedAuthority> authorities
) implements UserDetails {

    public static AppUserDetails from(User user, Collection<? extends GrantedAuthority> authorities) {
        return new AppUserDetails(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getStatus(),
                authorities
        );
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return status != UserStatus.DELETED;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != UserStatus.LOCKED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }
}
