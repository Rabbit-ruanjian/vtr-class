package com.vtr.service.impl;

import com.vtr.common.PageResult;
import com.vtr.common.exception.BusinessException;
import com.vtr.common.exception.NotFoundException;
import com.vtr.dto.AddStudentToClassroomDTO;
import com.vtr.dto.ClassroomCreateDTO;
import com.vtr.dto.ClassroomUpdateDTO;
import com.vtr.dto.JoinClassroomDTO;
import com.vtr.entity.Classroom;
import com.vtr.entity.Course;
import com.vtr.entity.ClassroomStudentRelation;
import com.vtr.entity.AcademicClassStudentRoster;
import com.vtr.entity.User;
import com.vtr.repository.ClassroomRepository;
import com.vtr.repository.ClassroomStudentRelationRepository;
import com.vtr.repository.CourseRepository;
import com.vtr.repository.AcademicClassRepository;
import com.vtr.repository.CourseAllowedAcademicClassRepository;
import com.vtr.repository.AcademicClassStudentRosterRepository;
import com.vtr.repository.UserRepository;
import com.vtr.service.ClassroomService;
import com.vtr.vo.ClassroomVO;
import com.vtr.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClassroomServiceImpl implements ClassroomService {

    private static final char[] INVITE_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom INVITE_CODE_RANDOM = new SecureRandom();

    private final ClassroomRepository classroomRepository;
    private final ClassroomStudentRelationRepository relationRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final AcademicClassRepository academicClassRepository;
    private final CourseAllowedAcademicClassRepository allowedAcademicClassRepository;
    private final AcademicClassStudentRosterRepository studentRosterRepository;

    @Override
    @Transactional
    public Long createClassroom(ClassroomCreateDTO dto, Long teacherId) {
        requireManageableActiveCourse(dto.getCourseId(), teacherId);
        String inviteCode = normalizeInviteCode(dto.getInviteCode());
        if (inviteCode == null) {
            inviteCode = generateUniqueInviteCode();
        } else if (classroomRepository.existsByInviteCode(inviteCode)) {
            throw new BusinessException("邀请码已被使用，请更换后重试");
        }

        Classroom classroom = Classroom.builder()
                .className(dto.getClassName())
                .description(dto.getDescription())
                .grade(dto.getGrade())
                .semester(dto.getSemester())
                .inviteCode(inviteCode)
                .teacherId(teacherId)
                .courseId(dto.getCourseId())
                .studentCount(0)
                .status("ACTIVE")
                .build();

        Classroom saved = classroomRepository.save(classroom);
        log.info("教师创建班级: teacherId={}, classroomId={}, className={}, inviteCode={}", teacherId, saved.getId(), saved.getClassName(), inviteCode);
        return saved.getId();
    }

    @Override
    @Transactional
    public void joinClassroom(JoinClassroomDTO dto, Long studentId) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new NotFoundException("学生", studentId));
        if (student.getRole() != User.UserRole.STUDENT || student.getStatus() != User.UserStatus.ACTIVE) {
            throw new BusinessException("只有正常状态的学生账户可以加入班级");
        }

        String inviteCode = normalizeInviteCode(dto.getInviteCode());
        // A course code can be used before the teacher creates a named teaching class.
        if (!classroomRepository.findByInviteCodeAndStatus(inviteCode, "ACTIVE").isPresent()) {
            Course enrollmentCourse = courseRepository.findByCourseCode(inviteCode).orElse(null);
            if (enrollmentCourse != null && "ACTIVE".equals(enrollmentCourse.getStatus())) {
                try {
                    classroomRepository.saveAndFlush(Classroom.builder()
                            .className(enrollmentCourse.getCourseName() + " Default Class")
                            .description("Students joined with the course enrollment code")
                            .semester(enrollmentCourse.getSemester()).inviteCode(enrollmentCourse.getCourseCode())
                            .teacherId(enrollmentCourse.getCreatedBy()).courseId(enrollmentCourse.getId())
                            .studentCount(0).status("ACTIVE").build());
                } catch (org.springframework.dao.DataIntegrityViolationException ignored) {
                    // Another enrollment request created the default class first.
                }
            }
        }
        Classroom classroom = classroomRepository.findByInviteCodeAndStatus(inviteCode, "ACTIVE")
                .orElseThrow(() -> new BusinessException("邀请码无效或班级已归档"));
        Course course = courseRepository.findById(classroom.getCourseId())
                .orElseThrow(() -> new NotFoundException("课程", classroom.getCourseId()));
        if (!"ACTIVE".equals(course.getStatus())) {
            throw new BusinessException("课程已归档，暂不支持加入教学班");
        }
        List<Long> allowedClassIds = allowedAcademicClassRepository.findByCourseIdOrderByAcademicClassIdAsc(course.getId())
                .stream().map(item -> item.getAcademicClassId()).collect(Collectors.toList());
        if (!allowedClassIds.isEmpty()) {
            verifyStudentBelongsToAllowedClass(student, allowedClassIds);
        }

        relationRepository.insertOrReactivate(classroom.getId(), studentId, student.getUsername());
        classroomRepository.updateStudentCount(classroom.getId());
        log.info("学生通过邀请码加入班级: studentId={}, classroomId={}", studentId, classroom.getId());
    }

    @Override
    public List<ClassroomVO> getJoinedClassrooms(Long studentId) {
        return relationRepository.findByStudentIdAndStatus(studentId, "ACTIVE").stream()
                .map(relation -> classroomRepository.findById(relation.getClassroomId()).orElse(null))
                .filter(classroom -> classroom != null && "ACTIVE".equals(classroom.getStatus()))
                .map(this::toClassroomVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void leaveClassroom(Long classroomId, Long studentId) {
        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new NotFoundException("班级", classroomId));
        if (!"ACTIVE".equals(classroom.getStatus())) {
            throw new BusinessException("教学班已归档，不能退出");
        }
        int removed = relationRepository.removeStudentFromClassroom(classroomId, studentId);
        if (removed == 0) {
            throw new BusinessException("你当前不在这个教学班中");
        }
        classroomRepository.updateStudentCount(classroomId);
        log.info("学生主动退出教学班: classroomId={}, studentId={}", classroomId, studentId);
    }

    private String normalizeInviteCode(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 校验学生是否属于课程允许进入的行政班。
     * 以学校维护的行政班名单（academic_class_student）中的学号为权威依据：
     * 学生必须先拥有学号身份，且该学号所在的行政班在课程允许列表内，才能加入。
     */
    private void verifyStudentBelongsToAllowedClass(User student, List<Long> allowedClassIds) {
        if (student.getIdentityType() != User.IdentityType.STUDENT
                || !StringUtils.hasText(student.getIdentityNumber())) {
            throw new BusinessException("请先在个人资料中完成学号认证后再加入该课程");
        }
        if (student.getSchoolId() == null) {
            throw new BusinessException("你的账号尚未归属学校，无法加入该课程");
        }
        String studentNumber = student.getIdentityNumber().trim();
        AcademicClassStudentRoster roster = studentRosterRepository
                .findBySchoolIdAndStudentNumber(student.getSchoolId(), studentNumber)
                .orElseThrow(() -> new BusinessException("学号 " + studentNumber + " 不在该课程面向的行政班名单中，无法加入"));
        if (!"ACTIVE".equals(roster.getStatus())
                || roster.getAcademicClassId() == null
                || !allowedClassIds.contains(roster.getAcademicClassId())) {
            throw new BusinessException("学号 " + studentNumber + " 不属于该课程面向的行政班，无法加入");
        }
        if (roster.getUserId() != null && !roster.getUserId().equals(student.getId())) {
            throw new BusinessException("该学号已绑定其他账号，无法加入该课程");
        }
    }

    private String generateUniqueInviteCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder code = new StringBuilder(8);
            for (int index = 0; index < 8; index++) {
                code.append(INVITE_CODE_CHARS[INVITE_CODE_RANDOM.nextInt(INVITE_CODE_CHARS.length)]);
            }
            if (!classroomRepository.existsByInviteCode(code.toString())) {
                return code.toString();
            }
        }
        throw new BusinessException("邀请码生成失败，请重试");
    }

    private ClassroomVO toClassroomVO(Classroom classroom) {
        ClassroomVO vo = new ClassroomVO();
        BeanUtils.copyProperties(classroom, vo);
        userRepository.findById(classroom.getTeacherId()).ifPresent(teacher ->
                vo.setTeacherName(teacher.getNickname() != null ? teacher.getNickname() : teacher.getUsername()));
        return vo;
    }

    @Override
    public void updateClassroom(Long id, ClassroomUpdateDTO dto, Long teacherId) {
        Classroom classroom = requireManageableClassroom(id, teacherId, true);

        if (dto.getClassName() != null) {
            classroom.setClassName(dto.getClassName());
        }
        if (dto.getDescription() != null) {
            classroom.setDescription(dto.getDescription());
        }
        if (dto.getGrade() != null) {
            classroom.setGrade(dto.getGrade());
        }
        if (dto.getSemester() != null) {
            classroom.setSemester(dto.getSemester());
        }
        if (dto.getCourseId() != null) {
            requireManageableActiveCourse(dto.getCourseId(), teacherId);
            classroom.setCourseId(dto.getCourseId());
        }

        classroomRepository.save(classroom);
        log.info("更新班级信息: classroomId={}", id);
    }

    @Override
    @Transactional
    public void deleteClassroom(Long id, Long teacherId) {
        Classroom classroom = requireManageableClassroom(id, teacherId, true);

        classroom.setStatus("ARCHIVED");
        classroomRepository.save(classroom);
        relationRepository.removeAllStudentsFromClassroom(id);

        log.info("删除班级: classroomId={}", id);
    }

    @Override
    @Transactional
    public void archiveClassroom(Long id, Long teacherId) {
        Classroom classroom = requireManageableClassroom(id, teacherId, true);

        classroom.setStatus("ARCHIVED");
        classroomRepository.save(classroom);

        log.info("归档班级: classroomId={}", id);
    }

    @Override
    @Transactional
    public void restoreClassroom(Long id, Long teacherId) {
        Classroom classroom = requireManageableClassroom(id, teacherId, false);

        if (!"ARCHIVED".equals(classroom.getStatus())) {
            throw new BusinessException("只有已归档的班级才能恢复");
        }
        Course course = courseRepository.findById(classroom.getCourseId())
                .orElseThrow(() -> new NotFoundException("课程", classroom.getCourseId()));
        if (!"ACTIVE".equals(course.getStatus())) {
            throw new BusinessException("课程已归档，不能恢复教学班");
        }

        classroom.setStatus("ACTIVE");
        classroomRepository.save(classroom);

        log.info("恢复班级: classroomId={}", id);
    }

    @Override
    public ClassroomVO getClassroomById(Long id, Long teacherId) {
        Classroom classroom = requireManageableClassroom(id, teacherId, false);

        ClassroomVO vo = new ClassroomVO();
        BeanUtils.copyProperties(classroom, vo);

        userRepository.findById(classroom.getTeacherId()).ifPresent(teacher -> {
            vo.setTeacherName(teacher.getNickname() != null ? teacher.getNickname() : teacher.getUsername());
        });

        return vo;
    }

    @Override
    public PageResult<ClassroomVO> getMyClassrooms(Long teacherId, Integer page, Integer size, String keyword, String status) {
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by("createdAt").descending());

        String queryStatus = StringUtils.hasText(status) ? status : "ACTIVE";

        Page<Classroom> classroomPage;
        if (StringUtils.hasText(keyword)) {
            classroomPage = classroomRepository.searchByKeywordAndStatus(teacherId, keyword.trim(), queryStatus, pageRequest);
        } else {
            classroomPage = classroomRepository.findByTeacherIdAndStatus(teacherId, queryStatus, pageRequest);
        }

        User teacher = userRepository.findById(teacherId).orElse(null);
        String teacherName = teacher != null ? (teacher.getNickname() != null ? teacher.getNickname() : teacher.getUsername()) : "未知";

        List<ClassroomVO> vos = classroomPage.getContent().stream()
                .map(c -> {
                    ClassroomVO vo = new ClassroomVO();
                    BeanUtils.copyProperties(c, vo);
                    vo.setTeacherId(teacherId);
                    vo.setTeacherName(teacherName);
                    return vo;
                })
                .collect(Collectors.toList());

        return PageResult.of(vos, classroomPage.getTotalElements(), page, size);
    }

    @Override
    @Transactional
    public void addStudentsToClassroom(AddStudentToClassroomDTO dto, Long teacherId) {
        Classroom classroom = requireManageableClassroom(dto.getClassroomId(), teacherId, true);
        Course course = courseRepository.findById(classroom.getCourseId())
                .orElseThrow(() -> new NotFoundException("课程", classroom.getCourseId()));

        List<Long> studentIds = dto.getStudentIds();

        if ((studentIds == null || studentIds.isEmpty()) && dto.getStudentNumbers() != null && !dto.getStudentNumbers().isEmpty()) {
            studentIds = dto.getStudentNumbers().stream()
                    .map(num -> findStudentByNumber(teacherId, num.trim())
                            .filter(u -> u.getRole() == User.UserRole.STUDENT)
                            .map(User::getId)
                            .orElseThrow(() -> new BusinessException("未找到学生: " + num)))
                    .collect(Collectors.toList());
        }

        if (studentIds == null || studentIds.isEmpty()) {
            throw new BusinessException("请提供要添加的学生");
        }

        int addedCount = 0;
        for (Long studentId : studentIds) {
            User student = userRepository.findById(studentId)
                    .orElseThrow(() -> new NotFoundException("学生", studentId));

            if (student.getRole() != User.UserRole.STUDENT) {
                log.warn("跳过非学生用户: userId={}", studentId);
                continue;
            }

            if (student.getStatus() != User.UserStatus.ACTIVE) {
                log.warn("跳过非活跃学生: userId={}", studentId);
                continue;
            }
            if (course.getSchoolId() != null && student.getSchoolId() != null
                    && !course.getSchoolId().equals(student.getSchoolId())) {
                throw new BusinessException("只能将本校学生加入该课程教学班");
            }

            int result = relationRepository.insertOrReactivate(
                    dto.getClassroomId(),
                    studentId,
                    student.getUsername()
            );

            if (result > 0) {
                addedCount++;
            }
        }

        classroomRepository.updateStudentCount(dto.getClassroomId());
        log.info("添加学生到班级: classroomId={}, added={}", dto.getClassroomId(), addedCount);
    }

    @Override
    @Transactional
    public void removeStudentFromClassroom(Long classroomId, Long studentId, Long teacherId) {
        requireManageableClassroom(classroomId, teacherId, true);

        int removed = relationRepository.removeStudentFromClassroom(classroomId, studentId);
        if (removed == 0) {
            throw new BusinessException("该学生不在班级中");
        }

        classroomRepository.updateStudentCount(classroomId);
        log.info("从班级移除学生: classroomId={}, studentId={}", classroomId, studentId);
    }

    @Override
    public PageResult<UserVO> getClassroomStudents(Long classroomId, Long teacherId, Integer page, Integer size, String keyword) {
        requireManageableClassroom(classroomId, teacherId, false);

        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 100);

        List<UserVO> studentVOs = relationRepository.findByClassroomIdAndStatus(classroomId, "ACTIVE").stream()
                .map(relation -> userRepository.findById(relation.getStudentId())
                        .map(student -> java.util.Map.entry(relation, student)).orElse(null))
                .filter(item -> item != null)
                .map(item -> {
                    ClassroomStudentRelation relation = item.getKey();
                    User student = item.getValue();
                    UserVO vo = new UserVO();
                    BeanUtils.copyProperties(student, vo);
                    vo.setStatus(student.getStatus() == null ? null : student.getStatus().name());
                    vo.setJoinedAt(relation.getCreatedAt());
                    if (student.getAcademicClassId() != null) {
                        academicClassRepository.findById(student.getAcademicClassId())
                                .ifPresent(academicClass -> vo.setAcademicClassName(academicClass.getName()));
                    }
                    if (vo.getUsername() == null && student.getUsername() != null) {
                        vo.setUsername(student.getUsername());
                    }
                    return vo;
                })
                .collect(Collectors.toList());

        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim().toLowerCase();
            studentVOs = studentVOs.stream()
                    .filter(s -> (s.getUsername() != null && s.getUsername().toLowerCase().contains(kw)) ||
                            (s.getNickname() != null && s.getNickname().toLowerCase().contains(kw)) ||
                            (s.getIdentityNumber() != null && s.getIdentityNumber().toLowerCase().contains(kw)) ||
                            (s.getEmail() != null && s.getEmail().toLowerCase().contains(kw)))
                    .collect(Collectors.toList());
        }

        long total = studentVOs.size();
        int fromIndex = Math.min((safePage - 1) * safeSize, studentVOs.size());
        int toIndex = Math.min(fromIndex + safeSize, studentVOs.size());
        return PageResult.of(studentVOs.subList(fromIndex, toIndex), total, safePage, safeSize);
    }

    @Override
    public List<ClassroomVO> getStudentClassrooms(Long studentId, Long teacherId) {
        List<ClassroomStudentRelation> relations = relationRepository.findByStudentIdAndStatus(studentId, "ACTIVE");

        return relations.stream()
                .map(relation -> classroomRepository.findById(relation.getClassroomId()).orElse(null))
                .filter(classroom -> classroom != null && classroom.getTeacherId().equals(teacherId))
                .map(classroom -> {
                    ClassroomVO vo = new ClassroomVO();
                    BeanUtils.copyProperties(classroom, vo);
                    vo.setTeacherId(teacherId);
                    userRepository.findById(teacherId).ifPresent(teacher -> {
                        vo.setTeacherName(teacher.getNickname() != null ? teacher.getNickname() : teacher.getUsername());
                    });
                    return vo;
                })
                .collect(Collectors.toList());
    }

    // ==================== 管理员接口实现 ====================

    @Override
    public PageResult<ClassroomVO> getAllClassroomsForAdmin(Integer page, Integer size, String keyword, String status) {
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by("createdAt").descending());

        String queryStatus = StringUtils.hasText(status) ? status : null;

        Page<Classroom> classroomPage;
        if (StringUtils.hasText(keyword)) {
            if (queryStatus != null) {
                classroomPage = classroomRepository.searchByKeywordAndStatusForAdmin(keyword.trim(), queryStatus, pageRequest);
            } else {
                classroomPage = classroomRepository.searchByKeywordForAdmin(keyword.trim(), pageRequest);
            }
        } else {
            if (queryStatus != null) {
                classroomPage = classroomRepository.findByStatus(queryStatus, pageRequest);
            } else {
                classroomPage = classroomRepository.findAll(pageRequest);
            }
        }

        List<ClassroomVO> vos = classroomPage.getContent().stream()
                .map(c -> {
                    ClassroomVO vo = new ClassroomVO();
                    BeanUtils.copyProperties(c, vo);
                    userRepository.findById(c.getTeacherId()).ifPresent(teacher -> {
                        vo.setTeacherName(teacher.getNickname() != null ? teacher.getNickname() : teacher.getUsername());
                    });
                    return vo;
                })
                .collect(Collectors.toList());

        return PageResult.of(vos, classroomPage.getTotalElements(), page, size);
    }

    @Override
    @Transactional
    public void assignTeacherToClassroom(Long classroomId, Long teacherId) {
        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new NotFoundException("班级", classroomId));

        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new NotFoundException("教师", teacherId));

        if (teacher.getRole() != User.UserRole.TEACHER &&
                teacher.getRole() != User.UserRole.ADMIN &&
                teacher.getRole() != User.UserRole.SUPER_ADMIN) {
            throw new BusinessException("只能安排教师角色用户作为班级负责人");
        }

        classroom.setTeacherId(teacherId);
        classroomRepository.save(classroom);

        log.info("管理员安排班级教师: classroomId={}, teacherId={}", classroomId, teacherId);
    }

    @Override
    @Transactional
    public void adminDeleteClassroom(Long id) {
        Classroom classroom = classroomRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("班级", id));

        relationRepository.removeAllStudentsFromClassroom(id);
        classroomRepository.delete(classroom);

        log.info("管理员删除班级: classroomId={}", id);
    }

    private Course requireManageableActiveCourse(Long courseId, Long userId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new NotFoundException("课程", courseId));
        if (!"ACTIVE".equals(course.getStatus())) {
            throw new BusinessException("已归档课程不能创建或调整教学班");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("用户", userId));
        if (!user.isAdmin() && !userId.equals(course.getCreatedBy())) {
            throw new BusinessException("只能管理自己负责的课程教学班");
        }
        return course;
    }

    private Classroom requireManageableClassroom(Long classroomId, Long userId, boolean requireActive) {
        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new NotFoundException("班级", classroomId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("用户", userId));
        if (!user.isAdmin() && !userId.equals(classroom.getTeacherId())) {
            throw new BusinessException("无权管理其他教师负责的教学班");
        }
        if (requireActive && !"ACTIVE".equals(classroom.getStatus())) {
            throw new BusinessException("教学班已归档，不能执行该操作");
        }
        return classroom;
    }

    private java.util.Optional<User> findStudentByNumber(Long requesterId, String studentNumber) {
        User requester = userRepository.findById(requesterId).orElse(null);
        if (requester != null && requester.getSchoolId() != null) {
            java.util.Optional<User> scoped = userRepository.findBySchoolIdAndIdentityTypeAndIdentityNumber(
                    requester.getSchoolId(), User.IdentityType.STUDENT, studentNumber);
            if (scoped.isPresent()) return scoped;
            scoped = userRepository.findBySchoolIdAndUsername(requester.getSchoolId(), studentNumber);
            if (scoped.isPresent()) return scoped;
            return java.util.Optional.empty();
        }
        return userRepository.findByUsername(studentNumber);
    }
}
