package com.vtr.config;

import com.vtr.entity.AcademicClass;
import com.vtr.entity.AcademicClassStudentRoster;
import com.vtr.entity.AdminSchoolAccess;
import com.vtr.entity.ContentAudit;
import com.vtr.entity.School;
import com.vtr.entity.User;
import com.vtr.entity.SchoolTeacherRoster;
import com.vtr.repository.AcademicClassRepository;
import com.vtr.repository.AcademicClassStudentRosterRepository;
import com.vtr.repository.AdminSchoolAccessRepository;
import com.vtr.repository.ContentAuditRepository;
import com.vtr.repository.CourseRepository;
import com.vtr.repository.CoursewareRepository;
import com.vtr.repository.SchoolRepository;
import com.vtr.repository.TeachingActivityRepository;
import com.vtr.repository.NoticeRepository;
import com.vtr.repository.SchoolTeacherRosterRepository;
import com.vtr.repository.ForumCommentRepository;
import com.vtr.repository.ForumPostRepository;
import com.vtr.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SchoolDataInitializer implements CommandLineRunner {
    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;
    private final AcademicClassRepository academicClassRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AcademicClassStudentRosterRepository studentRosterRepository;
    private final AdminSchoolAccessRepository adminSchoolAccessRepository;
    private final ContentAuditRepository contentAuditRepository;
    private final CourseRepository courseRepository;
    private final CoursewareRepository coursewareRepository;
    private final TeachingActivityRepository teachingActivityRepository;
    private final NoticeRepository noticeRepository;
    private final SchoolTeacherRosterRepository teacherRosterRepository;
    private final PasswordEncoder passwordEncoder;
    private final ForumPostRepository forumPostRepository;
    private final ForumCommentRepository forumCommentRepository;

    @Override
    @Transactional
    public void run(String... args) {
        dropLegacyClassCodeIndex();
        School defaultSchool = schoolRepository.findByCodeIgnoreCase("DEFAULT").orElseGet(() -> {
            School school = new School();
            school.setName("默认学校");
            school.setCode("DEFAULT");
            school.setStatus("ACTIVE");
            return schoolRepository.save(school);
        });

        for (AcademicClass item : academicClassRepository.findAll()) {
            if (item.getSchoolId() == null) {
                item.setSchoolId(defaultSchool.getId());
                academicClassRepository.save(item);
            }
        }
        for (User user : userRepository.findAll()) {
            // 未选择学校的普通账号必须保持未绑定，不能再被历史默认学校逻辑误归属。
            if (user.getRole() != User.UserRole.SUPER_ADMIN
                    && user.getSchoolId() == null
                    && user.getIdentityStatus() != User.IdentityStatus.UNBOUND) {
                user.setSchoolId(defaultSchool.getId());
                if (user.getUnitName() == null || user.getUnitName().isBlank()) user.setUnitName(defaultSchool.getName());
                userRepository.save(user);
            } else if (user.getRole() != User.UserRole.SUPER_ADMIN
                    && user.getSchoolId() != null
                    && user.getIdentityNumber() == null
                    && user.getIdentityStatus() == User.IdentityStatus.UNBOUND
                    && (user.isStudent() || user.isTeacher())) {
                user.setSchoolId(null);
                user.setUnitName(null);
                userRepository.save(user);
            }
            if (user.isStudent() && user.getAcademicClassId() != null && user.getSchoolId() != null
                    && user.getIdentityNumber() != null && !user.getIdentityNumber().isBlank()
                    && studentRosterRepository.findBySchoolIdAndStudentNumber(user.getSchoolId(), user.getIdentityNumber()).isEmpty()) {
                AcademicClassStudentRoster roster = new AcademicClassStudentRoster();
                roster.setSchoolId(user.getSchoolId());
                roster.setAcademicClassId(user.getAcademicClassId());
                roster.setStudentNumber(user.getIdentityNumber());
                roster.setName(user.getNickname());
                roster.setUserId(user.getId());
                roster.setStatus("ACTIVE");
                studentRosterRepository.save(roster);
            }
            if (user.isTeacher() && user.getSchoolId() != null && user.getIdentityNumber() != null
                    && !user.getIdentityNumber().isBlank()
                    && teacherRosterRepository.findBySchoolIdAndEmployeeNumber(
                    user.getSchoolId(), user.getIdentityNumber()).isEmpty()) {
                teacherRosterRepository.save(SchoolTeacherRoster.builder()
                        .schoolId(user.getSchoolId())
                        .employeeNumber(user.getIdentityNumber())
                        .name(user.getNickname())
                        .department(user.getUnitName())
                        .userId(user.getId())
                        .status("ACTIVE")
                        .build());
            }
        }

        // 内容归属由创建者的学校继承，历史数据只在首次升级时回填。
        for (com.vtr.entity.Course course : courseRepository.findAll()) {
            if (course.getSchoolId() == null && course.getCreatedBy() != null) {
                userRepository.findById(course.getCreatedBy()).map(User::getSchoolId).ifPresent(schoolId -> {
                    course.setSchoolId(schoolId);
                    courseRepository.save(course);
                });
            }
        }
        for (com.vtr.entity.Courseware resource : coursewareRepository.findAll()) {
            if (resource.getSchoolId() == null && resource.getTeacherId() != null) {
                userRepository.findById(resource.getTeacherId()).map(User::getSchoolId).ifPresent(schoolId -> {
                    resource.setSchoolId(schoolId);
                    coursewareRepository.save(resource);
                });
            }
        }
        for (com.vtr.entity.TeachingActivity activity : teachingActivityRepository.findAll()) {
            if (activity.getSchoolId() == null && activity.getOrganizerId() != null) {
                userRepository.findById(activity.getOrganizerId()).map(User::getSchoolId).ifPresent(schoolId -> {
                    activity.setSchoolId(schoolId);
                    teachingActivityRepository.save(activity);
                });
            }
        }
        for (com.vtr.entity.Notice notice : noticeRepository.findAll()) {
            if (notice.getSchoolId() == null && notice.getAuthorId() != null) {
                userRepository.findById(notice.getAuthorId()).map(User::getSchoolId).ifPresent(schoolId -> {
                    notice.setSchoolId(schoolId);
                    noticeRepository.save(notice);
                });
            }
        }

        // 给旧审核记录补上学校归属，避免升级后审核中心把不同学校的内容混在一起。
        for (ContentAudit audit : contentAuditRepository.findAll()) {
            if (audit.getSchoolId() == null && audit.getAuthorId() != null) {
                userRepository.findById(audit.getAuthorId()).map(User::getSchoolId).ifPresent(schoolId -> {
                    audit.setSchoolId(schoolId);
                    contentAuditRepository.save(audit);
                });
            }
        }

        for (com.vtr.entity.ForumPost post : forumPostRepository.findAll()) {
            if (post.getSchoolId() == null && post.getAuthorId() != null) {
                userRepository.findById(post.getAuthorId()).map(User::getSchoolId).ifPresent(schoolId -> {
                    post.setSchoolId(schoolId);
                    forumPostRepository.save(post);
                });
            }
        }
        for (com.vtr.entity.ForumComment comment : forumCommentRepository.findAll()) {
            if (comment.getSchoolId() == null && comment.getAuthorId() != null) {
                userRepository.findById(comment.getAuthorId()).map(User::getSchoolId).ifPresent(schoolId -> {
                    comment.setSchoolId(schoolId);
                    forumCommentRepository.save(comment);
                });
            }
        }

        // 旧版本管理员没有管理范围字段。首次升级时保留其原有可用性，
        // 将已有管理员收敛到一所学校；新提升的管理员不会自动获得学校权限。
        List<User> admins = userRepository.findByRoleInAndIsDeletedFalse(
                List.of(User.UserRole.ADMIN, User.UserRole.SUPER_ADMIN));
        for (User admin : admins) {
            // 新增字段为 NULL 的才是旧版本账号；新提升的管理员会明确写入 FALSE，不能自动放大权限。
            if (admin.getAdminScopeConfigured() != null) continue;
            if (admin.getRole() == User.UserRole.SUPER_ADMIN) {
                admin.setAdminScopeConfigured(true);
                admin.setAdminScopeAll(true);
                admin.setAdminScopeUnbound(false);
                admin.setSchoolId(null);
                adminSchoolAccessRepository.deleteByAdminUserId(admin.getId());
                userRepository.save(admin);
                continue;
            }
            Long schoolId = admin.getSchoolId();
            if (schoolId == null) {
                schoolId = adminSchoolAccessRepository.findByAdminUserIdOrderBySchoolIdAsc(admin.getId()).stream()
                        .map(AdminSchoolAccess::getSchoolId).findFirst().orElse(null);
            }
            if (schoolId == null) {
                schoolId = schoolRepository.findByStatusOrderByNameAsc("ACTIVE").stream()
                        .map(School::getId).findFirst().orElse(null);
            }
            adminSchoolAccessRepository.deleteByAdminUserId(admin.getId());
            if (schoolId != null) {
                adminSchoolAccessRepository.save(AdminSchoolAccess.builder()
                        .adminUserId(admin.getId()).schoolId(schoolId).status("ACTIVE").build());
            }
            admin.setSchoolId(schoolId);
            admin.setAdminScopeConfigured(true);
            admin.setAdminScopeAll(false);
            admin.setAdminScopeUnbound(false);
            userRepository.save(admin);
        }
        ensureUnboundUserManager();
    }

    private void dropLegacyClassCodeIndex() {
        try {
            jdbcTemplate.execute("ALTER TABLE academic_class DROP INDEX uk_academic_class_code");
        } catch (Exception ignored) {
            // 新安装或已迁移的数据库没有旧索引时无需处理。
        }
    }

    private void ensureUnboundUserManager() {
        User manager = userRepository.findByUsername("public-admin@vtr.com").orElse(null);
        if (manager == null) {
            manager = new User();
            manager.setUsername("public-admin@vtr.com");
            manager.setEmail("public-admin@vtr.com");
            manager.setPassword(passwordEncoder.encode("Padmin#2026Vtr"));
            manager.setNickname("公共账号管理员");
            manager.setAvatar("/uploads/default-avatar.png");
            manager.setStatus(User.UserStatus.ACTIVE);
            manager.setIdentityStatus(User.IdentityStatus.UNBOUND);
        }
        manager.setRole(User.UserRole.ADMIN);
        manager.setSchoolId(null);
        manager.setUnitName(null);
        manager.setIdentityType(null);
        manager.setIdentityNumber(null);
        manager.setAcademicClassId(null);
        manager.setAdminScopeConfigured(true);
        manager.setAdminScopeAll(false);
        manager.setAdminScopeUnbound(true);
        if (manager.getStatus() == null || manager.getStatus() == User.UserStatus.DELETED) {
            manager.setStatus(User.UserStatus.ACTIVE);
        }
        userRepository.save(manager);
        adminSchoolAccessRepository.deleteByAdminUserId(manager.getId());
    }
}
