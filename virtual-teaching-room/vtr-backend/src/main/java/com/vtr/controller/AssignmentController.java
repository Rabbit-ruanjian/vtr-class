package com.vtr.controller;

import com.vtr.common.PageResult;
import com.vtr.common.Result;
import com.vtr.dto.AssignmentCreateDTO;
import com.vtr.dto.AssignmentQueryDTO;
import com.vtr.dto.AssignmentUpdateDTO;
import com.vtr.service.AssignmentService;
import com.vtr.vo.AssignmentVO;
import com.vtr.vo.TestCaseVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/assignments")
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;

    // ========== 创建作业（仅教师） ==========
    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<Long> create(@RequestBody @Validated AssignmentCreateDTO dto) {
        Long userId = getCurrentUserId();
        log.info("创建作业: userId={}, title={}, publishType={}", userId, dto.getTitle(), dto.getPublishType());
        return Result.success(assignmentService.create(dto, userId));
    }

    // ========== 获取作业详情 ==========
    @GetMapping("/{id}")
    public Result<AssignmentVO> getById(@PathVariable Long id) {
        Long userId = getCurrentUserIdSilently();
        log.debug("获取作业详情: id={}, userId={}", id, userId);
        return Result.success(assignmentService.getById(id, userId));
    }

    // ========== 获取作业列表（教师端管理用） ==========
    @GetMapping
    public Result<PageResult<AssignmentVO>> list(AssignmentQueryDTO query) {
        Long userId = getCurrentUserIdSilently();
        log.debug("获取作业列表: page={}, size={}, userId={}", query.getPage(), query.getSize(), userId);
        return Result.success(assignmentService.query(query, userId));
    }

    // ========== 获取学生可见的作业列表（学生端专用） ==========
    @GetMapping("/student/visible")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<PageResult<AssignmentVO>> getStudentVisibleAssignments(AssignmentQueryDTO queryDTO) {
        Long studentId = getCurrentUserId();
        log.info("获取学生可见作业列表: studentId={}, page={}, size={}", studentId, queryDTO.getPage(), queryDTO.getSize());
        return Result.success(assignmentService.getStudentVisibleAssignments(studentId, queryDTO));
    }

    // ========== 获取教师的作业列表（教师端管理用） ==========
    @GetMapping("/teacher/manage")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<PageResult<AssignmentVO>> getTeacherAssignments(AssignmentQueryDTO queryDTO) {
        Long teacherId = getCurrentUserId();
        log.info("获取教师作业列表: teacherId={}, page={}, size={}", teacherId, queryDTO.getPage(), queryDTO.getSize());
        return Result.success(assignmentService.getTeacherAssignments(teacherId, queryDTO));
    }

    // ========== 获取我的作业（学生）- 保留兼容 ==========
    @GetMapping("/my")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<List<AssignmentVO>> getMyAssignments() {
        Long studentId = getCurrentUserId();
        log.info("获取我的作业: studentId={}", studentId);
        return Result.success(assignmentService.getMyAssignments(studentId));
    }

    // ========== 获取我教的作业（教师）- 保留兼容 ==========
    @GetMapping("/teaching")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<List<AssignmentVO>> getTeachingAssignments() {
        Long userId = getCurrentUserId();
        log.info("获取我教的作业: userId={}", userId);
        return Result.success(assignmentService.getTeachingAssignments(userId));
    }

    // ========== 更新作业 ==========
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<Void> update(@PathVariable Long id,
                               @RequestBody @Validated AssignmentUpdateDTO dto) {
        Long userId = getCurrentUserId();
        boolean isAdmin = hasRole("ADMIN") || hasRole("SUPER_ADMIN");
        log.info("更新作业: id={}, userId={}, isAdmin={}", id, userId, isAdmin);
        assignmentService.update(id, dto, userId, isAdmin);
        return Result.success();
    }

    // ========== 删除作业 ==========
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        boolean isAdmin = hasRole("ADMIN") || hasRole("SUPER_ADMIN");
        log.info("删除作业: id={}, userId={}, isAdmin={}", id, userId, isAdmin);
        assignmentService.delete(id, userId, isAdmin);
        return Result.success();
    }

    // ========== 发布作业（教师直接发布） ==========
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<Void> publish(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        boolean isAdmin = hasRole("ADMIN") || hasRole("SUPER_ADMIN");
        log.info("发布作业: id={}, userId={}, isAdmin={}", id, userId, isAdmin);
        assignmentService.publish(id, userId, isAdmin);
        return Result.success();
    }

    // ========== 发布作业给指定学生（教师发布后设置可见范围） ==========
    @PostMapping("/{id}/publish-to-students")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<Void> publishToStudents(@PathVariable Long id, @RequestBody List<Long> studentIds) {
        Long teacherId = getCurrentUserId();
        boolean isAdmin = hasRole("ADMIN") || hasRole("SUPER_ADMIN");
        log.info("发布作业给指定学生: assignmentId={}, teacherId={}, studentCount={}", id, teacherId, studentIds.size());
        assignmentService.publishToStudents(id, studentIds, teacherId, isAdmin);
        return Result.success();
    }

    // ========== 检查学生是否有权限查看作业 ==========
    @GetMapping("/{id}/can-view")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<Boolean> canStudentView(@PathVariable Long id) {
        Long studentId = getCurrentUserId();
        return Result.success(assignmentService.canStudentViewAssignment(studentId, id));
    }

    // ========== 关闭作业 ==========
    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<Void> close(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        boolean isAdmin = hasRole("ADMIN") || hasRole("SUPER_ADMIN");
        log.info("关闭作业: id={}, userId={}, isAdmin={}", id, userId, isAdmin);
        assignmentService.close(id, userId, isAdmin);
        return Result.success();
    }

    // ========== 重新开放作业 ==========
    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<Void> reopen(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        boolean isAdmin = hasRole("ADMIN") || hasRole("SUPER_ADMIN");
        log.info("重新开放作业: id={}, userId={}, isAdmin={}", id, userId, isAdmin);
        assignmentService.reopen(id, userId, isAdmin);
        return Result.success();
    }

    // ========== 获取公开测试用例 ==========
    @GetMapping("/{id}/testcases")
    public Result<List<TestCaseVO>> getPublicTestCases(@PathVariable Long id) {
        log.debug("获取公开测试用例: id={}", id);
        return Result.success(assignmentService.getPublicTestCases(id));
    }

    // ========== 获取所有测试用例（教师专用） ==========
    @GetMapping("/{id}/testcases/all")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<List<TestCaseVO>> getAllTestCases(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        boolean isAdmin = hasRole("ADMIN") || hasRole("SUPER_ADMIN");
        log.info("获取所有测试用例: id={}, userId={}, isAdmin={}", id, userId, isAdmin);
        return Result.success(assignmentService.getAllTestCases(id, userId, isAdmin));
    }

    // ========== 私有方法 ==========

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() ||
                "anonymousUser".equals(authentication.getPrincipal())) {
            throw new RuntimeException("未找到已认证的用户信息");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof com.vtr.security.CustomUserDetails) {
            return ((com.vtr.security.CustomUserDetails) principal).getId();
        }
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
        throw new RuntimeException("无法从 SecurityContext 中获取用户ID，principal 类型: " + principal.getClass());
    }

    private Long getCurrentUserIdSilently() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated() ||
                    "anonymousUser".equals(authentication.getPrincipal())) {
                return null;
            }
            Object principal = authentication.getPrincipal();
            if (principal instanceof com.vtr.security.CustomUserDetails) {
                return ((com.vtr.security.CustomUserDetails) principal).getId();
            }
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
            return null;
        } catch (Exception e) {
            log.debug("获取当前用户ID失败: {}", e.getMessage());
            return null;
        }
    }

    private boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return false;
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }
}
