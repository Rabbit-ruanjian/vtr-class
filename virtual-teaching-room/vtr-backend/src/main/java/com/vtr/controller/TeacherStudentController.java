package com.vtr.controller;

import com.vtr.common.PageResult;
import com.vtr.common.Result;
import com.vtr.dto.AddStudentDTO;
import com.vtr.dto.BatchImportStudentDTO;
import com.vtr.dto.ChangeClassroomDTO;
import com.vtr.service.TeacherStudentRelationService;
import com.vtr.vo.ClassroomVO;
import com.vtr.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
public class TeacherStudentController {

    private final TeacherStudentRelationService teacherStudentRelationService;

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
        throw new RuntimeException("无法从 SecurityContext 中获取用户ID");
    }

    /**
     * 获取我的学生列表（教师端）- 支持按班级过滤
     */
    @GetMapping("/my-students")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<PageResult<UserVO>> getMyStudents(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long classroomId) {
        Long teacherId = getCurrentUserId();
        log.info("获取我的学生列表: teacherId={}, page={}, size={}, keyword={}, classroomId={}",
                teacherId, page, size, keyword, classroomId);
        return Result.success(teacherStudentRelationService.getMyStudents(teacherId, page, size, keyword, classroomId));
    }

    /**
     * 获取我的老师列表（学生端）
     */
    @GetMapping("/my-teachers")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<List<UserVO>> getMyTeachers() {
        Long studentId = getCurrentUserId();
        log.info("获取我的老师列表: studentId={}", studentId);
        return Result.success(teacherStudentRelationService.getMyTeachers(studentId));
    }

    /**
     * 获取可添加的学生列表 - 支持排除指定班级的学生
     */
    @GetMapping("/available")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<PageResult<UserVO>> getAvailableStudents(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long excludeClassroomId) {
        Long teacherId = getCurrentUserId();
        log.info("获取可添加学生列表: teacherId={}, page={}, size={}, keyword={}, excludeClassroomId={}",
                teacherId, page, size, keyword, excludeClassroomId);
        return Result.success(teacherStudentRelationService.getAvailableStudents(teacherId, page, size, keyword, excludeClassroomId));
    }

    /**
     * 根据学号搜索学生
     */
    @GetMapping("/search/{studentNumber}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<UserVO> searchStudentByStudentId(@PathVariable String studentNumber) {
        log.info("搜索学生: studentNumber={}", studentNumber);
        return Result.success(teacherStudentRelationService.searchStudentByStudentId(getCurrentUserId(), studentNumber));
    }

    /**
     * 添加学生到我的班级（可指定班级）
     */
    @PostMapping("/add-to-teacher")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<Void> addStudentToTeacher(@RequestBody @Validated AddStudentDTO dto) {
        Long teacherId = getCurrentUserId();
        log.info("添加学生到班级: teacherId={}, studentNumber={}, classroomId={}",
                teacherId, dto.getStudentNumber(), dto.getClassroomId());
        teacherStudentRelationService.addStudentToTeacher(teacherId, dto);
        return Result.success();
    }

    /**
     * 从我的班级移除学生
     */
    @DeleteMapping("/{studentId}/remove-from-teacher")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<Void> removeStudentFromTeacher(@PathVariable Long studentId) {
        Long teacherId = getCurrentUserId();
        log.info("从班级移除学生: teacherId={}, studentId={}", teacherId, studentId);
        teacherStudentRelationService.removeStudentFromTeacher(teacherId, studentId);
        return Result.success();
    }

    /**
     * 批量导入学生到指定班级
     */
    @PostMapping("/batch-import")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<Void> batchImportStudents(@RequestBody BatchImportStudentDTO dto) {
        Long teacherId = getCurrentUserId();
        log.info("批量导入学生: teacherId={}, count={}, classroomId={}",
                teacherId, dto.getStudentNumbers().size(), dto.getClassroomId());
        teacherStudentRelationService.batchImportStudents(teacherId, dto);
        return Result.success();
    }

    /**
     * 获取教师的所有学生ID列表
     */
    @GetMapping("/my-student-ids")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<List<Long>> getMyStudentIds() {
        Long teacherId = getCurrentUserId();
        log.info("获取我的学生ID列表: teacherId={}", teacherId);
        return Result.success(teacherStudentRelationService.getMyStudentIds(teacherId));
    }

    /**
     * 获取指定班级的学生ID列表
     */
    @GetMapping("/classroom/{classroomId}/student-ids")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<List<Long>> getClassroomStudentIds(@PathVariable Long classroomId) {
        Long teacherId = getCurrentUserId();
        log.info("获取班级学生ID列表: teacherId={}, classroomId={}", teacherId, classroomId);
        return Result.success(teacherStudentRelationService.getClassroomStudentIds(teacherId, classroomId));
    }

    /**
     * 学生调班
     */
    @PostMapping("/change-classroom")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<Void> changeStudentClassroom(@RequestBody @Validated ChangeClassroomDTO dto) {
        Long teacherId = getCurrentUserId();
        log.info("学生调班: teacherId={}, studentId={}, newClassroomId={}", teacherId, dto.getStudentId(), dto.getNewClassroomId());
        teacherStudentRelationService.changeStudentClassroom(teacherId, dto);
        return Result.success();
    }

    /**
     * 获取学生的班级信息
     */
    @GetMapping("/{studentId}/classroom")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<ClassroomVO> getStudentClassroom(@PathVariable Long studentId) {
        Long teacherId = getCurrentUserId();
        log.info("获取学生班级信息: teacherId={}, studentId={}", teacherId, studentId);
        return Result.success(teacherStudentRelationService.getStudentClassroom(teacherId, studentId));
    }
}
