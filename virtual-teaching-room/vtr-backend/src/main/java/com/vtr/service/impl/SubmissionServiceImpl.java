package com.vtr.service.impl;

import com.alibaba.fastjson2.JSON;
import com.vtr.common.PageResult;
import com.vtr.common.exception.BusinessException;
import com.vtr.common.exception.NotFoundException;
import com.vtr.dto.CodeExecutionResult;
import com.vtr.dto.ManualReviewDTO;
import com.vtr.dto.SubmissionDTO;
import com.vtr.dto.SubmissionQueryDTO;
import com.vtr.dto.TestCaseDTO;
import com.vtr.entity.Assignment;
import com.vtr.entity.LearningQuestion;
import com.vtr.entity.Submission;
import com.vtr.entity.User;
import com.vtr.repository.AssignmentRepository;
import com.vtr.repository.LearningQuestionRepository;
import com.vtr.repository.SubmissionRepository;
import com.vtr.repository.UserRepository;
import com.vtr.service.AssignmentService;
import com.vtr.service.CodeExecutionService;
import com.vtr.service.NotificationService;
import com.vtr.service.SubmissionService;
import com.vtr.vo.SubmissionProgressVO;
import com.vtr.vo.SubmissionVO;
import com.vtr.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionServiceImpl implements SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final AssignmentRepository assignmentRepository;
    private final LearningQuestionRepository learningQuestionRepository;
    private final UserRepository userRepository;
    private final AssignmentService assignmentService;
    private final CodeExecutionService codeExecutionService;
    private final NotificationService notificationService;  // 新增

    @Override
    @Transactional
    public Long submit(SubmissionDTO dto, Long studentId) {
        Assignment assignment = assignmentRepository.findById(dto.getAssignmentId())
                .orElseThrow(() -> new NotFoundException("作业", dto.getAssignmentId()));

        if (assignment.getStatus() != Assignment.AssignmentStatus.PUBLISHED) {
            throw new BusinessException("作业未发布，无法提交");
        }

        if (assignment.isExpired()) {
            throw new BusinessException("作业已截止，无法提交");
        }

        long submitCount = submissionRepository.countByAssignmentIdAndStudentId(dto.getAssignmentId(), studentId);
        if (submitCount >= assignment.getMaxSubmitTimes()) {
            throw new BusinessException("已达到最大提交次数：" + assignment.getMaxSubmitTimes());
        }

        Submission submission = new Submission();
        submission.setAssignment(assignment);

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new NotFoundException("学生", studentId));
        submission.setStudent(student);

        // 根据作业类型处理提交内容
        if (assignment.isTextAssignment()) {
            // 文本作业：内容存储在 code 字段，语言标记为 TEXT
            submission.setCode(dto.getContent() != null ? dto.getContent() : dto.getCode());
            submission.setLanguage("TEXT");
        } else {
            // 编程作业：需要语言验证
            if (!assignment.isLanguageAllowed(dto.getLanguage())) {
                throw new BusinessException("不支持该编程语言：" + dto.getLanguage());
            }
            submission.setCode(dto.getCode());
            submission.setLanguage(dto.getLanguage());
        }

        submission.setSubmittedAt(LocalDateTime.now());
        submission.setStatus(Submission.SubmissionStatus.PENDING);
        submission.setSubmitCount((int) submitCount + 1);

        if (submitCount > 0) {
            List<Submission> previousSubmissions = submissionRepository.findByAssignmentIdAndStudentId(
                    dto.getAssignmentId(), studentId);
            for (Submission prev : previousSubmissions) {
                if (Boolean.TRUE.equals(prev.getIsFinal())) {
                    prev.setIsFinal(false);
                    submissionRepository.save(prev);
                }
            }
        }

        submission.setIsFinal(true);

        Submission saved = submissionRepository.save(submission);
        log.info("学生提交作业: assignmentId={}, studentId={}, assignmentType={}, submissionId={}",
                dto.getAssignmentId(), studentId, assignment.getAssignmentType(), saved.getId());

        try {
            notificationService.sendAssignmentSubmissionNotification(
                    assignment.getTeacher().getId(),
                    student.getNickname() != null ? student.getNickname() : student.getUsername(),
                    assignment.getTitle(), assignment.getId());
        } catch (Exception e) {
            log.error("发送作业提交通知失败: submissionId={}, message={}", saved.getId(), e.getMessage());
        }

        // 执行自动测试（编程作业才会真正测试，文本作业会跳过）
        executeAutoTest(saved.getId());

        return saved.getId();
    }

    @Override
    public SubmissionVO getById(Long id, Long userId) {
        Submission submission = submissionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("提交记录", id));

        User currentUser = userRepository.findById(userId).orElse(null);
        if (currentUser != null && currentUser.isStudent() && !submission.getStudent().getId().equals(userId)) {
            throw new BusinessException("无权查看他人的提交记录");
        }

        return convertToVO(submission);
    }

    @Override
    public PageResult<SubmissionVO> querySubmissions(SubmissionQueryDTO query, Long userId) {
        return query(query, userId);
    }

    @Override
    public List<SubmissionVO> getMySubmissions(Long assignmentId, Long studentId) {
        List<Submission> submissions = submissionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId);
        return submissions.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public SubmissionVO getMyLastSubmission(Long assignmentId, Long studentId) {
        List<Submission> submissions = submissionRepository.findRecentByAssignmentAndStudent(
                assignmentId, studentId, PageRequest.of(0, 1));
        if (submissions.isEmpty()) {
            return null;
        }
        return convertToVO(submissions.get(0));
    }

    @Override
    public PageResult<SubmissionVO> query(SubmissionQueryDTO queryDTO, Long userId) {
        Pageable pageable = PageRequest.of(
                queryDTO.getPage() - 1,
                queryDTO.getSize(),
                Sort.by("submittedAt").descending()
        );

        Page<Submission> page;
        User currentUser = userRepository.findById(userId).orElse(null);

        if (currentUser != null && currentUser.isAdmin()) {
            if (queryDTO.getAssignmentId() != null) {
                page = submissionRepository.findByAssignmentId(queryDTO.getAssignmentId(), pageable);
            } else {
                page = submissionRepository.findAll(pageable);
            }
        } else if (currentUser != null && currentUser.isTeacher()) {
            if (queryDTO.getAssignmentId() != null) {
                page = submissionRepository.findByAssignmentId(queryDTO.getAssignmentId(), pageable);
            } else {
                page = submissionRepository.findByAssignmentTeacherId(userId, pageable);
            }
        } else {
            page = submissionRepository.findByStudentId(userId, pageable);
        }

        List<SubmissionVO> list = page.getContent().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return PageResult.of(list, page.getTotalElements(), queryDTO.getPage(), queryDTO.getSize());
    }

    @Override
    @Transactional
    public void manualReview(Long id, ManualReviewDTO dto, Long reviewerId) {
        Submission submission = submissionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("提交记录", id));

        Assignment assignment = submission.getAssignment();
        User reviewer = userRepository.findById(reviewerId)
                .orElseThrow(() -> new NotFoundException("评审人", reviewerId));

        if (!reviewer.isAdmin() && !assignment.getTeacher().getId().equals(reviewerId)) {
            throw new BusinessException("无权评审此提交");
        }

        submission.setManualReview(dto.getComment());
        submission.setManualReviewScore(dto.getScore());
        submission.setReviewer(reviewer);

        Integer autoScore = submission.getAutoTestScore() != null ? submission.getAutoTestScore() : 0;
        Integer manualScore = dto.getScore() != null ? dto.getScore() : 0;

        int totalScore;

        // 文本作业：客观题得分已经在提交时保存，人工分数只补充简答题分数。
        if (assignment.isTextAssignment()) {
            totalScore = autoScore + manualScore;
            if (totalScore > (assignment.getTotalScore() == null ? 100 : assignment.getTotalScore())) {
                totalScore = assignment.getTotalScore() == null ? 100 : assignment.getTotalScore();
            }
        } else {
            // 编程作业：自动分数 + 人工分数
            totalScore = autoScore + manualScore;
            if (totalScore > 100) {
                totalScore = 100;
            }
        }

        submission.setTotalScore(totalScore);
        submission.setStatus(Submission.SubmissionStatus.GRADED);
        submissionRepository.save(submission);

        log.info("人工评审完成: submissionId={}, reviewerId={}, assignmentType={}, autoScore={}, manualScore={}, totalScore={}",
                id, reviewerId, assignment.getAssignmentType(), autoScore, manualScore, totalScore);

        // 发送评分通知给学生
        try {
            String assignmentTitle = assignment.getTitle();
            notificationService.sendGradeNotification(
                    submission.getStudent().getId(),
                    assignmentTitle,
                    totalScore,
                    id
            );
            log.info("发送评分通知成功: studentId={}, submissionId={}, score={}",
                    submission.getStudent().getId(), id, totalScore);
        } catch (Exception e) {
            log.error("发送评分通知失败: {}", e.getMessage());
        }
    }

    @Override
    @Transactional
    public void batchReview(List<Long> ids, Integer score, String comment, Long reviewerId) {
        for (Long id : ids) {
            ManualReviewDTO dto = new ManualReviewDTO();
            dto.setScore(score);
            dto.setComment(comment);
            manualReview(id, dto, reviewerId);
        }
    }

    @Override
    public void calculatePlagiarism(Long assignmentId) {
        log.info("计算查重: assignmentId={}", assignmentId);
    }

    private void autoGradeTextSubmission(Submission submission) {
        Assignment assignment = submission.getAssignment();
        List<Long> questionIds = parseQuestionIds(assignment.getQuestionIds());
        int objectiveCount = 0;
        int correctCount = 0;
        int subjectiveCount = 0;
        String content = submission.getCode() == null ? "" : submission.getCode();

        for (int index = 0; index < questionIds.size(); index++) {
            LearningQuestion question = learningQuestionRepository.findById(questionIds.get(index)).orElse(null);
            if (question == null || !isObjective(question.getQuestionType())) {
                subjectiveCount++;
                continue;
            }
            objectiveCount++;
            if (answersEqual(extractAnswer(content, index + 1), question.getReferenceAnswer(), question.getQuestionType())) {
                correctCount++;
            }
        }

        int fullScore = assignment.getTotalScore() == null ? 100 : assignment.getTotalScore();
        int autoScore = questionIds.isEmpty() || objectiveCount == 0
                ? 0
                : Math.round(correctCount * fullScore / (float) questionIds.size());
        submission.setAutoTestScore(autoScore);
        submission.setTotalScore(autoScore);
        submission.setPassedTestCount(correctCount);
        submission.setTotalTestCount(objectiveCount);
        submission.setStatus(subjectiveCount == 0
                ? Submission.SubmissionStatus.GRADED
                : Submission.SubmissionStatus.PENDING);
    }

    private List<Long> parseQuestionIds(String raw) {
        if (raw == null || raw.trim().isEmpty()) return List.of();
        List<Long> ids = new ArrayList<>();
        for (String value : raw.split(",")) {
            try { ids.add(Long.valueOf(value.trim())); } catch (NumberFormatException ignored) { }
        }
        return ids;
    }

    private String extractAnswer(String content, int questionNumber) {
        Pattern pattern = Pattern.compile("第\\s*" + questionNumber + "\\s*题\\s*[：:]\\s*(.*)");
        for (String line : content.split("\\R")) {
            Matcher matcher = pattern.matcher(line.trim());
            if (matcher.matches()) return matcher.group(1).trim();
        }
        return "";
    }

    private boolean isObjective(String type) {
        return java.util.Set.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "JUDGMENT", "FILL").contains(type);
    }

    private boolean answersEqual(String actual, String expected, String type) {
        if (actual == null || expected == null) return false;
        if ("MULTIPLE_CHOICE".equals(type)) return choiceSet(actual).equals(choiceSet(expected));
        return normalizeAnswer(actual, type).equals(normalizeAnswer(expected, type));
    }

    private java.util.Set<String> choiceSet(String value) {
        java.util.Set<String> result = new java.util.HashSet<>();
        for (String token : value.split("[、,，\\s]+")) {
            String normalized = normalizeAnswer(token, "SINGLE_CHOICE");
            if (normalized.matches("[A-Z]{2,}")) {
                for (char letter : normalized.toCharArray()) result.add(String.valueOf(letter));
            } else if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return result;
    }

    private String normalizeAnswer(String value, String type) {
        String text = value.trim().toUpperCase(Locale.ROOT);
        if ("SINGLE_CHOICE".equals(type) || "MULTIPLE_CHOICE".equals(type)) {
            Matcher matcher = Pattern.compile("^([A-Z])[\\.、)）:：\\s].*").matcher(text);
            return matcher.matches() ? matcher.group(1) : text;
        }
        if ("JUDGMENT".equals(type)) return text.replace("正确", "对").replace("错误", "错");
        return text.replaceAll("\\s+", "").replaceAll("[，。；;、]", "");
    }

    @Override
    @Transactional
    public void executeAutoTest(Long submissionId) {
        log.info("执行自动测试: submissionId={}", submissionId);

        submissionRepository.findById(submissionId).ifPresent(submission -> {
            Assignment assignment = submission.getAssignment();

            // 文本作业也要先自动判客观题，简答题再由教师人工批改。
            if (assignment.isTextAssignment()) {
                autoGradeTextSubmission(submission);
                log.info("文本作业客观题自动评分完成: assignmentId={}, autoScore={}",
                        assignment.getId(), assignment.getAssignmentType());
                submissionRepository.save(submission);
                return;
            }

            List<com.vtr.entity.TestCase> testCases = assignment.getTestCases();

            if (testCases == null || testCases.isEmpty()) {
                log.warn("编程作业没有测试用例，跳过自动测试: assignmentId={}", assignment.getId());
                submission.setAutoTestScore(0);
                submission.setPassedTestCount(0);
                submission.setTotalTestCount(0);
                submission.setStatus(Submission.SubmissionStatus.PENDING);
                submissionRepository.save(submission);
                return;
            }

            try {
                // 将 TestCase 实体转换为 TestCaseDTO
                List<TestCaseDTO> testCaseDTOs = testCases.stream().map(tc -> {
                    TestCaseDTO dto = new TestCaseDTO();
                    dto.setDescription(tc.getDescription());
                    dto.setInput(tc.getInput());
                    dto.setExpectedOutput(tc.getExpectedOutput());
                    dto.setScore(tc.getScore());
                    dto.setIsPublic(tc.getIsPublic());
                    dto.setIsSample(tc.getIsSample());
                    return dto;
                }).collect(Collectors.toList());

                // 调用代码执行服务进行真实测试
                CodeExecutionResult result = codeExecutionService.executeCode(
                        submission.getCode(),
                        submission.getLanguage(),
                        testCaseDTOs
                );

                if (result.isSuccess()) {
                    submission.setAutoTestScore(result.getTotalScore());
                    submission.setPassedTestCount(result.getPassedCount());
                    submission.setTotalTestCount(result.getTotalCount());
                    submission.setAutoTestResult(JSON.toJSONString(result.getResults()));
                    submission.setStatus(Submission.SubmissionStatus.PASSED);

                    if (submission.getManualReviewScore() != null) {
                        int totalScore = result.getTotalScore() + submission.getManualReviewScore();
                        submission.setTotalScore(totalScore);
                        submission.setStatus(Submission.SubmissionStatus.GRADED);
                    }
                } else {
                    submission.setCompileError(result.getError());
                    submission.setStatus(Submission.SubmissionStatus.COMPILE_ERROR);
                }

                submissionRepository.save(submission);
                log.info("自动测试完成: submissionId={}, score={}, passed={}/{}",
                        submissionId, result.getTotalScore(), result.getPassedCount(), result.getTotalCount());

            } catch (Exception e) {
                log.error("自动测试执行异常: submissionId={}", submissionId, e);
                submission.setCompileError("测试执行异常: " + e.getMessage());
                submission.setStatus(Submission.SubmissionStatus.COMPILE_ERROR);
                submissionRepository.save(submission);
            }
        });
    }

    @Override
    public SubmissionProgressVO getProgress(Long submissionId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("提交记录", submissionId));

        SubmissionProgressVO progress = new SubmissionProgressVO();
        progress.setSubmissionId(submissionId);

        if (submission.getStatus() == Submission.SubmissionStatus.PENDING) {
            progress.setStatus("PENDING");
            progress.setMessage("等待评测中...");
            progress.setProgress(10);
        } else if (submission.getStatus() == Submission.SubmissionStatus.EVALUATING) {
            progress.setStatus("EVALUATING");
            progress.setMessage("正在评测代码...");
            progress.setProgress(50);
        } else if (submission.getStatus() == Submission.SubmissionStatus.GRADED) {
            progress.setStatus("GRADED");
            progress.setMessage("评测完成");
            progress.setProgress(100);
            progress.setScore(submission.getTotalScore());
        } else if (submission.getStatus() == Submission.SubmissionStatus.FAILED) {
            progress.setStatus("FAILED");
            progress.setMessage(submission.getCompileError());
            progress.setProgress(100);
        } else if (submission.getStatus() == Submission.SubmissionStatus.COMPILE_ERROR) {
            progress.setStatus("COMPILE_ERROR");
            progress.setMessage(submission.getCompileError());
            progress.setProgress(100);
        }

        return progress;
    }

    @Override
    @Transactional
    public void markAsFinal(Long submissionId, Long studentId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("提交记录", submissionId));

        if (!submission.getStudent().getId().equals(studentId)) {
            throw new BusinessException("只能标记自己的提交为最终版本");
        }

        List<Submission> studentSubmissions = submissionRepository.findByAssignmentIdAndStudentId(
                submission.getAssignment().getId(), studentId);
        for (Submission sub : studentSubmissions) {
            sub.setIsFinal(false);
            submissionRepository.save(sub);
        }

        submission.setIsFinal(true);
        submissionRepository.save(submission);
        log.info("标记最终提交: submissionId={}, studentId={}", submissionId, studentId);
    }

    @Override
    public SubmissionVO getFinalSubmission(Long assignmentId, Long studentId) {
        List<Submission> submissions = submissionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId);
        return submissions.stream()
                .filter(sub -> Boolean.TRUE.equals(sub.getIsFinal()))
                .findFirst()
                .map(this::convertToVO)
                .orElse(null);
    }

    @Override
    public PageResult<SubmissionVO> getAllSubmissionsByAssignment(Long assignmentId, SubmissionQueryDTO queryDTO, Long userId) {
        Assignment assignment = assignmentService.getEntityById(assignmentId);
        User currentUser = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("用户", userId));

        if (!currentUser.isAdmin() && !assignment.getTeacher().getId().equals(userId)) {
            throw new BusinessException("您没有权限查看此作业的所有提交记录");
        }

        PageRequest pageRequest = PageRequest.of(
                queryDTO.getPage() - 1,
                queryDTO.getSize(),
                Sort.by("submittedAt").descending()
        );

        Page<Submission> page = submissionRepository.findByAssignmentId(assignmentId, pageRequest);

        List<SubmissionVO> list = page.getContent().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return PageResult.of(list, page.getTotalElements(), queryDTO.getPage(), queryDTO.getSize());
    }

    private SubmissionVO convertToVO(Submission submission) {
        SubmissionVO vo = new SubmissionVO();
        BeanUtils.copyProperties(submission, vo);

        vo.setId(submission.getId());
        vo.setCode(submission.getCode());
        vo.setLanguage(submission.getLanguage());
        vo.setSubmitTime(submission.getSubmittedAt());
        vo.setSubmittedAt(submission.getSubmittedAt());

        if (submission.getStudent() != null) {
            UserVO studentVO = new UserVO();
            BeanUtils.copyProperties(submission.getStudent(), studentVO);
            vo.setStudent(studentVO);
            vo.setStudentName(submission.getStudent().getNickname());
            vo.setStudentId(submission.getStudent().getId());
        }

        if (submission.getAssignment() != null) {
            vo.setAssignmentId(submission.getAssignment().getId());
            vo.setAssignmentTitle(submission.getAssignment().getTitle());
        }

        if (submission.getReviewer() != null) {
            UserVO reviewerVO = new UserVO();
            BeanUtils.copyProperties(submission.getReviewer(), reviewerVO);
            vo.setReviewer(reviewerVO);
        }

        vo.setAutoTestScore(submission.getAutoTestScore());
        vo.setManualReviewScore(submission.getManualReviewScore());

        if (submission.getTotalScore() != null) {
            vo.setScore(submission.getTotalScore());
            vo.setTotalScore(submission.getTotalScore());
        } else if (submission.getAutoTestScore() != null) {
            vo.setScore(submission.getAutoTestScore());
        }

        vo.setManualReview(submission.getManualReview());
        vo.setFeedback(submission.getManualReview());
        vo.setResult(submission.getAutoTestResult());
        vo.setCompileError(submission.getCompileError());
        vo.setExecutionTime(submission.getExecutionTime());
        vo.setMemoryUsed(submission.getMemoryUsed());
        vo.setPassedTestCount(submission.getPassedTestCount());
        vo.setTotalTestCount(submission.getTotalTestCount());

        if (submission.getStatus() != null) {
            vo.setStatus(submission.getStatus().name());
            vo.setStatusDescription(submission.getStatus().getDescription());
        }

        vo.setIsFinal(submission.getIsFinal());
        vo.setPlagiarismScore(submission.getPlagiarismScore());
        vo.setSubmitCount(submission.getSubmitCount());

        return vo;
    }
}
