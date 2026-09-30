package com.vtr.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

@Getter
public class CustomUserDetails extends User {
    private final Long id;
    private final String avatar;
    private final boolean isAdmin;

    public CustomUserDetails(Long id, String username, String password, String avatar,
                             boolean isAdmin, Collection<? extends GrantedAuthority> authorities) {
        super(username, password, authorities);
        this.id = id;
        this.avatar = avatar;
        this.isAdmin = isAdmin;
    }
}