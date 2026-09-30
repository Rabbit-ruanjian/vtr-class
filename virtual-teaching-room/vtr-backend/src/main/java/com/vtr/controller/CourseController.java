package com.vtr.controller;

import com.vtr.common.Result;
import com.vtr.common.exception.BusinessException;
import com.vtr.common.exception.NotFoundException;
import com.vtr.dto.CourseCreateDTO;
import com.vtr.dto.CourseUpdateDTO;
import com.vtr.dto.CourseMemberUpsertDTO;
import com.vtr.entity.Course;
import com.vtr.entity.Classroom;
import com.vtr.entity.CourseMember;
import com.vtr.entity.CourseSection;
import com.vtr.entity.User;
import com.vtr.repository.ClassroomRepository;
import com.vtr.repository.CourseRepository;
import com.vtr.repository.CourseMemberRepository;
import com.vtr.repository.UserRepository;
import com.vtr.repository.CoursewareRepository;
import com.vtr.repository.TeachingActivityRepository;
import com.vtr.repository.AssignmentRepository;
import com.vtr.repository.ClassroomStudentRelationRepository;
import com.vtr.repository.CourseChapterRepository;
import com.vtr.repository.CourseSectionRepository;
import com.vtr.repository.CourseAllowedAcademicClassRepository;
import com.vtr.repository.AcademicClassRepository;
import com.vtr.repository.SchoolDepartmentRepository;
import com.vtr.repository.SchoolRepository;
import com.vtr.entity.School;
import com.vtr.entity.AcademicClass;
import com.vtr.entity.CourseAllowedAcademicClass;
import com.vtr.vo.CourseMemberVO;
import com.vtr.vo.StudentCourseVO;
import com.vtr.security.CustomUserDetails;
import com.vtr.service.CourseCodeGenerator;
import com.vtr.service.NotificationService;
import com.vtr.vo.CourseVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {
    private final CourseRepository courseRepository;
    private final ClassroomRepository classroomRepository;
    private final CourseCodeGenerator courseCodeGenerator;
    private final CourseMemberRepository courseMemberRepository;
    private final UserRepository userRepository;
    private final CoursewareRepository coursewareRepository;
    private final TeachingActivityRepository activityRepository;
    private final AssignmentRepository assignmentRepository;
    private final ClassroomStudentRelationRepository classroomStudentRelationRepository;
    private final CourseChapterRepository courseChapterRepository;
    private final CourseSectionRepository courseSectionRepository;
    private final CourseAllowedAcademicClassRepository allowedAcademicClasses;
    private final AcademicClassRepository academicClassRepository;
    private final SchoolDepartmentRepository schoolDepartmentRepository;
    private final SchoolRepository schoolRepository;
    private final NotificationService notificationService;

    /** Courses available for the resource hub. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Result<List<CourseVO>> list() {
        CustomUserDetails user = currentUser();
        List<Course> courses = isPlatformAdmin(user)
                ? courseRepository.findByStatusOrderByCourseNameAsc("ACTIVE")
                : currentSchoolId(user) == null
                ? List.of()
                : courseRepository.findBySchoolIdAndStatusOrderByCourseNameAsc(currentSchoolId(user), "ACTIVE");
        return Result.success(toVOs(courses));
    }

    @GetMapping("/joined")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<List<StudentCourseVO>> joinedCourses() {
        Long studentId = currentUser().getId();
        Map<Long, StudentCourseVO> grouped = new LinkedHashMap<>();
        classroomStudentRelationRepository.findByStudentIdAndStatus(studentId, "ACTIVE").stream()
                .map(relation -> classroomRepository.findById(relation.getClassroomId()).orElse(null))
                .filter(classroom -> classroom != null
                        && "ACTIVE".equals(classroom.getStatus())
                        && classroom.getCourseId() != null)
                .forEach(classroom -> courseRepository.findById(classroom.getCourseId()).ifPresent(course -> {
                    if (!"ACTIVE".equals(course.getStatus())) return;
                    StudentCourseVO courseVO = grouped.computeIfAbsent(course.getId(), key -> {
                        StudentCourseVO item = new StudentCourseVO();
                        BeanUtils.copyProperties(course, item);
                        userRepository.findById(course.getCreatedBy()).ifPresent(teacher ->
                                item.setTeacherName(teacher.getNickname() != null && !teacher.getNickname().isBlank()
                                        ? teacher.getNickname() : teacher.getUsername()));
                        return item;
                    });
                    com.vtr.vo.ClassroomVO classroomVO = new com.vtr.vo.ClassroomVO();
                    BeanUtils.copyProperties(classroom, classroomVO);
                    courseVO.getClassrooms().add(classroomVO);
                }));
        return Result.success(new java.util.ArrayList<>(grouped.values()));
    }

    /** Teacher sees courses they own; administrators govern every course, including archived ones. */
    @GetMapping("/manage")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    public Result<List<CourseVO>> manageList(@RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) String status) {
        CustomUserDetails user = currentUser();
        List<Course> courses;
        if (user.isAdmin()) {
            courses = isPlatformAdmin(user)
                    ? courseRepository.findAllByOrderByCourseNameAsc()
                    : currentSchoolId(user) == null
                    ? List.of()
                    : courseRepository.findBySchoolIdOrderByCourseNameAsc(currentSchoolId(user));
        } else {
            courses = new java.util.ArrayList<>(courseRepository.findByCreatedByOrderByCourseNameAsc(user.getId()));
            List<Long> sharedCourseIds = courseMemberRepository.findByUserIdAndStatus(user.getId(), "ACTIVE")
                    .stream().map(CourseMember::getCourseId).collect(Collectors.toList());
            courseRepository.findAllById(sharedCourseIds).forEach(course -> {
                if (courses.stream().noneMatch(existing -> existing.getId().equals(course.getId()))) courses.add(course);
            });
            courses.sort(java.util.Comparator.comparing(Course::getCourseName));
            Long schoolId = currentSchoolId(user);
            courses.removeIf(course -> schoolId == null || !schoolId.equals(course.getSchoolId()));
        }

        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase();
        return Result.success(toVOs(courses.stream()
                .filter(course -> status == null || status.isBlank() || status.equals(course.getStatus()))
                .filter(course -> normalizedKeyword.isEmpty()
                        || course.getCourseName().toLowerCase().contains(normalizedKeyword)
                        || (course.getCourseCode() != null && course.getCourseCode().toLowerCase().contains(normalizedKeyword)))
                .collect(Collectors.toList())));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    @Transactional
    public Result<Long> create(@RequestBody @Valid CourseCreateDTO dto) {
        CustomUserDetails current = currentUser();
        Long userId = current.getId();
        Long schoolId = isPlatformAdmin(current) ? dto.getSchoolId() : currentSchoolId(current);
        if (schoolId == null) throw new BusinessException("当前账号未绑定学校，不能创建课程");
        if (isPlatformAdmin(current)) {
            School school = schoolRepository.findById(schoolId).orElseThrow(() -> new BusinessException("请选择有效学校"));
            if (!"ACTIVE".equals(school.getStatus())) throw new BusinessException("学校已停用，不能创建课程");
        }
        if (!isPlatformAdmin(current)) schoolId = currentSchoolId(current);
        if (dto.getSemester() == null || dto.getSemester().isBlank()) throw new BusinessException("请选择学期");
        if (dto.getCourseCategory() == null || dto.getCourseCategory().isBlank()) throw new BusinessException("请选择课程类别");
        if (dto.getTeachingDepartment() == null || dto.getTeachingDepartment().isBlank()) throw new BusinessException("请选择开课院系");
        validateTeachingDepartment(schoolId, dto.getTeachingDepartment());
        String courseCode = blankToNull(dto.getCourseCode());
        ensureCodeAvailable(courseCode, null);
        Course course = Course.builder()
                .courseName(dto.getCourseName().trim())
                .courseCode(courseCode)
                .description(blankToNull(dto.getDescription()))
                .coverImage(blankToNull(dto.getCoverImage()))
                .semester(blankToNull(dto.getSemester()))
                .credits(dto.getCredits())
                .courseCategory(blankToNull(dto.getCourseCategory()))
                .teachingDepartment(blankToNull(dto.getTeachingDepartment()))
                .assessmentMethod(blankToNull(dto.getAssessmentMethod()))
                .createdBy(userId)
                .schoolId(schoolId)
                .status("ACTIVE")
                .build();
        course = courseRepository.save(course);
        if (courseCode == null) {
            course.setCourseCode(courseCodeGenerator.generateUniqueCode());
            courseRepository.save(course);
        }
        courseMemberRepository.save(CourseMember.builder()
                .courseId(course.getId()).userId(userId).role("OWNER").status("ACTIVE").build());
        replaceAllowedAcademicClasses(course, dto.getAllowedAcademicClassIds(), current);
        return Result.success(course.getId());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    public Result<CourseVO> detail(@PathVariable Long id) {
        return Result.success(toVO(requireWorkspaceAccess(id)));
    }

    @GetMapping("/{id}/overview")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    public Result<Map<String, Object>> overview(@PathVariable Long id) {
        Course course = requireWorkspaceAccess(id);
        List<Classroom> classrooms = classroomRepository.findByCourseIdOrderByCreatedAtDesc(id);
        List<Long> activeClassroomIds = classrooms.stream()
                .filter(classroom -> "ACTIVE".equals(classroom.getStatus()))
                .map(Classroom::getId)
                .collect(Collectors.toList());
        long students = activeClassroomIds.isEmpty() ? 0
                : classroomStudentRelationRepository.countDistinctStudentsByClassroomIdsAndStatus(activeClassroomIds, "ACTIVE");
        Map<String, Object> data = new HashMap<>();
        data.put("course", toVO(course));
        data.put("teacherCount", courseMemberRepository.countByCourseIdAndStatus(id, "ACTIVE"));
        data.put("classroomCount", classroomRepository.countByCourseIdAndStatus(id, "ACTIVE"));
        data.put("studentCount", students);
        data.put("resourceCount", coursewareRepository.countByCourseIdAndStatus(id, "ACTIVE"));
        data.put("activityCount", activityRepository.countByCourseIdAndIsDeletedFalse(id));
        data.put("assignmentCount", assignmentRepository.countByCourseId(id));
        return Result.success(data);
    }

    @GetMapping("/{id}/members")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    public Result<List<CourseMemberVO>> members(@PathVariable Long id) {
        requireWorkspaceAccess(id);
        List<CourseMemberVO> result = courseMemberRepository.findByCourseIdAndStatusOrderByJoinedAtAsc(id, "ACTIVE")
                .stream().map(this::toMemberVO).collect(Collectors.toList());
        return Result.success(result);
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    @Transactional
    public Result<Void> addMember(@PathVariable Long id, @RequestBody @Valid CourseMemberUpsertDTO dto) {
        Course course = requireManageable(id);
        if (course.getCreatedBy().equals(dto.getUserId())) {
            throw new BusinessException("课程负责人不能作为普通协作成员变更角色");
        }
        User user = userRepository.findById(dto.getUserId()).orElseThrow(() -> new NotFoundException("用户", dto.getUserId()));
        if (user.getRole() != User.UserRole.TEACHER) throw new BusinessException("课程协作成员必须是教师");
        String role = normalizeMemberRole(dto.getRole());
        CourseMember existingMember = courseMemberRepository.findByCourseIdAndUserId(id, dto.getUserId()).orElse(null);
        CourseMember member = existingMember != null
                ? existingMember
                : CourseMember.builder().courseId(id).userId(dto.getUserId()).build();
        if ("OWNER".equals(member.getRole())) {
            throw new BusinessException("课程负责人不能被降级为普通协作成员");
        }
        boolean wasActive = existingMember != null && "ACTIVE".equals(existingMember.getStatus());
        boolean roleChanged = existingMember == null || !role.equals(existingMember.getRole());
        member.setRole(role); member.setStatus("ACTIVE");
        courseMemberRepository.save(member);
        if (!wasActive || roleChanged) {
            notificationService.sendNotification(
                    dto.getUserId(),
                    wasActive ? "COURSE_MEMBER_ROLE_UPDATED" : "COURSE_MEMBER_ADDED",
                    wasActive ? "课程协作角色已调整" : "已加入课程协作团队",
                    wasActive
                            ? "您在课程《" + course.getCourseName() + "》中的协作角色已调整为“" + role + "”。"
                            : "您已加入课程《" + course.getCourseName() + "》，当前协作角色为“" + role + "”。",
                    id);
        }
        return Result.success();
    }

    @PutMapping("/{id}/members/{userId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    @Transactional
    public Result<Void> updateMember(@PathVariable Long id, @PathVariable Long userId, @RequestBody @Valid CourseMemberUpsertDTO dto) {
        Course course = requireManageable(id);
        if (course.getCreatedBy().equals(userId)) {
            throw new BusinessException("课程负责人不能修改自身的负责人角色");
        }
        CourseMember member = courseMemberRepository.findByCourseIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("课程成员", userId));
        String role = normalizeMemberRole(dto.getRole());
        boolean roleChanged = !role.equals(member.getRole());
        member.setRole(role);
        courseMemberRepository.save(member);
        if (roleChanged) {
            notificationService.sendNotification(userId, "COURSE_MEMBER_ROLE_UPDATED", "课程协作角色已调整",
                    "您在课程《" + course.getCourseName() + "》中的协作角色已调整为“" + role + "”。", id);
        }
        return Result.success();
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    @Transactional
    public Result<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        requireManageable(id);
        Course course = courseRepository.findById(id).orElseThrow(() -> new NotFoundException("课程", id));
        if (course.getCreatedBy().equals(userId)) throw new BusinessException("不能移除课程负责人");
        courseMemberRepository.findByCourseIdAndUserId(id, userId).ifPresent(member -> {
            member.setStatus("REMOVED"); courseMemberRepository.save(member);
            notificationService.sendNotification(userId, "COURSE_MEMBER_REMOVED", "已移出课程协作团队",
                    "您已被移出课程《" + course.getCourseName() + "》的协作团队。", id);
        });
        return Result.success();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    @Transactional
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid CourseUpdateDTO dto) {
        Course course = requireManageable(id);
        if (dto.getCourseName() != null && !dto.getCourseName().isBlank()) course.setCourseName(dto.getCourseName().trim());
        if (dto.getCourseCode() != null) {
            String courseCode = blankToNull(dto.getCourseCode());
            if (courseCode == null) {
                course.setCourseCode(courseCodeGenerator.generateUniqueCode());
            } else {
                ensureCodeAvailable(courseCode, course.getId());
                course.setCourseCode(courseCode);
            }
        }
        if (dto.getDescription() != null) course.setDescription(blankToNull(dto.getDescription()));
        if (dto.getCoverImage() != null) course.setCoverImage(blankToNull(dto.getCoverImage()));
        if (dto.getSemester() != null) course.setSemester(blankToNull(dto.getSemester()));
        if (dto.getCredits() != null) course.setCredits(dto.getCredits());
        if (dto.getCourseCategory() != null) course.setCourseCategory(blankToNull(dto.getCourseCategory()));
        if (dto.getTeachingDepartment() != null) {
            if (!dto.getTeachingDepartment().isBlank()) validateTeachingDepartment(course.getSchoolId(), dto.getTeachingDepartment());
            course.setTeachingDepartment(blankToNull(dto.getTeachingDepartment()));
        }
        if (dto.getAssessmentMethod() != null) course.setAssessmentMethod(blankToNull(dto.getAssessmentMethod()));
        courseRepository.save(course);
        replaceAllowedAcademicClasses(course, dto.getAllowedAcademicClassIds(), currentUser());
        return Result.success();
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    public Result<Void> archive(@PathVariable Long id) {
        Course course = requireManageable(id);
        if (!"ACTIVE".equals(course.getStatus())) {
            throw new BusinessException("只有授课中的课程才能归档");
        }
        course.setStatus("ARCHIVED");
        courseRepository.save(course);
        return Result.success();
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    public Result<Void> restore(@PathVariable Long id) {
        Course course = requireManageable(id);
        if (!"ARCHIVED".equals(course.getStatus())) {
            throw new BusinessException("只有已归档的课程才能恢复");
        }
        course.setStatus("ACTIVE");
        courseRepository.save(course);
        return Result.success();
    }

    @GetMapping("/{id}/classrooms")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    public Result<List<Classroom>> classrooms(@PathVariable Long id) {
        requireWorkspaceAccess(id);
        return Result.success(classroomRepository.findByCourseIdOrderByCreatedAtDesc(id));
    }

    @GetMapping("/{id}/sections")
    @PreAuthorize("isAuthenticated()")
    public Result<List<CourseSection>> sections(@PathVariable Long id) {
        requireCourseContentAccess(id);
        return Result.success(courseSectionRepository.findByCourseIdAndStatusOrderByChapterIdAscSortOrderAscIdAsc(id, "ACTIVE"));
    }

    @PostMapping("/{id}/chapters/{chapterId}/sections")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    public Result<Long> createSection(@PathVariable Long id, @PathVariable Long chapterId, @RequestBody Map<String, Object> body) {
        requireManageable(id); requireChapter(id, chapterId);
        CourseSection section = CourseSection.builder()
                .courseId(id).chapterId(chapterId).title(text(body, "title")).subtitle(text(body, "subtitle"))
                .description(text(body, "description")).sortOrder(intValue(body.get("sortOrder"), 0)).status("ACTIVE").build();
        validateTitle(section.getTitle(), "小节名称");
        return Result.success(courseSectionRepository.save(section).getId());
    }

    @PutMapping("/{id}/chapters/{chapterId}/sections/{sectionId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    public Result<Void> updateSection(@PathVariable Long id, @PathVariable Long chapterId, @PathVariable Long sectionId, @RequestBody Map<String, Object> body) {
        requireManageable(id); requireChapter(id, chapterId);
        CourseSection section = courseSectionRepository.findByIdAndChapterIdAndCourseId(sectionId, chapterId, id).orElseThrow(() -> new NotFoundException("小节", sectionId));
        section.setTitle(text(body, "title")); section.setSubtitle(text(body, "subtitle")); section.setDescription(text(body, "description"));
        section.setSortOrder(intValue(body.get("sortOrder"), section.getSortOrder()));
        validateTitle(section.getTitle(), "小节名称"); courseSectionRepository.save(section);
        return Result.success();
    }

    @PostMapping("/{id}/chapters/{chapterId}/sections/{sectionId}/archive")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    public Result<Void> archiveSection(@PathVariable Long id, @PathVariable Long chapterId, @PathVariable Long sectionId) {
        requireManageable(id); requireChapter(id, chapterId);
        CourseSection section = courseSectionRepository.findByIdAndChapterIdAndCourseId(sectionId, chapterId, id).orElseThrow(() -> new NotFoundException("小节", sectionId));
        section.setStatus("ARCHIVED"); courseSectionRepository.save(section);
        return Result.success();
    }

    private CourseVO toVO(Course course) {
        CourseVO vo = new CourseVO();
        BeanUtils.copyProperties(course, vo);
        vo.setTeacherName(userRepository.findById(course.getCreatedBy())
                .map(user -> user.getNickname() != null && !user.getNickname().isBlank()
                        ? user.getNickname() : user.getUsername())
                .orElse("未分配教师"));
        vo.setAllowedAcademicClassIds(allowedAcademicClasses.findByCourseIdOrderByAcademicClassIdAsc(course.getId())
                .stream().map(item -> item.getAcademicClassId()).collect(Collectors.toList()));
        return vo;
    }

    private CourseMemberVO toMemberVO(CourseMember member) {
        CourseMemberVO vo = new CourseMemberVO();
        vo.setUserId(member.getUserId()); vo.setRole(member.getRole()); vo.setJoinedAt(member.getJoinedAt());
        userRepository.findById(member.getUserId()).ifPresent(user -> {
            vo.setUsername(user.getUsername()); vo.setNickname(user.getNickname());
            vo.setEmail(user.getEmail()); vo.setAvatar(user.getAvatar());
        });
        return vo;
    }

    private String normalizeMemberRole(String role) {
        String normalized = role == null ? "" : role.trim().toUpperCase();
        if (!List.of("CO_TEACHER", "TEACHING_ASSISTANT", "OBSERVER").contains(normalized)) {
            throw new BusinessException("无效的课程成员角色");
        }
        return normalized;
    }

    private Course requireWorkspaceAccess(Long id) {
        Course course = courseRepository.findById(id).orElseThrow(() -> new NotFoundException("课程", id));
        CustomUserDetails user = currentUser();
        requireCourseSchool(course, user);
        if (!user.isAdmin() && !user.getId().equals(course.getCreatedBy())
                && !courseMemberRepository.findByCourseIdAndUserId(id, user.getId())
                .map(member -> "ACTIVE".equals(member.getStatus())).orElse(false)) {
            throw new BusinessException("无权查看该课程");
        }
        return course;
    }

    private Course requireManageable(Long id) {
        Course course = courseRepository.findById(id).orElseThrow(() -> new NotFoundException("课程", id));
        CustomUserDetails user = currentUser();
        requireCourseSchool(course, user);
        if (!user.isAdmin() && !user.getId().equals(course.getCreatedBy())) {
            throw new BusinessException("无权管理其他教师创建的课程");
        }
        return course;
    }

    private Course requireChapter(Long courseId, Long chapterId) {
        courseChapterRepository.findByIdAndCourseId(chapterId, courseId)
                .orElseThrow(() -> new NotFoundException("章节", chapterId));
        return courseRepository.findById(courseId).orElseThrow(() -> new NotFoundException("课程", courseId));
    }

    private Course requireCourseContentAccess(Long id) {
        Course course = courseRepository.findById(id).orElseThrow(() -> new NotFoundException("课程", id));
        CustomUserDetails user = currentUser();
        boolean student = user.getAuthorities().stream().anyMatch(item -> "ROLE_STUDENT".equals(item.getAuthority()));
        if (!student) return requireWorkspaceAccess(id);
        if (!"ACTIVE".equals(course.getStatus())) throw new NotFoundException("课程", id);
        List<Long> classroomIds = classroomRepository.findByCourseIdOrderByCreatedAtDesc(id).stream()
                .filter(item -> "ACTIVE".equals(item.getStatus())).map(Classroom::getId).collect(Collectors.toList());
        if (classroomIds.isEmpty() || !classroomStudentRelationRepository.existsByClassroomIdInAndStudentIdAndStatus(classroomIds, user.getId(), "ACTIVE")) {
            throw new BusinessException("您尚未加入该课程");
        }
        return course;
    }

    private void validateTitle(String title, String label) {
        if (title == null || title.isBlank()) throw new BusinessException("请输入" + label);
    }

    private String text(Map<String, Object> body, String key) { Object value = body.get(key); return value == null ? "" : String.valueOf(value).trim(); }
    private int intValue(Object value, int fallback) { try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); } catch (NumberFormatException ex) { return fallback; } }

    private CustomUserDetails currentUser() {
        return (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private User currentUserEntity(CustomUserDetails user) {
        return userRepository.findById(user.getId()).orElseThrow(() -> new NotFoundException("用户", user.getId()));
    }

    private Long currentSchoolId(CustomUserDetails user) {
        User current = currentUserEntity(user);
        if (isPlatformAdmin(user)) return null;
        return current.getSchoolId() == null ? -1L : current.getSchoolId();
    }

    private boolean isPlatformAdmin(CustomUserDetails user) {
        return user.getAuthorities().stream().anyMatch(item -> "ROLE_SUPER_ADMIN".equals(item.getAuthority()));
    }

    private void requireCourseSchool(Course course, CustomUserDetails user) {
        if (isPlatformAdmin(user)) return;
        Long schoolId = currentSchoolId(user);
        if (schoolId == null || course.getSchoolId() == null || !schoolId.equals(course.getSchoolId())) {
            throw new BusinessException("无权访问其他学校的课程");
        }
    }

    private List<CourseVO> toVOs(List<Course> courses) {
        return courses.stream().map(course -> {
            CourseVO vo = new CourseVO();
            BeanUtils.copyProperties(course, vo);
            vo.setTeacherName(userRepository.findById(course.getCreatedBy())
                    .map(user -> user.getNickname() != null && !user.getNickname().isBlank()
                            ? user.getNickname() : user.getUsername())
                    .orElse("未分配教师"));
            vo.setAllowedAcademicClassIds(allowedAcademicClasses.findByCourseIdOrderByAcademicClassIdAsc(course.getId())
                    .stream().map(item -> item.getAcademicClassId()).collect(Collectors.toList()));
            return vo;
        }).collect(Collectors.toList());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void validateTeachingDepartment(Long schoolId, String departmentName) {
        if (departmentName == null || departmentName.isBlank()) return;
        schoolDepartmentRepository.findBySchoolIdAndNameAndStatus(schoolId, departmentName.trim(), "ACTIVE")
                .orElseThrow(() -> new BusinessException("开课院系不存在，请先在学校管理中维护"));
    }

    private void ensureCodeAvailable(String courseCode, Long courseId) {
        if (courseCode == null) return;
        boolean exists = courseId == null
                ? courseRepository.existsByCourseCode(courseCode)
                : courseRepository.existsByCourseCodeAndIdNot(courseCode, courseId);
        if (exists) {
            throw new BusinessException("课程代码已存在，请使用其他代码");
        }
    }

    private void replaceAllowedAcademicClasses(Course course, List<Long> ids, CustomUserDetails operator) {
        if (ids == null) return;
        Long schoolId = currentSchoolId(operator);
        List<Long> normalized = ids.stream().filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        List<AcademicClass> classes = normalized.isEmpty() ? List.of() : academicClassRepository.findAllById(normalized);
        if (classes.size() != normalized.size()) throw new BusinessException("存在无效的行政班");
        if (classes.stream().anyMatch(item -> !"ACTIVE".equals(item.getStatus())
                || item.getSchoolId() == null || !item.getSchoolId().equals(schoolId)
                || (course.getSchoolId() != null && !course.getSchoolId().equals(item.getSchoolId())))) {
            throw new BusinessException("只能选择本校有效行政班");
        }
        allowedAcademicClasses.deleteByCourseId(course.getId());
        normalized.forEach(classId -> allowedAcademicClasses.save(CourseAllowedAcademicClass.builder()
                .courseId(course.getId()).academicClassId(classId).build()));
    }

}
