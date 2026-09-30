package com.vtr.controller;

import com.vtr.common.PageResult;
import com.vtr.common.Result;
import com.vtr.dto.*;
import com.vtr.entity.User;
import com.vtr.repository.UserRepository;
import com.vtr.service.UserService;
import com.vtr.vo.LoginVO;
import com.vtr.vo.UserStatisticsVO;
import com.vtr.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, Object> redisTemplate;

    // ========== 具体路径的方法（必须放在前面，避免被/{id}拦截） ==========

    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody @Validated LoginDTO dto,
                                 HttpServletRequest request) {
        try {
            return Result.success(userService.login(dto, request));
        } catch (Exception e) {
            log.error("登录失败: {}", e.getMessage());
            return Result.error(401, e.getMessage());
        }
    }

    @PostMapping("/register")
    public Result<Long> register(@RequestBody @Validated UserRegisterDTO dto) {
        try {
            return Result.success(userService.register(dto));
        } catch (Exception e) {
            log.error("注册失败: {}", e.getMessage());
            return Result.error(400, e.getMessage());
        }
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public Result<UserVO> getCurrentUser() {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) {
                return Result.error(401, "用户未登录");
            }
            UserVO userVO = userService.getCurrentUser(userId);
            if (userVO == null) {
                return Result.error(404, "用户不存在");
            }

            if (userVO.getRole() == null) {
                User user = userService.getEntityById(userId);
                if (user != null && user.getRole() != null) {
                    userVO.setRole(user.getRole().name());
                }
            }

            if (userVO.getAvatar() == null || userVO.getAvatar().isEmpty()) {
                userVO.setAvatar("/uploads/default-avatar.png");
            }

            log.info("返回用户信息: id={}, username={}, role={}, avatar={}",
                    userVO.getId(), userVO.getUsername(), userVO.getRole(), userVO.getAvatar());

            return Result.success(userVO);
        } catch (Exception e) {
            log.error("获取当前用户信息失败: {}", e.getMessage(), e);
            return Result.error(500, "获取用户信息失败: " + e.getMessage());
        }
    }

    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> updateProfile(@RequestBody @Validated UserProfileDTO profileDTO) {
        try {
            Long userId = getCurrentUserId();
            userService.updateProfile(userId, profileDTO);
            return Result.success();
        } catch (Exception e) {
            log.error("更新用户资料失败: {}", e.getMessage());
            return Result.error(500, e.getMessage());
        }
    }

    @PutMapping("/me/password")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> changePassword(@RequestBody @Validated PasswordChangeDTO dto) {
        try {
            Long userId = getCurrentUserId();
            userService.changePassword(userId, dto);
            return Result.success();
        } catch (Exception e) {
            log.error("修改密码失败: {}", e.getMessage());
            return Result.error(500, e.getMessage());
        }
    }

    @PostMapping("/me/avatar")
    @PreAuthorize("isAuthenticated()")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        try {
            Long userId = getCurrentUserId();
            String avatarUrl = userService.uploadAvatar(userId, file);
            return Result.success(avatarUrl);
        } catch (Exception e) {
            log.error("上传头像失败: {}", e.getMessage());
            return Result.error(500, e.getMessage());
        }
    }

    @PostMapping("/batch-review")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public Result<Void> batchReview(@RequestBody @Validated BatchReviewDTO dto) {
        try {
            Long adminId = getCurrentUserId();
            userService.batchReview(dto, adminId);
            return Result.success();
        } catch (Exception e) {
            log.error("批量审核失败: {}", e.getMessage());
            return Result.error(500, e.getMessage());
        }
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public Result<UserStatisticsVO> getStatistics() {
        try {
            return Result.success(userService.getStatistics());
        } catch (Exception e) {
            log.error("获取统计信息失败: {}", e.getMessage());
            return Result.error(500, e.getMessage());
        }
    }

    // ========== 调试接口 ==========

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<Map<String, Object>> debugPassword(@RequestBody LoginDTO dto) {
        Map<String, Object> result = new HashMap<>();
        try {
            log.info("=== 调试密码验证 ===");
            log.info("输入的用户名: {}", dto.getUsername());
            log.info("输入的密码: {}", dto.getPassword());

            User user = userRepository.findByUsername(dto.getUsername()).orElse(null);
            if (user == null) {
                result.put("error", "用户不存在");
                return Result.success(result);
            }

            String dbPassword = user.getPassword();
            log.info("数据库密码: {}", dbPassword);
            log.info("数据库密码长度: {}", dbPassword.length());

            boolean matches = passwordEncoder.matches(dto.getPassword(), dbPassword);

            String fixedHash = "$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG";
            boolean matchesFixed = passwordEncoder.matches("admin123", fixedHash);

            String reEncoded = passwordEncoder.encode(dto.getPassword());

            result.put("username", user.getUsername());
            result.put("dbPasswordPrefix", dbPassword.substring(0, Math.min(30, dbPassword.length())));
            result.put("inputPassword", dto.getPassword());
            result.put("passwordMatches", matches);
            result.put("fixedHashMatches", matchesFixed);
            result.put("reEncodedPassword", reEncoded);
            result.put("dbPasswordEqualsFixed", dbPassword.equals(fixedHash));

            log.info("密码匹配结果: {}", matches);
            log.info("固定密文匹配结果: {}", matchesFixed);
            log.info("数据库密码是否等于固定密文: {}", dbPassword.equals(fixedHash));

            return Result.success(result);
        } catch (Exception e) {
            log.error("调试失败", e);
            result.put("error", e.getMessage());
            return Result.success(result);
        }
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<Map<String, Object>> testPasswordDirect() {
        Map<String, Object> result = new HashMap<>();
        try {
            log.info("=== 直接密码测试 ===");

            // 直接从数据库查询 admin 用户
            User user = userRepository.findByUsername("admin").orElse(null);
            if (user == null) {
                result.put("error", "admin 用户不存在");
                return Result.success(result);
            }

            String dbPassword = user.getPassword();
            String testPassword = "admin123";

            // 测试1: 直接字符串比较
            String expectedHash = "$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG";
            boolean stringEquals = dbPassword.equals(expectedHash);

            // 测试2: 使用 PasswordEncoder 验证
            boolean matches = passwordEncoder.matches(testPassword, dbPassword);

            // 测试3: 重新编码看结果
            String reEncoded = passwordEncoder.encode(testPassword);
            boolean reEncodedMatches = passwordEncoder.matches(testPassword, reEncoded);

            // 测试4: 用固定密文测试 PasswordEncoder
            boolean fixedHashMatches = passwordEncoder.matches(testPassword, expectedHash);

            result.put("username", user.getUsername());
            result.put("dbPasswordPrefix", dbPassword.substring(0, Math.min(30, dbPassword.length())));
            result.put("dbPasswordLength", dbPassword.length());
            result.put("dbPasswordFull", dbPassword);
            result.put("expectedHashPrefix", expectedHash.substring(0, Math.min(30, expectedHash.length())));
            result.put("expectedHashFull", expectedHash);
            result.put("stringEquals", stringEquals);
            result.put("passwordEncoderMatches", matches);
            result.put("fixedHashMatches", fixedHashMatches);
            result.put("reEncodedPassword", reEncoded);
            result.put("reEncodedMatches", reEncodedMatches);

            log.info("=== 密码调试信息 ===");
            log.info("用户名: {}", user.getUsername());
            log.info("数据库密码: {}", dbPassword);
            log.info("期望密码: {}", expectedHash);
            log.info("字符串相等: {}", stringEquals);
            log.info("PasswordEncoder匹配: {}", matches);
            log.info("固定密文匹配: {}", fixedHashMatches);

            return Result.success(result);
        } catch (Exception e) {
            log.error("调试失败", e);
            result.put("error", e.getMessage());
            return Result.success(result);
        }
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<String> generateCorrectPassword() {
        try {
            String rawPassword = "admin123";
            String encodedPassword = passwordEncoder.encode(rawPassword);

            // 验证
            boolean matches = passwordEncoder.matches(rawPassword, encodedPassword);

            String result = String.format(
                    "原始密码: %s\n\n新生成的密文: %s\n\n验证结果: %s\n\n请执行SQL:\nUPDATE sys_user SET password = '%s' WHERE username = 'admin';\n\nUPDATE sys_user SET password = '%s' WHERE username IN ('student1', 'student2', 'teacher1', 'teacher2', 'suspended_user', 'xxxxx', 'wangzhongwei', 'hzh');",
                    rawPassword, encodedPassword, matches, encodedPassword, encodedPassword
            );

            log.info("生成正确密码: {}", result);
            return Result.success(result);
        } catch (Exception e) {
            log.error("生成密码失败", e);
            return Result.error(500, e.getMessage());
        }
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<String> resetPasswordSimple(@RequestParam String username) {
        try {
            User user = userRepository.findByUsername(username).orElse(null);
            if (user == null) {
                return Result.error(404, "用户不存在");
            }

            String newPassword = "admin123";
            String encodedPassword = passwordEncoder.encode(newPassword);

            user.setPassword(encodedPassword);
            userRepository.save(user);

            redisTemplate.delete("user:" + user.getId());

            boolean verify = passwordEncoder.matches(newPassword, user.getPassword());

            String result = String.format(
                    "密码已重置\n用户名: %s\n新密码: %s\n新密文: %s\n验证结果: %s\n数据库密码前缀: %s",
                    username, newPassword, encodedPassword, verify,
                    user.getPassword().substring(0, Math.min(30, user.getPassword().length()))
            );

            log.info(result);
            return Result.success(result);
        } catch (Exception e) {
            log.error("重置密码失败", e);
            return Result.error(500, e.getMessage());
        }
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<String> forceUpdatePassword(@RequestParam String username) {
        try {
            User user = userRepository.findByUsername(username).orElse(null);
            if (user == null) {
                return Result.error(404, "用户不存在");
            }

            String fixedHash = "$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG";
            user.setPassword(fixedHash);
            userRepository.save(user);

            redisTemplate.delete("user:" + user.getId());

            boolean verify = passwordEncoder.matches("admin123", user.getPassword());

            return Result.success("密码已强制更新为固定密文\n验证结果: " + verify);
        } catch (Exception e) {
            return Result.error(500, e.getMessage());
        }
    }

    // ========== 带路径变量的方法 ==========

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public Result<PageResult<UserVO>> listUsers(UserQueryDTO query) {
        try {
            return Result.success(userService.queryUsers(query));
        } catch (Exception e) {
            log.error("查询用户列表失败: {}", e.getMessage());
            return Result.error(500, e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public Result<UserVO> getUserDetail(@PathVariable Long id) {
        try {
            return Result.success(userService.getUserById(id));
        } catch (Exception e) {
            log.error("获取用户详情失败: {}", e.getMessage());
            return Result.error(500, e.getMessage());
        }
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public Result<Void> updateStatus(@PathVariable Long id,
                                     @RequestParam String status,
                                     @RequestParam(required = false) String reason) {
        try {
            userService.updateStatus(id, User.UserStatus.valueOf(status), reason);
            return Result.success();
        } catch (Exception e) {
            log.error("更新用户状态失败: {}", e.getMessage());
            return Result.error(500, e.getMessage());
        }
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> assignRole(@PathVariable Long id,
                                   @RequestParam String role) {
        try {
            Long operatorId = getCurrentUserId();
            userService.assignRole(id, User.UserRole.valueOf(role), operatorId);
            return Result.success();
        } catch (Exception e) {
            log.error("分配角色失败: {}", e.getMessage());
            return Result.error(500, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> updateUser(@PathVariable Long id,
                                   @RequestBody @Validated AdminUserUpdateDTO dto) {
        try {
            userService.updateUser(id, dto);
            return Result.success();
        } catch (Exception e) {
            log.error("管理员编辑用户账号失败: {}", e.getMessage());
            return Result.error(400, e.getMessage());
        }
    }

    @PutMapping("/{id}/identity-binding/review")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public Result<Void> reviewIdentityBinding(@PathVariable Long id,
                                               @RequestParam boolean approved,
                                               @RequestParam(required = false) String reason) {
        try {
            userService.reviewIdentityBinding(id, approved, reason);
            return Result.success();
        } catch (Exception e) {
            log.error("审核身份绑定失败: {}", e.getMessage());
            return Result.error(400, e.getMessage());
        }
    }

    @PostMapping("/send-code")
    public Result<Map<String, Object>> sendVerificationCode(
            @RequestBody @Validated SendVerificationCodeDTO dto,
            HttpServletRequest request) {
        try {
            userService.sendVerificationCode(dto, request);
            Map<String, Object> data = new HashMap<>();
            data.put("cooldownSeconds", 60);
            data.put("expireSeconds", 300);
            return Result.success("验证码已发送", data);
        } catch (Exception e) {
            log.error("发送验证码失败: {}", e.getMessage(), e);
            return Result.error(400, e.getMessage());
        }
    }

    @PostMapping("/code-login")
    public Result<LoginVO> codeLogin(@RequestBody @Validated CodeLoginDTO dto,
                                     HttpServletRequest request) {
        try {
            return Result.success(userService.codeLogin(dto, request));
        } catch (Exception e) {
            log.error("验证码登录失败: {}", e.getMessage());
            return Result.error(401, e.getMessage());
        }
    }

    @PostMapping("/forgot-password/reset")
    public Result<Void> resetPasswordByCode(@RequestBody @Validated ResetPasswordByCodeDTO dto) {
        try {
            userService.resetPasswordByCode(dto);
            return Result.success();
        } catch (Exception e) {
            log.error("验证码找回密码失败: {}", e.getMessage());
            return Result.error(400, e.getMessage());
        }
    }

    @PostMapping("/me/identity-binding")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> bindIdentity(@RequestBody @Validated IdentityBindingDTO dto) {
        try {
            userService.bindIdentity(getCurrentUserId(), dto);
            return Result.success();
        } catch (Exception e) {
            log.error("提交身份绑定失败: {}", e.getMessage());
            return Result.error(400, e.getMessage());
        }
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<String> resetPassword(@PathVariable Long id) {
        try {
            String temporaryPassword = userService.resetPassword(id, getCurrentUserId());
            return Result.success(temporaryPassword);
        } catch (Exception e) {
            log.error("重置用户密码失败: {}", e.getMessage());
            return Result.error(500, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public Result<Void> deleteUser(@PathVariable Long id) {
        try {
            Long operatorId = getCurrentUserId();
            userService.deleteUser(id, operatorId);
            return Result.success();
        } catch (Exception e) {
            log.error("删除用户失败: {}", e.getMessage());
            return Result.error(500, e.getMessage());
        }
    }

    // ========== 私有方法：获取当前登录用户ID ==========
    private Long getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated() ||
                    "anonymousUser".equals(authentication.getPrincipal())) {
                log.warn("未找到已认证的用户信息");
                return null;
            }
            Object principal = authentication.getPrincipal();

            if (principal instanceof Long) {
                return (Long) principal;
            }
            if (principal instanceof Integer) {
                return ((Integer) principal).longValue();
            }
            if (principal instanceof String) {
                try {
                    return Long.parseLong((String) principal);
                } catch (NumberFormatException ignored) {
                }
            }
            if (principal instanceof UserDetails) {
                String username = ((UserDetails) principal).getUsername();
                User user = userService.getEntityByUsername(username);
                if (user != null) {
                    return user.getId();
                }
                log.info("从 UserDetails 获取用户名: {}, 但未找到用户", username);
                return 1L;
            }
            log.warn("无法从 SecurityContext 中获取用户ID，principal 类型: {}", principal.getClass());
            return 1L;
        } catch (Exception e) {
            log.error("获取当前用户ID异常: {}", e.getMessage());
            return 1L;
        }
    }
}
