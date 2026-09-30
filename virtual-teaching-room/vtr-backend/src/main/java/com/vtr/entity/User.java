package com.vtr.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vtr.common.BaseEntity;
import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sys_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)   // 忽略 JSON 中未定义的字段
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(unique = true, length = 100)
    private String email;

    @Column(length = 50)
    private String nickname;  // 添加昵称字段

    @Column(length = 20)
    private String phone;  // 添加手机号字段

    @Column(name = "unit_name", length = 200)
    private String unitName;

    // 学校租户ID；unitName 仅保留用于兼容历史资料。
    @Column(name = "school_id")
    private Long schoolId;

    // ADMIN 账号是否已经完成学校范围配置；SUPER_ADMIN 不受学校范围限制。
    @Column(name = "admin_scope_configured")
    private Boolean adminScopeConfigured = false;

    @Column(name = "admin_scope_all")
    private Boolean adminScopeAll = false;

    // 仅用于管理未绑定学校的普通账号；学校管理员不能看到或操作这类账号。
    @Column(name = "admin_scope_unbound")
    private Boolean adminScopeUnbound = false;

    @Column(name = "identity_type", length = 20)
    @Enumerated(EnumType.STRING)
    private IdentityType identityType;

    @Column(name = "identity_number", length = 50)
    private String identityNumber;

    @Column(name = "identity_status", length = 20)
    @Enumerated(EnumType.STRING)
    private IdentityStatus identityStatus;

    @Column(name = "phone_verified_at")
    private LocalDateTime phoneVerifiedAt;

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    @Column(length = 500)
    private String avatar;  // 添加头像URL字段

    @Column(length = 500)
    private String bio;  // 添加个人简介字段

    @Column(name = "academic_class_id")
    private Long academicClassId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private UserStatus status;

    @Column(name = "last_login_ip", length = 50)
    private String lastLoginIp;

    @Column(name = "last_login_time")
    private LocalDateTime lastLoginTime;

    @Override
    public boolean isEnabled() {
        return this.status == UserStatus.ACTIVE;
    }

    public enum UserRole {
        STUDENT("学生"),
        TEACHER("教师"),
        ADMIN("管理员"),
        // Kept for database compatibility; the application treats it exactly like ADMIN.
        SUPER_ADMIN("管理员");

        private final String description;

        UserRole(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum UserStatus {
        PENDING("待审核"),
        ACTIVE("活跃"),
        SUSPENDED("已暂停"),
        BANNED("已封禁"),
        DELETED("已删除");

        private final String description;

        UserStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum IdentityType {
        STUDENT("学号"),
        TEACHER("工号");

        private final String description;

        IdentityType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum IdentityStatus {
        UNBOUND("未绑定"),
        PENDING("待审核"),
        VERIFIED("已认证"),
        REJECTED("审核未通过");

        private final String description;

        IdentityStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public boolean isAdmin() {
        return this.role == UserRole.ADMIN || this.role == UserRole.SUPER_ADMIN;
    }

    public boolean isTeacher() {
        return this.role == UserRole.TEACHER;
    }

    public boolean isStudent() {
        return this.role == UserRole.STUDENT;
    }

    public void updateLastLogin(String ip) {
        this.lastLoginIp = ip;
        this.lastLoginTime = LocalDateTime.now();
    }
}
