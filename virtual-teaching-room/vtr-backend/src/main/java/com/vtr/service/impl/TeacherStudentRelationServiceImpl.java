package com.vtr.service.impl;

import com.vtr.common.PageResult;
import com.vtr.common.exception.BusinessException;
import com.vtr.common.exception.NotFoundException;
import com.vtr.dto.AddStudentDTO;
import com.vtr.dto.BatchImportStudentDTO;
import com.vtr.dto.ChangeClassroomDTO;
import com.vtr.entity.Classroom;
import com.vtr.entity.ClassroomStudentRelation;
import com.vtr.entity.User;
import com.vtr.repository.ClassroomRepository;
import com.vtr.repository.ClassroomStudentRelationRepository;
import com.vtr.repository.UserRepository;
import com.vtr.service.TeacherStudentRelationService;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeacherStudentRelationServiceImpl implements TeacherStudentRelationService {

    private final UserRepository userRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomStudentRelationRepository classroomStudentRelationRepository;

    /**
     * 获取教师的所有班级ID
     */
    private List<Long> getTeacherClassroomIds(Long teacherId) {
        return classroomRepository.findByTeacherIdAndStatus(teacherId, "ACTIVE")
                .stream()
                .map(Classroom::getId)
                .collect(Collectors.toList());
    }

    /**
     * 获取教师的所有学生（通过班级关联）
     */
    @Override
    public PageResult<UserVO> getMyStudents(Long teacherId, Integer page, Integer size, String keyword, Long classroomId) {
        List<Long> classroomIds;

        if (classroomId != null) {
            // 按指定班级过滤
            classroomIds = List.of(classroomId);
            // 验证班级属于该教师
            classroomRepository.findByIdAndTeacherIdAndStatus(classroomId, teacherId, "ACTIVE")
                    .orElseThrow(() -> new NotFoundException("班级", classroomId));
        } else {
            // 获取教师的所有班级
            classroomIds = getTeacherClassroomIds(teacherId);
        }

        if (classroomIds.isEmpty()) {
            return PageResult.of(new ArrayList<>(), 0L, page, size);
        }

        // 查询班级学生关系
        List<ClassroomStudentRelation> relations = classroomStudentRelationRepository
                .findByClassroomIdInAndStatus(classroomIds, "ACTIVE");

        if (relations.isEmpty()) {
            return PageResult.of(new ArrayList<>(), 0L, page, size);
        }

        // 获取学生ID列表
        List<Long> studentIds = relations.stream()
                .map(ClassroomStudentRelation::getStudentId)
                .distinct()
                .collect(Collectors.toList());

        // 查询学生信息
        List<User> students = userRepository.findAllById(studentIds);

        // 构建学生班级映射
        Map<Long, Classroom> studentClassroomMap = new HashMap<>();
        for (ClassroomStudentRelation relation : relations) {
            classroomRepository.findById(relation.getClassroomId()).ifPresent(classroom ->
                    studentClassroomMap.put(relation.getStudentId(), classroom)
            );
        }

        // 构建返回数据
        List<UserVO> studentVOs = new ArrayList<>();
        for (User student : students) {
            UserVO vo = new UserVO();
            BeanUtils.copyProperties(student, vo);

            Classroom studentClassroom = studentClassroomMap.get(student.getId());
            if (studentClassroom != null) {
                vo.setClassroomId(studentClassroom.getId());
                vo.setClassroomName(studentClassroom.getClassName());
            }
            studentVOs.add(vo);
        }

        // 关键词过滤
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.toLowerCase();
            studentVOs = studentVOs.stream()
                    .filter(s -> (s.getUsername() != null && s.getUsername().toLowerCase().contains(kw)) ||
                            (s.getNickname() != null && s.getNickname().toLowerCase().contains(kw)))
                    .collect(Collectors.toList());
        }

        // 排序
        studentVOs.sort(Comparator
                .comparing(UserVO::getClassroomName, Comparator.nullsLast(String::compareTo))
                .thenComparing(UserVO::getNickname, Comparator.nullsLast(String::compareTo)));

        // 分页
        int start = (page - 1) * size;
        int end = Math.min(start + size, studentVOs.size());
        List<UserVO> pagedList = start < studentVOs.size() ? studentVOs.subList(start, end) : new ArrayList<>();

        log.info("获取学生列表: teacherId={}, classroomId={}, 总数={}", teacherId, classroomId, studentVOs.size());

        return PageResult.of(pagedList, (long) studentVOs.size(), page, size);
    }

    @Override
    public List<UserVO> getMyTeachers(Long studentId) {
        List<ClassroomStudentRelation> relations = classroomStudentRelationRepository
                .findByStudentIdAndStatus(studentId, "ACTIVE");

        List<UserVO> teachers = new ArrayList<>();
        for (ClassroomStudentRelation relation : relations) {
            classroomRepository.findById(relation.getClassroomId()).ifPresent(classroom ->
                    userRepository.findById(classroom.getTeacherId()).ifPresent(teacher -> {
                        UserVO vo = new UserVO();
                        BeanUtils.copyProperties(teacher, vo);
                        teachers.add(vo);
                    })
            );
        }

        return teachers.stream().distinct().collect(Collectors.toList());
    }

    @Override
    public UserVO searchStudentByStudentId(Long requesterId, String studentNumber) {
        User student = findStudentByNumber(requesterId, studentNumber)
                .orElseThrow(() -> new NotFoundException("学生(学号: " + studentNumber + ")"));

        if (student.getRole() != User.UserRole.STUDENT) {
            throw new BusinessException("该用户不是学生，无法添加");
        }

        UserVO vo = new UserVO();
        BeanUtils.copyProperties(student, vo);
        return vo;
    }

    @Override
    @Transactional
    public void addStudentToTeacher(Long teacherId, AddStudentDTO dto) {
        User student;
        if (dto.getStudentId() != null) {
            student = userRepository.findById(dto.getStudentId())
                    .orElseThrow(() -> new NotFoundException("学生", dto.getStudentId()));
        } else if (dto.getStudentNumber() != null) {
            student = findStudentByNumber(teacherId, dto.getStudentNumber())
                    .orElseThrow(() -> new NotFoundException("学生(学号: " + dto.getStudentNumber() + ")"));
        } else {
            throw new BusinessException("请提供学生ID或学号");
        }

        if (student.getRole() != User.UserRole.STUDENT) {
            throw new BusinessException("该用户不是学生，无法添加");
        }

        if (student.getStatus() != User.UserStatus.ACTIVE) {
            throw new BusinessException("该学生账号状态异常（未审核或已禁用），无法添加");
        }

        // 如果指定了教学班，将学生添加到该教学班。学生可以同时参加多门课程。
        if (dto.getClassroomId() != null) {
            addStudentToClassroom(teacherId, dto.getClassroomId(), student);
        }
    }

    private void addStudentToClassroom(Long teacherId, Long classroomId, User student) {
        Classroom classroom = classroomRepository.findByIdAndTeacherIdAndStatus(classroomId, teacherId, "ACTIVE")
                .orElseThrow(() -> new NotFoundException("班级", classroomId));

        // 教学班关系按课程独立维护，不影响学生在其他课程中的选课。
        int result = classroomStudentRelationRepository.insertOrReactivate(classroomId, student.getId(), student.getUsername());
        if (result > 0) {
            classroomRepository.updateStudentCount(classroomId);
            log.info("添加学生到班级: classroomId={}, studentId={}", classroomId, student.getId());
        }
    }

    @Override
    @Transactional
    public void removeStudentFromTeacher(Long teacherId, Long studentId) {
        List<Long> classroomIds = getTeacherClassroomIds(teacherId);

        for (Long classroomId : classroomIds) {
            classroomStudentRelationRepository.removeStudentFromClassroom(classroomId, studentId);
            classroomRepository.updateStudentCount(classroomId);
        }

        log.info("移除学生: teacherId={}, studentId={}", teacherId, studentId);
    }

    @Override
    @Transactional
    public void batchImportStudents(Long teacherId, BatchImportStudentDTO dto) {
        if (dto.getStudentNumbers() == null || dto.getStudentNumbers().isEmpty()) {
            throw new BusinessException("请提供学生学号列表");
        }

        Long classroomId = dto.getClassroomId();
        if (classroomId == null) {
            throw new BusinessException("请指定要导入到的班级");
        }

        classroomRepository.findByIdAndTeacherIdAndStatus(classroomId, teacherId, "ACTIVE")
                .orElseThrow(() -> new NotFoundException("班级", classroomId));

        int successCount = 0;
        int failCount = 0;
        List<String> failedNumbers = new ArrayList<>();

        for (String studentNumber : dto.getStudentNumbers()) {
            try {
                User student = findStudentByNumber(teacherId, studentNumber.trim())
                        .orElse(null);

                if (student == null) {
                    log.warn("批量导入失败：未找到学生, studentNumber={}", studentNumber);
                    failCount++;
                    failedNumbers.add(studentNumber + "（未找到）");
                    continue;
                }

                if (student.getRole() != User.UserRole.STUDENT) {
                    log.warn("批量导入失败：不是学生角色, studentNumber={}", studentNumber);
                    failCount++;
                    failedNumbers.add(studentNumber + "（不是学生）");
                    continue;
                }

                if (student.getStatus() != User.UserStatus.ACTIVE) {
                    log.warn("批量导入失败：学生状态异常, studentNumber={}", studentNumber);
                    failCount++;
                    failedNumbers.add(studentNumber + "（账号状态异常）");
                    continue;
                }

                addStudentToClassroom(teacherId, classroomId, student);
                successCount++;

            } catch (Exception e) {
                log.error("批量导入失败: studentNumber={}", studentNumber, e);
                failCount++;
                failedNumbers.add(studentNumber + "（系统错误）");
            }
        }

        classroomRepository.updateStudentCount(classroomId);
        log.info("批量导入完成: teacherId={}, classroomId={}, 成功={}, 失败={}", teacherId, classroomId, successCount, failCount);

        if (failCount > 0 && successCount == 0) {
            throw new BusinessException("批量导入全部失败: " + String.join(", ", failedNumbers));
        }

        if (failCount > 0) {
            log.warn("部分导入失败: {}", failedNumbers);
        }
    }

    @Override
    public List<Long> getMyStudentIds(Long teacherId) {
        List<Long> classroomIds = getTeacherClassroomIds(teacherId);
        if (classroomIds.isEmpty()) {
            return new ArrayList<>();
        }

        return classroomStudentRelationRepository.findByClassroomIdInAndStatus(classroomIds, "ACTIVE")
                .stream()
                .map(ClassroomStudentRelation::getStudentId)
                .collect(Collectors.toList());
    }

    @Override
    public List<Long> getClassroomStudentIds(Long teacherId, Long classroomId) {
        classroomRepository.findByIdAndTeacherIdAndStatus(classroomId, teacherId, "ACTIVE")
                .orElseThrow(() -> new NotFoundException("班级", classroomId));

        return classroomStudentRelationRepository.findByClassroomIdAndStatus(classroomId, "ACTIVE")
                .stream()
                .map(ClassroomStudentRelation::getStudentId)
                .collect(Collectors.toList());
    }

    @Override
    public boolean isMyStudent(Long teacherId, Long studentId) {
        List<Long> classroomIds = getTeacherClassroomIds(teacherId);
        if (classroomIds.isEmpty()) {
            return false;
        }

        return classroomStudentRelationRepository.existsByClassroomIdInAndStudentIdAndStatus(classroomIds, studentId, "ACTIVE");
    }

    @Override
    public PageResult<UserVO> getAvailableStudents(Long teacherId, Integer page, Integer size, String keyword, Long excludeClassroomId) {
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by("createdAt").descending());

        List<Long> myStudentIds = getMyStudentIds(teacherId);

        List<Long> excludeStudentIds = new ArrayList<>(myStudentIds);

        if (excludeClassroomId != null) {
            List<ClassroomStudentRelation> classroomStudents =
                    classroomStudentRelationRepository.findByClassroomIdAndStatus(excludeClassroomId, "ACTIVE");
            List<Long> classroomStudentIds = classroomStudents.stream()
                    .map(ClassroomStudentRelation::getStudentId)
                    .collect(Collectors.toList());
            excludeStudentIds.addAll(classroomStudentIds);
        }

        excludeStudentIds = excludeStudentIds.stream().distinct().collect(Collectors.toList());

        Page<User> studentPage;

        if (excludeStudentIds.isEmpty()) {
            if (StringUtils.hasText(keyword)) {
                studentPage = userRepository.findByRoleAndStatusAndUsernameContainingOrNicknameContaining(
                        User.UserRole.STUDENT,
                        User.UserStatus.ACTIVE,
                        keyword.trim(),
                        pageRequest);
            } else {
                studentPage = userRepository.findByRoleAndStatus(
                        User.UserRole.STUDENT,
                        User.UserStatus.ACTIVE,
                        pageRequest);
            }
        } else {
            if (StringUtils.hasText(keyword)) {
                studentPage = userRepository.findAvailableStudentsExcludingIdsWithKeyword(
                        excludeStudentIds,
                        User.UserRole.STUDENT,
                        User.UserStatus.ACTIVE,
                        keyword.trim(),
                        pageRequest);
            } else {
                studentPage = userRepository.findAvailableStudentsExcludingIds(
                        excludeStudentIds,
                        User.UserRole.STUDENT,
                        User.UserStatus.ACTIVE,
                        pageRequest);
            }
        }

        List<UserVO> studentVOs = studentPage.getContent().stream()
                .map(student -> {
                    UserVO vo = new UserVO();
                    BeanUtils.copyProperties(student, vo);
                    return vo;
                })
                .collect(Collectors.toList());

        return PageResult.of(studentVOs, studentPage.getTotalElements(), page, size);
    }

    @Override
    @Transactional
    public void changeStudentClassroom(Long teacherId, ChangeClassroomDTO dto) {
        if (!isMyStudent(teacherId, dto.getStudentId())) {
            throw new BusinessException("该学生不是您的学生");
        }

        Classroom newClassroom = classroomRepository.findByIdAndTeacherIdAndStatus(dto.getNewClassroomId(), teacherId, "ACTIVE")
                .orElseThrow(() -> new NotFoundException("目标班级", dto.getNewClassroomId()));

        User student = userRepository.findById(dto.getStudentId())
                .orElseThrow(() -> new NotFoundException("学生", dto.getStudentId()));

        // 只移除同一门课程下的其他教学班关系，不能影响学生的其他课程。
        classroomStudentRelationRepository.findByStudentIdAndStatus(student.getId(), "ACTIVE")
                .forEach(relation -> {
                    Classroom oldClassroom = classroomRepository.findById(relation.getClassroomId()).orElse(null);
                    if (oldClassroom != null && java.util.Objects.equals(oldClassroom.getCourseId(), newClassroom.getCourseId())
                            && !java.util.Objects.equals(oldClassroom.getId(), newClassroom.getId())) {
                        relation.setStatus("REMOVED");
                        relation.setRemovedAt(LocalDateTime.now());
                        classroomStudentRelationRepository.save(relation);
                        classroomRepository.updateStudentCount(relation.getClassroomId());
                    }
                });

        // 添加到新班级
        classroomStudentRelationRepository.insertOrReactivate(dto.getNewClassroomId(), student.getId(), student.getUsername());
        classroomRepository.updateStudentCount(dto.getNewClassroomId());

        log.info("学生调班成功: teacherId={}, studentId={}, 新班级={}", teacherId, student.getId(), newClassroom.getClassName());
    }

    @Override
    public ClassroomVO getStudentClassroom(Long teacherId, Long studentId) {
        if (!isMyStudent(teacherId, studentId)) {
            throw new BusinessException("该学生不是您的学生");
        }

        return classroomStudentRelationRepository.findByStudentIdAndStatus(studentId, "ACTIVE")
                .stream()
                .findFirst()
                .map(relation -> {
                    Classroom classroom = classroomRepository.findById(relation.getClassroomId()).orElse(null);
                    if (classroom == null) return null;
                    ClassroomVO vo = new ClassroomVO();
                    BeanUtils.copyProperties(classroom, vo);
                    return vo;
                })
                .orElse(null);
    }

    private java.util.Optional<User> findStudentByNumber(Long requesterId, String studentNumber) {
        String number = studentNumber == null ? "" : studentNumber.trim();
        User requester = userRepository.findById(requesterId).orElse(null);
        if (requester != null && requester.getSchoolId() != null) {
            java.util.Optional<User> scoped = userRepository.findBySchoolIdAndIdentityTypeAndIdentityNumber(
                    requester.getSchoolId(), User.IdentityType.STUDENT, number);
            if (scoped.isPresent()) return scoped;
            scoped = userRepository.findBySchoolIdAndUsername(requester.getSchoolId(), number);
            if (scoped.isPresent()) return scoped;
            return java.util.Optional.empty();
        }
        return userRepository.findByUsername(number);
    }
}
