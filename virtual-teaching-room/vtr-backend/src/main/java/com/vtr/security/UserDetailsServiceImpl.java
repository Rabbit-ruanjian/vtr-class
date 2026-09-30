package com.vtr.security;

import com.vtr.entity.User;           // 根据你的实际 User 实体类路径修改
import com.vtr.repository.UserRepository;  // 根据你的实际 Repository 路径修改
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 从数据库查询用户
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));

        // 转换为 Spring Security 的 UserDetails 对象
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())  // 密码必须是已加密的
                .authorities(getAuthorities(user))
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(!user.isEnabled())
                .build();
    }

    /**
     * 获取用户权限（角色/权限列表）
     */
    private List<SimpleGrantedAuthority> getAuthorities(User user) {
        // 方式1：简单角色（如 ROLE_USER, ROLE_ADMIN）
        String role = String.valueOf(user.getRole());  // 假设 User 实体有 role 字段
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role));

        // 方式2：多权限（如果用户有多个权限）
        // return user.getPermissions().stream()
        //         .map(permission -> new SimpleGrantedAuthority(permission.getName()))
        //         .collect(Collectors.toList());
    }
}