package com.vtr.service.impl;

import com.vtr.common.PageResult;
import com.vtr.common.exception.BusinessException;
import com.vtr.common.exception.NotFoundException;
import com.vtr.dto.AssignmentCreateDTO;
import com.vtr.dto.AssignmentQueryDTO;
import com.vtr.dto.AssignmentUpdateDTO;
import com.vtr.dto.TestCaseDTO;
import com.vtr.entity.*;
import com.vtr.repository.*;
import com.vtr.service.AssignmentService;
import com.vtr.service.NotificationService;
import com.vtr.vo.AssignmentVO;
import com.vtr.vo.SubmissionVO;
import com.vtr.vo.TestCaseVO;
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
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentServiceImpl implements AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final SubmissionRepository submissionRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomStudentRelationRepository classroomStudentRelationRepository;
    private final AssignmentStudentVisibilityRepository assignmentVisibilityRepository;
    private final LearningQuestionRepository learningQuestionRepository;
    private final CourseRepository courseRepository;
    private final NotificationService notificationService;

    private boolean isAdmin(Long userId) {
        if (userId == null) return false;
        return userRepository.findById(userId)
                .map(user -> user.getRole() == User.UserRole.ADMIN || user.getRole() == User.UserRole.SUPER_ADMIN)
                .orElse(false);
    }

    /**
     * 获取教师的所有学生ID（通过班级关联）
     */
    private List<Long> getTeacherStudentIds(Long teacherId) {
        // 获取教师的所有班级
        List<Classroom> classrooms = classroomRepository.findByTeacherIdAndStatus(teacherId, "ACTIVE");
        if (classrooms.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> classroomIds = classrooms.stream()
                .map(Classroom::getId)
                .collect(Collectors.toList());

        // 获取所有班级的学生
        List<ClassroomStudentRelation> relations = classroomStudentRelationRepository
                .findByClassroomIdInAndStatus(classroomIds, "ACTIVE");

        return relations.stream()
                .map(ClassroomStudentRelation::getStudentId)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 检查学生是否是教师的学生
     */
    private boolean isTeacherStudent(Long teacherId, Long studentId) {
        List<Long> studentIds = getTeacherStudentIds(teacherId);
        return studentIds.contains(studentId);
    }

    /** 获取当前课程下的学生，避免把同一教师其他课程的学生一起通知。 */
    private List<Long> getCourseStudentIds(Long courseId, Long teacherId) {
        if (courseId == null) return getTeacherStudentIds(teacherId);
        if (!courseRepository.findById(courseId)
                .map(course -> "ACTIVE".equals(course.getStatus())).orElse(false)) {
            return new ArrayList<>();
        }

        List<Long> classroomIds = classroomRepository.findByCourseIdOrderByCreatedAtDesc(courseId).stream()
                .filter(classroom -> "ACTIVE".equals(classroom.getStatus()))
                .map(Classroom::getId)
                .collect(Collectors.toList());
        if (classroomIds.isEmpty()) return new ArrayList<>();

        return classroomStudentRelationRepository.findByClassroomIdInAndStatus(classroomIds, "ACTIVE").stream()
                .map(ClassroomStudentRelation::getStudentId)
                .distinct()
                .collect(Collectors.toList());
    }

    // ========== 新增：学生可见作业查询 ==========
    @Override
    public PageResult<AssignmentVO> getStudentVisibleAssignments(Long studentId, AssignmentQueryDTO queryDTO) {
        PageRequest pageRequest = PageRequest.of(queryDTO.getPage() - 1, queryDTO.getSize(),
                Sort.by("deadline").ascending());

        Page<Assignment> page = queryDTO.getCourseId() == null
                ? assignmentRepository.findVisibleAssignmentsForStudent(studentId, pageRequest)
                : assignmentRepository.findVisibleAssignmentsForStudentAndCourse(studentId, queryDTO.getCourseId(), pageRequest);

        List<AssignmentVO> voList = page.getContent().stream()
                .map(a -> convertToVO(a, studentId))
                .collect(Collectors.toList());

        return PageResult.of(voList, page.getTotalElements(), queryDTO.getPage(), queryDTO.getSize());
    }

    // ========== 新增：教师作业列表查询 ==========
    @Override
    public PageResult<AssignmentVO> getTeacherAssignments(Long teacherId, AssignmentQueryDTO queryDTO) {
        PageRequest pageRequest = PageRequest.of(queryDTO.getPage() - 1, queryDTO.getSize(),
                Sort.by("createdAt").descending());

        Page<Assignment> page;
        if (queryDTO.getCourseId() != null) {
            page = assignmentRepository.findByTeacherIdAndCourseId(teacherId, queryDTO.getCourseId(), pageRequest);
        } else if (queryDTO.getStatus() != null && !queryDTO.getStatus().isEmpty()) {
            Assignment.AssignmentStatus status = Assignment.AssignmentStatus.valueOf(queryDTO.getStatus());
            page = assignmentRepository.findByTeacherIdAndStatus(teacherId, status, pageRequest);
        } else {
            page = assignmentRepository.findByTeacherId(teacherId, pageRequest);
        }

        List<AssignmentVO> voList = page.getContent().stream()
                .map(a -> convertToVO(a, teacherId))
                .collect(Collectors.toList());

        return PageResult.of(voList, page.getTotalElements(), queryDTO.getPage(), queryDTO.getSize());
    }

    // ========== 新增：发布作业给指定学生 ==========
    @Override
    @Transactional
    public void publishToStudents(Long assignmentId, List<Long> studentIds, Long teacherId, boolean isAdmin) {
        Assignment assignment = getEntityById(assignmentId);

        if (!isAdmin && !assignment.getTeacher().getId().equals(teacherId)) {
            throw new BusinessException("无权操作此作业");
        }

        assignmentVisibilityRepository.deleteByAssignmentId(assignmentId);

        if (studentIds != null && !studentIds.isEmpty()) {
            for (Long studentId : studentIds) {
                if (!getCourseStudentIds(assignment.getCourseId(), teacherId).contains(studentId)) {
                    log.warn("学生不是该教师的学生，跳过: studentId={}", studentId);
                    continue;
                }
                AssignmentStudentVisibility visibility = AssignmentStudentVisibility.builder()
                        .assignment(assignment)
                        .student(userRepository.getReferenceById(studentId))
                        .build();
                assignmentVisibilityRepository.save(visibility);
            }
            assignment.setPublishType("SELECTED");
            assignment.setStudentCount((int) studentIds.stream()
                    .filter(studentId -> getCourseStudentIds(assignment.getCourseId(), teacherId).contains(studentId))
                    .count());
        } else {
            assignment.setPublishType("ALL");
            assignment.setStudentCount(0);
        }

        assignmentRepository.save(assignment);
        log.info("发布作业给指定学生: assignmentId={}, studentCount={}", assignmentId, assignment.getStudentCount());
        notifyPublishedStudents(assignment);
    }

    // ========== 新增：检查学生是否有权限查看作业 ==========
    @Override
    public boolean canStudentViewAssignment(Long studentId, Long assignmentId) {
        Assignment assignment = getEntityById(assignmentId);

        if (assignment.getStatus() != Assignment.AssignmentStatus.PUBLISHED) {
            return false;
        }

        if ("ALL".equals(assignment.getPublishType()) || assignment.getPublishType() == null) {
            return assignment.getCourseId() == null
                    ? isTeacherStudent(assignment.getTeacher().getId(), studentId)
                    : getCourseStudentIds(assignment.getCourseId(), assignment.getTeacher().getId()).contains(studentId);
        } else {
            return assignmentVisibilityRepository.existsByAssignmentIdAndStudentId(assignmentId, studentId)
                    && (assignment.getCourseId() == null
                    || getCourseStudentIds(assignment.getCourseId(), assignment.getTeacher().getId()).contains(studentId));
        }
    }

    // ========== 新增：获取作业可见学生ID列表 ==========
    @Override
    public List<Long> getAssignmentVisibleStudentIds(Long assignmentId) {
        List<AssignmentStudentVisibility> visibilities = assignmentVisibilityRepository.findByAssignmentId(assignmentId);
        return visibilities.stream()
                .map(v -> v.getStudent().getId())
                .collect(Collectors.toList());
    }

    // ========== 修改：创建作业 ==========
    @Override
    @Transactional
    public Long create(AssignmentCreateDTO dto, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("用户", userId));

        Assignment assignment = new Assignment();
        BeanUtils.copyProperties(dto, assignment);
        assignment.setTeacher(user);
        assignment.setStatus(Assignment.AssignmentStatus.PUBLISHED);

        String publishType = dto.getPublishType();
        if (publishType == null || publishType.isEmpty()) {
            publishType = "ALL";
        }
        assignment.setPublishType(publishType);

        String assignmentType = dto.getAssignmentType();
        if (assignmentType == null || assignmentType.isEmpty()) {
            assignmentType = "PROGRAMMING";
        }
        assignment.setAssignmentType(assignmentType);
        assignment.setQuestionIds(normalizeQuestionIds(dto.getQuestionIds(), dto.getCourseId()));

        boolean isFullManualReview = dto.getManualReviewRatio() != null && dto.getManualReviewRatio() == 100;
        boolean isTextAssignment = "TEXT".equals(assignmentType);

        if (isFullManualReview || isTextAssignment) {
            if (dto.getTestCases() != null && !dto.getTestCases().isEmpty()) {
                for (TestCaseDTO tcDTO : dto.getTestCases()) {
                    TestCase tc = new TestCase();
                    BeanUtils.copyProperties(tcDTO, tc);
                    tc.setIsVirtual(true);
                    assignment.addTestCase(tc);
                }
                log.info("纯人工评审作业保留虚拟测试用例: count={}", dto.getTestCases().size());
            } else {
                assignment.setTestCases(new ArrayList<>());
            }

            assignment.setAutoTestRatio(0);
            assignment.setManualReviewRatio(100);
            assignment.setTimeLimit(0);
            assignment.setMemoryLimit(0);
            assignment.setAllowedLanguages(isTextAssignment ? "TEXT" : (dto.getAllowedLanguages() != null ? String.join(",", dto.getAllowedLanguages()) : ""));
        } else {
            if (dto.getTestCases() == null || dto.getTestCases().isEmpty()) {
                throw new BusinessException("编程作业至少需要添加一个测试用例");
            }

            for (TestCaseDTO tcDTO : dto.getTestCases()) {
                TestCase tc = new TestCase();
                BeanUtils.copyProperties(tcDTO, tc);
                tc.setIsVirtual(false);
                assignment.addTestCase(tc);
            }

            if (dto.getAllowedLanguages() != null && !dto.getAllowedLanguages().isEmpty()) {
                assignment.setAllowedLanguages(String.join(",", dto.getAllowedLanguages()));
            } else {
                assignment.setAllowedLanguages("Java,Python,C++");
            }
            assignment.setAutoTestRatio(dto.getAutoTestRatio() != null ? dto.getAutoTestRatio() : 70);
            assignment.setManualReviewRatio(dto.getManualReviewRatio() != null ? dto.getManualReviewRatio() : 30);
            assignment.setTimeLimit(dto.getTimeLimit() != null ? dto.getTimeLimit() : 1000);
            assignment.setMemoryLimit(dto.getMemoryLimit() != null ? dto.getMemoryLimit() : 128);
        }

        assignmentRepository.save(assignment);

        if ("SELECTED".equals(publishType) && dto.getTargetStudentIds() != null && !dto.getTargetStudentIds().isEmpty()) {
            for (Long studentId : dto.getTargetStudentIds()) {
                if (getCourseStudentIds(dto.getCourseId(), userId).contains(studentId)) {
                    AssignmentStudentVisibility visibility = AssignmentStudentVisibility.builder()
                            .assignment(assignment)
                            .student(userRepository.getReferenceById(studentId))
                            .build();
                    assignmentVisibilityRepository.save(visibility);
                }
            }
            assignment.setStudentCount((int) dto.getTargetStudentIds().stream()
                    .filter(studentId -> getCourseStudentIds(dto.getCourseId(), userId).contains(studentId))
                    .count());
            assignmentRepository.save(assignment);
        }

        notifyPublishedStudents(assignment);

        log.info("教师发布作业: id={}, userId={}, assignmentType={}, publishType={}, testCasesCount={}",
                assignment.getId(), userId, assignmentType, publishType, assignment.getTestCases().size());
        return assignment.getId();
    }

    // ========== 修改：更新作业 ==========
    @Override
    @Transactional
    public void update(Long id, AssignmentUpdateDTO dto, Long userId, boolean isAdmin) {
        Assignment assignment = getEntityById(id);

        if (!isAdmin && !assignment.getTeacher().getId().equals(userId)) {
            throw new BusinessException("无权修改此作业");
        }

        if (assignment.isPublished() && dto.getDeadline() != null && dto.getDeadline().isBefore(LocalDateTime.now())) {
            throw new BusinessException("截止时间不能早于当前时间");
        }

        if (dto.getAssignmentType() != null && !dto.getAssignmentType().equals(assignment.getAssignmentType())) {
            assignment.setAssignmentType(dto.getAssignmentType());
            if ("TEXT".equals(dto.getAssignmentType())) {
                assignment.getTestCases().clear();
                assignment.setAutoTestRatio(0);
                assignment.setManualReviewRatio(100);
                assignment.setAllowedLanguages("TEXT");
                assignment.setTimeLimit(0);
                assignment.setMemoryLimit(0);
            }
        }

        if (dto.getTitle() != null) assignment.setTitle(dto.getTitle());
        if (dto.getDescription() != null) assignment.setDescription(dto.getDescription());
        if (dto.getDeadline() != null) assignment.setDeadline(dto.getDeadline());
        if (dto.getMaxSubmitTimes() != null) assignment.setMaxSubmitTimes(dto.getMaxSubmitTimes());
        if (dto.getTotalScore() != null) assignment.setTotalScore(dto.getTotalScore());
        if (dto.getAutoTestRatio() != null) assignment.setAutoTestRatio(dto.getAutoTestRatio());
        if (dto.getManualReviewRatio() != null) assignment.setManualReviewRatio(dto.getManualReviewRatio());
        if (dto.getQuestionIds() != null) assignment.setQuestionIds(normalizeQuestionIds(dto.getQuestionIds(), assignment.getCourseId()));

        if (dto.getPublishType() != null) {
            assignment.setPublishType(dto.getPublishType());
            if ("SELECTED".equals(dto.getPublishType()) && dto.getTargetStudentIds() != null) {
                assignmentVisibilityRepository.deleteByAssignmentId(id);
                for (Long studentId : dto.getTargetStudentIds()) {
                    if (getCourseStudentIds(assignment.getCourseId(), userId).contains(studentId)) {
                        AssignmentStudentVisibility visibility = AssignmentStudentVisibility.builder()
                                .assignment(assignment)
                                .student(userRepository.getReferenceById(studentId))
                                .build();
                        assignmentVisibilityRepository.save(visibility);
                    }
                }
                assignment.setStudentCount((int) dto.getTargetStudentIds().stream()
                        .filter(studentId -> getCourseStudentIds(assignment.getCourseId(), userId).contains(studentId))
                        .count());
            } else if ("ALL".equals(dto.getPublishType())) {
                assignmentVisibilityRepository.deleteByAssignmentId(id);
                assignment.setStudentCount(0);
            }
        }

        if ("PROGRAMMING".equals(assignment.getAssignmentType()) && dto.getTestCases() != null) {
            assignment.getTestCases().clear();
            for (TestCaseDTO tcDTO : dto.getTestCases()) {
                TestCase tc = new TestCase();
                BeanUtils.copyProperties(tcDTO, tc);
                assignment.addTestCase(tc);
            }
        }

        if (StringUtils.hasText(dto.getStatus())) {
            assignment.setStatus(Assignment.AssignmentStatus.valueOf(dto.getStatus()));
        }

        assignmentRepository.save(assignment);
        log.info("更新作业: id={}, userId={}, isAdmin={}", id, userId, isAdmin);
    }

    // ========== 教师发布旧草稿作业 ==========
    @Override
    @Transactional
    public void publish(Long id, Long userId, boolean isAdmin) {
        Assignment assignment = getEntityById(id);

        if (!isAdmin && !assignment.getTeacher().getId().equals(userId)) {
            throw new BusinessException("无权发布此作业");
        }

        if (assignment.getStatus() == Assignment.AssignmentStatus.PUBLISHED) {
            return;
        }
        if (assignment.getStatus() != Assignment.AssignmentStatus.DRAFT && assignment.getStatus() != Assignment.AssignmentStatus.PENDING) {
            throw new BusinessException("只能发布草稿作业，当前状态：" + assignment.getStatus());
        }

        boolean needTestCases = "PROGRAMMING".equals(assignment.getAssignmentType())
                && assignment.getAutoTestRatio() != null
                && assignment.getAutoTestRatio() > 0;

        if (needTestCases) {
            if (assignment.getTestCases() == null || assignment.getTestCases().isEmpty()) {
                throw new BusinessException("编程作业必须包含至少一个测试用例才能通过审核");
            }

            boolean hasRealTestCase = assignment.getTestCases().stream()
                    .anyMatch(tc -> tc.getIsVirtual() == null || !tc.getIsVirtual());

            boolean hasValidScore = assignment.getTestCases().stream()
                    .anyMatch(tc -> tc.getScore() != null && tc.getScore() > 0);

            if (!hasRealTestCase) {
                log.info("纯人工评审作业通过审核: id={}, 仅包含虚拟测试用例", id);
            } else if (!hasValidScore) {
                throw new BusinessException("编程作业的测试用例分值不能全为0，请设置合理分值");
            }
        }

        assignment.setStatus(Assignment.AssignmentStatus.PUBLISHED);
        assignmentRepository.save(assignment);
        log.info("教师发布作业: id={}, userId={}, assignmentType={}, publishType={}",
                id, userId, assignment.getAssignmentType(), assignment.getPublishType());
        notifyPublishedStudents(assignment);
    }

    @Override
    @Transactional
    public void delete(Long id, Long userId, boolean isAdmin) {
        Assignment assignment = getEntityById(id);

        if (!isAdmin && !assignment.getTeacher().getId().equals(userId)) {
            throw new BusinessException("无权删除此作业");
        }

        assignmentRepository.softDelete(id);
        assignmentVisibilityRepository.deleteByAssignmentId(id);

        log.info("删除作业: id={}, userId={}, isAdmin={}, 原状态={}", id, userId, isAdmin, assignment.getStatus());
    }

    // ========== 获取作业详情 ==========
    @Override
    public AssignmentVO getById(Long id, Long userId) {
        Assignment assignment = getEntityById(id);
        return convertToVO(assignment, userId);
    }

    // ========== 查询作业列表 ==========
    @Override
    public PageResult<AssignmentVO> query(AssignmentQueryDTO query, Long userId) {
        Sort sort = Sort.by("createdAt").descending();
        PageRequest pageRequest = PageRequest.of(query.getPage() - 1, query.getSize(), sort);
        Page<Assignment> page;

        if (query.getCourseId() != null) {
            page = assignmentRepository.findByCourseId(query.getCourseId(), pageRequest);
        } else if (userId == null) {
            page = assignmentRepository.findByStatus(Assignment.AssignmentStatus.PUBLISHED, pageRequest);
        } else {
            User currentUser = userRepository.findById(userId).orElse(null);
            boolean isStudent = currentUser != null && currentUser.getRole() == User.UserRole.STUDENT;
            boolean isAdminUser = currentUser != null && (currentUser.getRole() == User.UserRole.ADMIN || currentUser.getRole() == User.UserRole.SUPER_ADMIN);

            if (isStudent) {
                page = assignmentRepository.findVisibleAssignmentsForStudent(userId, pageRequest);
            } else if (isAdminUser) {
                if (query.getStatus() != null && !query.getStatus().isEmpty()) {
                    Assignment.AssignmentStatus status = Assignment.AssignmentStatus.valueOf(query.getStatus());
                    page = assignmentRepository.findByStatus(status, pageRequest);
                } else {
                    page = assignmentRepository.findByStatus(Assignment.AssignmentStatus.PUBLISHED, pageRequest);
                }
            } else {
                Long teacherId = query.getTeacherId() != null ? query.getTeacherId() : userId;
                if (query.getStatus() != null && !query.getStatus().isEmpty()) {
                    Assignment.AssignmentStatus status = Assignment.AssignmentStatus.valueOf(query.getStatus());
                    page = assignmentRepository.findByTeacherIdAndStatus(teacherId, status, pageRequest);
                } else {
                    page = assignmentRepository.findByTeacherId(teacherId, pageRequest);
                }
            }
        }

        List<AssignmentVO> list = page.getContent().stream()
                .map(a -> convertToVO(a, userId))
                .collect(Collectors.toList());

        return PageResult.of(list, page.getTotalElements(), query.getPage(), query.getSize());
    }

    // ========== 获取教师的所有作业（列表） ==========
    @Override
    public List<AssignmentVO> getTeacherAssignments(Long teacherId) {
        Page<Assignment> page = assignmentRepository.findByTeacherId(teacherId,
                PageRequest.of(0, 100, Sort.by("createdAt").descending()));
        return page.getContent().stream()
                .map(a -> convertToVO(a, teacherId))
                .collect(Collectors.toList());
    }

    // ========== 获取学生的所有作业 ==========
    @Override
    public List<AssignmentVO> getStudentAssignments(Long studentId) {
        PageRequest pageRequest = PageRequest.of(0, 100, Sort.by("deadline").ascending());
        Page<Assignment> page = assignmentRepository.findVisibleAssignmentsForStudent(studentId, pageRequest);
        return page.getContent().stream()
                .map(a -> convertToVO(a, studentId))
                .collect(Collectors.toList());
    }

    // ========== 获取活跃作业 ==========
    @Override
    public List<AssignmentVO> getActiveAssignments() {
        return assignmentRepository.findActiveAssignments(LocalDateTime.now()).stream()
                .map(a -> convertToVO(a, null))
                .collect(Collectors.toList());
    }

    // ========== 获取公开测试用例 ==========
    @Override
    public List<TestCaseVO> getPublicTestCases(Long questionId) {
        Assignment assignment = getEntityById(questionId);
        return assignment.getTestCases().stream()
                .filter(tc -> Boolean.TRUE.equals(tc.getIsPublic()))
                .map(this::convertToTestCaseVO)
                .collect(Collectors.toList());
    }

    // ========== 获取所有测试用例 ==========
    @Override
    public List<TestCaseVO> getAllTestCases(Long id, Long userId, boolean isAdmin) {
        Assignment assignment = getEntityById(id);
        if (isAdmin || assignment.getTeacher().getId().equals(userId)) {
            return assignment.getTestCases().stream()
                    .map(this::convertToTestCaseVO)
                    .collect(Collectors.toList());
        }
        return assignment.getTestCases().stream()
                .filter(tc -> Boolean.TRUE.equals(tc.getIsPublic()))
                .map(tc -> {
                    TestCaseVO tcVO = convertToTestCaseVO(tc);
                    tcVO.setInput(null);
                    tcVO.setExpectedOutput(null);
                    return tcVO;
                })
                .collect(Collectors.toList());
    }

    // ========== 关闭作业 ==========
    @Override
    @Transactional
    public void close(Long id, Long userId, boolean isAdmin) {
        Assignment assignment = getEntityById(id);
        if (!isAdmin && !assignment.getTeacher().getId().equals(userId)) {
            throw new BusinessException("无权关闭此作业");
        }
        if (isAdmin && assignment.getStatus() == Assignment.AssignmentStatus.PENDING) {
            assignment.setStatus(Assignment.AssignmentStatus.CLOSED);
            assignmentRepository.save(assignment);
            log.info("管理员拒绝作业: id={}, userId={}", id, userId);
            return;
        }
        if (assignment.getStatus() == Assignment.AssignmentStatus.PUBLISHED) {
            assignment.setStatus(Assignment.AssignmentStatus.CLOSED);
            assignmentRepository.save(assignment);
            log.info("关闭作业: id={}, userId={}", id, userId);
        } else {
            throw new BusinessException("只能关闭已发布的作业");
        }
    }

    // ========== 重新开放作业 ==========
    @Override
    @Transactional
    public void reopen(Long id, Long userId, boolean isAdmin) {
        Assignment assignment = getEntityById(id);
        if (!isAdmin && !assignment.getTeacher().getId().equals(userId)) {
            throw new BusinessException("无权重新开放此作业");
        }
        if (assignment.getStatus() != Assignment.AssignmentStatus.CLOSED) {
            throw new BusinessException("只有已关闭的作业才能重新开放，当前状态：" + assignment.getStatus());
        }
        if (assignment.isExpired()) {
            assignment.setDeadline(LocalDateTime.now().plusDays(7));
            log.info("作业已过期，自动延长截止时间至: {}", assignment.getDeadline());
        }
        assignment.setStatus(Assignment.AssignmentStatus.PUBLISHED);
        assignmentRepository.save(assignment);
        log.info("重新开放作业: id={}, userId={}, isAdmin={}", id, userId, isAdmin);
    }

    // ========== 获取所有提交记录 ==========
    @Override
    public List<SubmissionVO> getAllSubmissions(Long id, Long userId, boolean isAdmin) {
        Assignment assignment = getEntityById(id);
        if (!isAdmin && !assignment.getTeacher().getId().equals(userId)) {
            throw new BusinessException("只有管理员或作业创建者可以查看所有提交记录");
        }
        List<Submission> submissions = submissionRepository.findByAssignmentIdOrderBySubmittedAtDesc(id);
        return submissions.stream()
                .map(this::convertSubmissionToVO)
                .collect(Collectors.toList());
    }

    // ========== 检查是否可以提交 ==========
    @Override
    public boolean canSubmit(Long assignmentId, Long studentId) {
        Assignment assignment = getEntityById(assignmentId);
        if (!assignment.isPublished()) return false;
        if (assignment.isExpired()) return false;
        long submitCount = submissionRepository.countByAssignmentAndStudent(assignmentId, studentId);
        return submitCount < assignment.getMaxSubmitTimes();
    }

    // ========== 获取剩余提交次数 ==========
    @Override
    public int getRemainingSubmits(Long assignmentId, Long studentId) {
        Assignment assignment = getEntityById(assignmentId);
        long submitCount = submissionRepository.countByAssignmentAndStudent(assignmentId, studentId);
        return Math.max(0, assignment.getMaxSubmitTimes() - (int) submitCount);
    }

    // ========== 获取我的作业（学生） ==========
    @Override
    public List<AssignmentVO> getMyAssignments(Long studentId) {
        return getStudentAssignments(studentId);
    }

    // ========== 获取我教的作业（教师） ==========
    @Override
    public List<AssignmentVO> getTeachingAssignments(Long userId) {
        return getTeacherAssignments(userId);
    }

    // ========== 获取作业实体 ==========
    @Override
    public Assignment getEntityById(Long id) {
        return assignmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("作业", id));
    }

    // ========== 私有转换方法 ==========

    private TestCaseVO convertToTestCaseVO(TestCase tc) {
        if (tc == null) return null;
        TestCaseVO tcVO = new TestCaseVO();
        tcVO.setId(tc.getId());
        tcVO.setDescription(tc.getDescription());
        tcVO.setInput(tc.getInput());
        tcVO.setExpectedOutput(tc.getExpectedOutput());
        tcVO.setIsPublic(tc.getIsPublic());
        tcVO.setScore(tc.getScore());
        tcVO.setTimeLimit(tc.getTimeLimit());
        tcVO.setMemoryLimit(tc.getMemoryLimit());
        tcVO.setIsSample(tc.getIsSample());
        return tcVO;
    }

    private AssignmentVO convertToVO(Assignment assignment, Long userId) {
        AssignmentVO vo = new AssignmentVO();
        BeanUtils.copyProperties(assignment, vo);
        vo.setQuestionIds(parseQuestionIds(assignment.getQuestionIds()));
        vo.setAssignmentType(assignment.getAssignmentType());
        vo.setPublishType(assignment.getPublishType());
        vo.setStudentCount(assignment.getStudentCount());

        if (assignment.getTeacher() != null) {
            UserVO teacherVO = new UserVO();
            BeanUtils.copyProperties(assignment.getTeacher(), teacherVO);
            vo.setTeacher(teacherVO);
            vo.setTeacherName(assignment.getTeacher().getNickname());
            vo.setTeacherId(assignment.getTeacher().getId());
        }

        vo.setStatus(assignment.getStatus().name());
        vo.setIsExpired(assignment.isExpired());
        vo.setAllowedLanguages(assignment.getAllowedLanguageList());

        if (assignment.getCourseId() != null) {
            courseRepository.findById(assignment.getCourseId()).ifPresent(course -> {
                vo.setCourseName(course.getCourseName());
                vo.setCourseStatus(course.getStatus());
            });
        }

        List<TestCaseVO> allTestCases = assignment.getTestCases().stream()
                .map(this::convertToTestCaseVO)
                .collect(Collectors.toList());

        boolean isAdminUser = isAdmin(userId);
        boolean isOwner = userId != null && assignment.getTeacher() != null &&
                assignment.getTeacher().getId().equals(userId);

        if (userId != null && !isOwner && !isAdminUser) {
            List<TestCaseVO> publicTestCases = allTestCases.stream()
                    .filter(tc -> Boolean.TRUE.equals(tc.getIsPublic()) || Boolean.TRUE.equals(tc.getIsSample()))
                    .collect(Collectors.toList());
            vo.setTestCases(publicTestCases);
        } else {
            vo.setTestCases(allTestCases);
        }

        if (userId != null) {
            User currentUser = userRepository.findById(userId).orElse(null);
            if (currentUser != null && currentUser.getRole() == User.UserRole.STUDENT) {
                boolean courseActive = assignment.getCourseId() == null || "ACTIVE".equals(vo.getCourseStatus());
                boolean visible = courseActive && canStudentViewAssignment(userId, assignment.getId());
                vo.setCanView(visible);
                if (!courseActive && assignment.getCourseId() != null) {
                    vo.setAccessMessage("该课程已归档，无法再查看相应作业");
                } else if (!visible) {
                    vo.setAccessMessage("您尚未加入该课程，无法查看此作业");
                }
                if (!visible) {
                    vo.setDescription(null);
                    vo.setQuestionIds(new ArrayList<>());
                    vo.setTestCases(new ArrayList<>());
                }
            }

            long submitCount = submissionRepository.countByAssignmentAndStudent(assignment.getId(), userId);
            vo.setMySubmitCount((int) submitCount);
            vo.setMyRemainingSubmits(assignment.getMaxSubmitTimes() - (int) submitCount);

            if (submitCount > 0) {
                List<Submission> recentSubmissions = submissionRepository.findRecentByAssignmentAndStudent(
                        assignment.getId(), userId, PageRequest.of(0, 1));
                if (!recentSubmissions.isEmpty()) {
                    vo.setMyLastSubmission(convertSubmissionToVO(recentSubmissions.get(0)));
                }
            }
        }

        if ("SELECTED".equals(assignment.getPublishType()) && (isOwner || isAdminUser)) {
            List<Long> visibleStudentIds = getAssignmentVisibleStudentIds(assignment.getId());
            vo.setVisibleStudentIds(visibleStudentIds);
        }

        if (userId != null && (isOwner || isAdminUser)) {
            Integer distinctStudents = submissionRepository.countDistinctStudentsByAssignmentId(assignment.getId());
            vo.setTotalSubmissions(distinctStudents != null ? distinctStudents.longValue() : 0L);
            long totalSubmits = submissionRepository.countByAssignmentAndStudent(assignment.getId(), null);
            vo.setTotalSubmitCount((int) totalSubmits);
            Double avgScore = submissionRepository.calculateAverageScore(assignment.getId());
            vo.setAverageScore(avgScore);
        }

        return vo;
    }

    private String normalizeQuestionIds(List<Long> questionIds, Long courseId) {
        if (questionIds == null || questionIds.isEmpty()) return null;
        List<Long> ids = questionIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) return null;
        List<LearningQuestion> questions = learningQuestionRepository.findAllById(ids);
        if (questions.size() != ids.size() || questions.stream().anyMatch(question -> !Objects.equals(courseId, question.getCourseId()))) {
            throw new BusinessException("只能选择当前课程题库中的题目");
        }
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private List<Long> parseQuestionIds(String value) {
        if (!StringUtils.hasText(value)) return new ArrayList<>();
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(Long::valueOf)
                .collect(Collectors.toList());
    }

    private void notifyPublishedStudents(Assignment assignment) {
        try {
            List<Long> studentIds = "SELECTED".equals(assignment.getPublishType())
                    ? getAssignmentVisibleStudentIds(assignment.getId())
                    : getCourseStudentIds(assignment.getCourseId(), assignment.getTeacher().getId());
            if (assignment.getCourseId() != null) {
                studentIds.retainAll(getCourseStudentIds(assignment.getCourseId(), assignment.getTeacher().getId()));
            }
            for (Long studentId : studentIds) {
                notificationService.sendAssignmentPublishedNotification(studentId, assignment.getTitle(), assignment.getId(), assignment.getDeadline());
            }
            log.info("发布作业通知已发送: assignmentId={}, studentCount={}", assignment.getId(), studentIds.size());
        } catch (Exception e) {
            log.error("发送作业发布通知失败: {}", e.getMessage());
        }
    }

    private SubmissionVO convertSubmissionToVO(Submission submission) {
        if (submission == null) return null;
        SubmissionVO vo = new SubmissionVO();
        vo.setId(submission.getId());
        vo.setCode(submission.getCode());
        vo.setLanguage(submission.getLanguage());
        if (submission.getStatus() != null) {
            vo.setStatus(submission.getStatus().name());
            vo.setStatusDescription(submission.getStatus().getDescription());
        }
        if (submission.getAssignment() != null) {
            vo.setAssignmentId(submission.getAssignment().getId());
            vo.setAssignmentTitle(submission.getAssignment().getTitle());
        }
        if (submission.getStudent() != null) {
            UserVO studentVO = new UserVO();
            BeanUtils.copyProperties(submission.getStudent(), studentVO);
            vo.setStudent(studentVO);
            vo.setStudentName(submission.getStudent().getNickname());
            vo.setStudentId(submission.getStudent().getId());
        }
        LocalDateTime submitTime = submission.getSubmittedAt() != null ? submission.getSubmittedAt() : submission.getCreatedAt();
        vo.setSubmittedAt(submitTime);
        vo.setSubmitTime(submitTime);
        if (submission.getTotalScore() != null) {
            vo.setScore(submission.getTotalScore());
            vo.setTotalScore(submission.getTotalScore());
        } else if (submission.getAutoTestScore() != null) {
            vo.setScore(submission.getAutoTestScore());
        }
        vo.setAutoTestScore(submission.getAutoTestScore());
        vo.setManualReviewScore(submission.getManualReviewScore());
        if (submission.getReviewer() != null) {
            UserVO reviewerVO = new UserVO();
            BeanUtils.copyProperties(submission.getReviewer(), reviewerVO);
            vo.setReviewer(reviewerVO);
        }
        vo.setManualReview(submission.getManualReview());
        vo.setFeedback(submission.getManualReview());
        vo.setResult(submission.getAutoTestResult());
        vo.setCompileError(submission.getCompileError());
        vo.setExecutionTime(submission.getExecutionTime());
        vo.setMemoryUsed(submission.getMemoryUsed());
        vo.setPassedTestCount(submission.getPassedTestCount());
        vo.setTotalTestCount(submission.getTotalTestCount());
        vo.setIsFinal(submission.getIsFinal());
        vo.setPlagiarismScore(submission.getPlagiarismScore());
        vo.setSubmitCount(submission.getSubmitCount());
        return vo;
    }
}
