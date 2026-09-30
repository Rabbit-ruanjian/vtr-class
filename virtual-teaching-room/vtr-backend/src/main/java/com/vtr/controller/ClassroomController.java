package com.vtr.controller;

import com.vtr.common.PageResult;
import com.vtr.common.Result;
import com.vtr.dto.AddStudentToClassroomDTO;
import com.vtr.dto.AssignTeacherDTO;
import com.vtr.dto.ClassroomCreateDTO;
import com.vtr.dto.ClassroomUpdateDTO;
import com.vtr.dto.JoinClassroomDTO;
import com.vtr.service.ClassroomService;
import com.vtr.vo.ClassroomVO;
import com.vtr.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/classrooms")
@RequiredArgsConstructor
public class ClassroomController {

    private final ClassroomService classroomService;

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication.getPrincipal();
        if (principal instanceof com.vtr.security.CustomUserDetails) {
            return ((com.vtr.security.CustomUserDetails) principal).getId();
        }
        throw new RuntimeException("无法获取用户ID");
    }

    /**
     * 创建班级
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<Long> createClassroom(@RequestBody @Valid ClassroomCreateDTO dto) {
        Long teacherId = getCurrentUserId();
        Long classroomId = classroomService.createClassroom(dto, teacherId);
        return Result.success(classroomId);
    }

    /**
     * 更新班级
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<Void> updateClassroom(@PathVariable Long id, @RequestBody ClassroomUpdateDTO dto) {
        Long teacherId = getCurrentUserId();
        classroomService.updateClassroom(id, dto, teacherId);
        return Result.success();
    }

    /**
     * 删除班级（教师软删除）
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<Void> deleteClassroom(@PathVariable Long id) {
        Long teacherId = getCurrentUserId();
        classroomService.deleteClassroom(id, teacherId);
        return Result.success();
    }

    /**
     * 归档班级
     */
    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<Void> archiveClassroom(@PathVariable Long id) {
        Long teacherId = getCurrentUserId();
        classroomService.archiveClassroom(id, teacherId);
        return Result.success();
    }

    /**
     * 恢复归档的班级
     */
    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<Void> restoreClassroom(@PathVariable Long id) {
        Long teacherId = getCurrentUserId();
        classroomService.restoreClassroom(id, teacherId);
        return Result.success();
    }

    /**
     * 获取班级详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<ClassroomVO> getClassroom(@PathVariable Long id) {
        Long teacherId = getCurrentUserId();
        return Result.success(classroomService.getClassroomById(id, teacherId));
    }

    /**
     * 获取我的班级列表（包括活跃和归档）
     */
    @GetMapping("/my-classrooms")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<PageResult<ClassroomVO>> getMyClassrooms(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        Long teacherId = getCurrentUserId();
        return Result.success(classroomService.getMyClassrooms(teacherId, page, size, keyword, status));
    }

    @PostMapping("/join")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<Void> joinClassroom(@RequestBody @Valid JoinClassroomDTO dto) {
        classroomService.joinClassroom(dto, getCurrentUserId());
        return Result.success();
    }

    @GetMapping("/joined")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<List<ClassroomVO>> getJoinedClassrooms() {
        return Result.success(classroomService.getJoinedClassrooms(getCurrentUserId()));
    }

    /** 学生主动退出教学班 */
    @DeleteMapping("/{classroomId}/leave")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<Void> leaveClassroom(@PathVariable Long classroomId) {
        classroomService.leaveClassroom(classroomId, getCurrentUserId());
        return Result.success();
    }

    /**
     * 添加学生到班级
     */
    @PostMapping("/students")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<Void> addStudentsToClassroom(@RequestBody @Valid AddStudentToClassroomDTO dto) {
        Long teacherId = getCurrentUserId();
        classroomService.addStudentsToClassroom(dto, teacherId);
        return Result.success();
    }

    /**
     * 从班级移除学生
     */
    @DeleteMapping("/{classroomId}/students/{studentId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<Void> removeStudentFromClassroom(@PathVariable Long classroomId, @PathVariable Long studentId) {
        Long teacherId = getCurrentUserId();
        classroomService.removeStudentFromClassroom(classroomId, studentId, teacherId);
        return Result.success();
    }

    /**
     * 获取班级学生列表
     */
    @GetMapping("/{classroomId}/students")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<PageResult<UserVO>> getClassroomStudents(
            @PathVariable Long classroomId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String keyword) {
        Long teacherId = getCurrentUserId();
        return Result.success(classroomService.getClassroomStudents(classroomId, teacherId, page, size, keyword));
    }

    /**
     * 获取学生所在班级列表
     */
    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<List<ClassroomVO>> getStudentClassrooms(@PathVariable Long studentId) {
        Long teacherId = getCurrentUserId();
        return Result.success(classroomService.getStudentClassrooms(studentId, teacherId));
    }

    // ==================== 管理员接口 ====================

    /**
     * 管理员获取所有班级列表（支持状态筛选）
     */
    @GetMapping("/admin/list")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<PageResult<ClassroomVO>> getAllClassroomsForAdmin(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        log.info("管理员获取所有班级列表: page={}, size={}, keyword={}, status={}", page, size, keyword, status);
        return Result.success(classroomService.getAllClassroomsForAdmin(page, size, keyword, status));
    }

    /**
     * 管理员安排班级教师
     */
    @PutMapping("/admin/{id}/teacher")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> assignTeacherToClassroom(@PathVariable Long id, @RequestBody @Valid AssignTeacherDTO dto) {
        log.info("管理员安排班级教师: classroomId={}, teacherId={}", id, dto.getTeacherId());
        classroomService.assignTeacherToClassroom(id, dto.getTeacherId());
        return Result.success();
    }

    /**
     * 管理员删除班级（物理删除）
     */
    @DeleteMapping("/admin/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> adminDeleteClassroom(@PathVariable Long id) {
        log.info("管理员删除班级: id={}", id);
        classroomService.adminDeleteClassroom(id);
        return Result.success();
    }
}
