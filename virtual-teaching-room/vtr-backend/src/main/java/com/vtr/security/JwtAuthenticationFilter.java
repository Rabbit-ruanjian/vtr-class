package com.vtr.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    // 公开路径（不需要 token）
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/users/login",
            "/api/users/register",
            "/api/users/send-code",
            "/api/users/forgot-password/reset",
            "/api/upload/register-avatar",
            "/api/carousel/list"
    );

    // GET请求公开路径（不需要 token）
    private static final List<String> PUBLIC_GET_PATHS = List.of(
            "/api/forum/posts",
            "/api/forum/post/",
            "/api/snippets",
            "/api/snippets/",
            "/api/snippets/tags",
            "/api/snippets/languages/",
            "/api/snippets/rankings/",
            "/api/notices",
            "/api/notices/",
            "/api/notices/active",
            "/api/notices/top",
            "/api/notices/\\d+"
    );

    // ✅ 用户个人接口（所有认证用户都可访问，不需要额外权限检查）
    private static final List<String> USER_SELF_PATHS = List.of(
            "/api/users/me",
            "/api/users/me/password",
            "/api/users/me/avatar",
            "/api/users/me/identity-binding",
            "/api/users/change-password",
            "/api/users/update-profile",
            "/api/users/upload-avatar"
    );

    // 管理员专用路径（需要ADMIN角色）
    private static final List<String> ADMIN_PATHS = List.of(
            "/api/admin/",
            "/api/users",
            "/api/users/statistics",
            "/api/users/batch-review",
            "/api/forum/admin/"
    );

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        log.debug("JWT过滤: {} {}", method, path);

        // 1. OPTIONS预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(method)) {
            chain.doFilter(request, response);
            return;
        }

        // 2. 教研活动路径 - 需要认证，但不需要额外权限检查（让Controller自己处理权限）
        if (path.startsWith("/api/teaching-activities")) {
            log.debug("教研活动路径，继续验证Token: {}", path);
            // 继续执行下面的token验证逻辑，不直接放行
        }
        // 3. 公开路径直接放行
        else if (isPublicPath(path)) {
            log.debug("公开路径放行: {}", path);
            chain.doFilter(request, response);
            return;
        }
        // 4. GET请求的公开路径放行
        else if ("GET".equalsIgnoreCase(method) && isPublicGetPath(path) && extractToken(request) == null) {
            log.debug("公开GET请求放行: {}", path);
            chain.doFilter(request, response);
            return;
        }

        // 5. 其他路径需要验证 token
        String token = extractToken(request);
        log.debug("提取Token: {}", token != null ? "存在" : "不存在");

        if (token != null && jwtTokenProvider.validateToken(token)) {
            try {
                Long userId = jwtTokenProvider.getUserIdFromToken(token);
                String username = jwtTokenProvider.getUsernameFromToken(token);
                List<String> roles = jwtTokenProvider.getRolesFromToken(token);
                String avatar = jwtTokenProvider.getAvatarFromToken(token);

                log.info("从Token解析: userId={}, username={}, roles={}", userId, username, roles);

                boolean isAdmin = roles != null && (roles.contains("ADMIN") || roles.contains("SUPER_ADMIN"));

                List<String> safeRoles = roles != null ? roles : List.of();
                List<GrantedAuthority> authorities = safeRoles.stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .collect(Collectors.toList());

                // 6. 用户个人接口：只验证 token 有效性，不检查管理员权限
                if (isUserSelfPath(path)) {
                    log.debug("用户个人接口，跳过管理员权限检查: {}", path);

                    CustomUserDetails userDetails = new CustomUserDetails(
                            userId, username, "", avatar, isAdmin, authorities
                    );

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.info("用户个人接口认证成功: userId={}, username={}", userId, username);

                    chain.doFilter(request, response);
                    return;
                }

                // 7. 检查管理员权限（对于需要管理员权限的路径）
                if (isAdminPath(path) && !isAdmin) {
                    log.warn("用户 {} 尝试访问管理员路径 {} 但没有ADMIN角色", username, path);
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":403,\"message\":\"没有权限执行此操作\"}");
                    return;
                }

                // 8. 对于教研活动路径，不需要检查管理员权限，直接通过
                CustomUserDetails userDetails = new CustomUserDetails(
                        userId, username, "", avatar, isAdmin, authorities
                );

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.info("认证成功: userId={}, username={}, roles={}, isAdmin={}", userId, username, safeRoles, isAdmin);

                chain.doFilter(request, response);
                return;
            } catch (Exception e) {
                log.error("设置认证信息失败: {}", e.getMessage(), e);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"message\":\"认证失败\"}");
                return;
            }
        } else if (token != null) {
            log.warn("无效Token: {}", token);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"无效Token\"}");
            return;
        }

        log.debug("无Token，请求继续（后续会被拦截）");
        chain.doFilter(request, response);
    }

    private boolean isPublicPath(String path) {
        for (String publicPath : PUBLIC_PATHS) {
            if (path.startsWith(publicPath)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPublicGetPath(String path) {
        for (String publicPath : PUBLIC_GET_PATHS) {
            if (publicPath.endsWith("\\d+") && path.matches(publicPath)) {
                return true;
            }
            if (path.startsWith(publicPath)) {
                return true;
            }
        }
        return false;
    }

    // ✅ 判断是否为用户个人接口
    private boolean isUserSelfPath(String path) {
        for (String userPath : USER_SELF_PATHS) {
            if (path.equals(userPath) || path.startsWith(userPath)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAdminPath(String path) {
        for (String adminPath : ADMIN_PATHS) {
            if (path.startsWith(adminPath)) {
                return true;
            }
        }
        return false;
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
