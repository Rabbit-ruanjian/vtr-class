package com.vtr.config;

import com.vtr.security.JwtAuthenticationFilter;
import com.vtr.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class JwtConfig {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtTokenProvider);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "http://[::1]:*"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // ========== 公开接口 ==========
                        .antMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Used by the local launcher and deployment probes to
                        // distinguish "backend still starting" from "AI failed".
                        .antMatchers("/actuator/health").permitAll()
                        .antMatchers("/api/users/login", "/api/users/register", "/api/users/send-code", "/api/users/forgot-password/reset").permitAll()
                        .antMatchers(HttpMethod.GET, "/api/public/overview").permitAll()
                        // 登录、注册页面需要先加载可用学校，未登录用户也应能读取学校基础信息。
                        .antMatchers(HttpMethod.GET, "/api/schools/active").permitAll()
                        .antMatchers("/api/upload/register-avatar").permitAll()
                        .antMatchers("/api/carousel/list").permitAll()
                        .antMatchers(HttpMethod.GET, "/api/notices/**").permitAll()
                        .antMatchers(HttpMethod.GET, "/api/snippets/**").permitAll()
                        .antMatchers(HttpMethod.GET, "/api/forum/**").permitAll()
                        .antMatchers("/uploads/**").permitAll()

                        // ========== 班级管理接口（教师、管理员、超级管理员） ==========
                        // These enrollment endpoints are intentionally available to students.
                        .antMatchers(HttpMethod.POST, "/api/classrooms/join").hasRole("STUDENT")
                        .antMatchers(HttpMethod.GET, "/api/classrooms/joined").hasRole("STUDENT")
                        .antMatchers(HttpMethod.GET, "/api/classrooms/**").hasAnyRole("TEACHER", "ADMIN", "SUPER_ADMIN")
                        .antMatchers(HttpMethod.POST, "/api/classrooms/**").hasAnyRole("TEACHER", "ADMIN", "SUPER_ADMIN")
                        .antMatchers(HttpMethod.PUT, "/api/classrooms/**").hasAnyRole("TEACHER", "ADMIN", "SUPER_ADMIN")
                        .antMatchers(HttpMethod.DELETE, "/api/classrooms/**").hasAnyRole("TEACHER", "ADMIN", "SUPER_ADMIN")

                        // ========== 用户个人接口（所有认证用户） ==========
                        .requestMatchers(
                                new AntPathRequestMatcher("/api/users/me"),
                                new AntPathRequestMatcher("/api/users/me/**"),
                                new AntPathRequestMatcher("/api/users/change-password"),
                                new AntPathRequestMatcher("/api/users/update-profile"),
                                new AntPathRequestMatcher("/api/users/upload-avatar")
                        ).authenticated()

                        // ========== 论坛交互 ==========
                        .antMatchers(HttpMethod.POST, "/api/forum/comment").authenticated()
                        .antMatchers(HttpMethod.POST, "/api/forum/post").authenticated()
                        .antMatchers(HttpMethod.DELETE, "/api/forum/post/**").authenticated()

                        // ========== 代码片段交互 ==========
                        .antMatchers(HttpMethod.POST, "/api/snippets").authenticated()
                        .antMatchers(HttpMethod.PUT, "/api/snippets/**").authenticated()
                        .antMatchers(HttpMethod.DELETE, "/api/snippets/**").authenticated()
                        .antMatchers(HttpMethod.POST, "/api/snippets/**/like").authenticated()
                        .antMatchers(HttpMethod.DELETE, "/api/snippets/**/like").authenticated()

                        // ========== 作业交互 ==========
                        .antMatchers(HttpMethod.POST, "/api/assignments").authenticated()
                        .antMatchers(HttpMethod.PUT, "/api/assignments/**").authenticated()
                        .antMatchers(HttpMethod.DELETE, "/api/assignments/**").authenticated()
                        .antMatchers(HttpMethod.POST, "/api/submissions/**").authenticated()
                        .antMatchers(HttpMethod.POST, "/api/upload/**").authenticated()

                        // ========== 公告管理（管理员） ==========
                        .antMatchers(HttpMethod.POST, "/api/notices").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .antMatchers(HttpMethod.PUT, "/api/notices/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .antMatchers(HttpMethod.DELETE, "/api/notices/**").hasAnyRole("ADMIN", "SUPER_ADMIN")

                        // ========== 管理员专用接口 ==========
                        .antMatchers("/api/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .antMatchers("/api/users/statistics").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .antMatchers("/api/users/batch-review").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .antMatchers("/api/users/*/status").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .antMatchers("/api/users/*/role").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .antMatchers(HttpMethod.POST, "/api/users/*/reset-password").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .antMatchers("/api/forum/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .antMatchers("/api/carousel/**").hasAnyRole("ADMIN", "SUPER_ADMIN")

                        // 用户管理 - 需要管理员权限
                        .requestMatchers(
                                new RegexRequestMatcher("/api/users/\\d+", null),
                                new AntPathRequestMatcher("/api/users")
                        ).hasAnyRole("ADMIN", "SUPER_ADMIN")

                        // 其他所有请求都需要认证
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
