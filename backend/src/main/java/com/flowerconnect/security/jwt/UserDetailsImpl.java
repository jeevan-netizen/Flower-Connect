package com.flowerconnect.security.jwt;

import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.User.Status;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class UserDetailsImpl implements UserDetails {

    private final Long id;
    private final String email;
    private final String password;
    private final String fullName;
    private final String roleName;
    private final boolean active;

    public UserDetailsImpl(Long id, String email, String password, String fullName, String roleName, boolean active) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.fullName = fullName;
        this.roleName = roleName;
        this.active = active;
    }

    public static UserDetailsImpl fromUser(User user) {
        Role role = user.getRole();
        String roleName = role != null ? role.getName() : "CUSTOMER";
        return new UserDetailsImpl(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getFullName(),
                roleName,
                user.getStatus() == Status.ACTIVE);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + roleName));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return active;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return active;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
