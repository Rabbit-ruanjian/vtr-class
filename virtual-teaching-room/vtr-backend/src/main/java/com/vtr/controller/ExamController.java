package com.vtr.controller;

import com.vtr.common.Result;
import com.vtr.common.exception.BusinessException;
import com.vtr.common.exception.NotFoundException;
import com.vtr.entity.*;
import com.vtr.repository.*;
import com.vtr.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/exams")
@RequiredArgsConstructor
public class ExamController {
    private final ChapterQuizRepository examRepository;
    private final ChapterQuizQuestionRepository examQuestionRepository;
    private final QuizAttemptRepository attemptRepository;
    private final LearningQuestionRepository questionRepository;
    private final CourseRepository courseRepository;
    private final CourseMemberRepository courseMemberRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomStudentRelationRepository classroomStudentRelationRepository;
    private final NotificationService notificationService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Result<List<Map<String, Object>>> list(@RequestParam Long courseId) {
        Long userId = currentUserId();
        if (isStudent()) {
            requireStudentCourseAccess(courseId, userId);
            return Result.success(examRepository.findByCourseIdAndAssessmentTypeOrderByCreatedAtDesc(courseId, "EXAM")
                    .stream().filter(exam -> "PUBLISHED".equals(exam.getStatus()))
                    .map(exam -> examMap(exam, latestCompletedAttempt(exam.getId(), userId))).collect(Collectors.toList()));
        }
        requireCourseBuilder(courseId, userId);
        return Result.success(examRepository.findByCourseIdAndAssessmentTypeOrderByCreatedAtDesc(courseId, "EXAM")
                .stream().map(exam -> examMap(exam, null)).collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        ChapterQuiz exam = requireExam(id);
        if (isStudent()) {
            requireStudentCourseAccess(exam.getCourseId(), currentUserId());
            if (!"PUBLISHED".equals(exam.getStatus())) throw new NotFoundException("考试", id);
            return Result.success(detailMap(exam, false));
        }
        requireCourseBuilder(exam.getCourseId(), currentUserId());
        return Result.success(detailMap(exam, true));
    }

    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    @Transactional
    public Result<Long> create(@RequestBody Map<String, Object> body) {
        Long courseId = longValue(body.get("courseId"));
        requireCourseBuilder(courseId, currentUserId());
        ChapterQuiz exam = fromBody(new ChapterQuiz(), body, courseId);
        exam.setAssessmentType("EXAM");
        exam.setCreatedBy(currentUserId());
        exam.setStatus("DRAFT");
        validate(exam);
        ChapterQuiz saved = examRepository.save(exam);
        replaceQuestions(saved, body.get("questions"));
        return Result.success(saved.getId());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    @Transactional
    public Result<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        ChapterQuiz exam = requireExam(id);
        requireCourseBuilder(exam.getCourseId(), currentUserId());
        if (!"DRAFT".equals(exam.getStatus())) throw new BusinessException("已发布考试请先归档后再编辑");
        fromBody(exam, body, exam.getCourseId());
        validate(exam);
        examRepository.save(exam);
        replaceQuestions(exam, body.get("questions"));
        return Result.success();
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('TEACHER')")
    @Transactional
    public Result<Void> publish(@PathVariable Long id) {
        ChapterQuiz exam = requireExam(id);
        requireCourseBuilder(exam.getCourseId(), currentUserId());
        List<ChapterQuizQuestion> links = examQuestionRepository.findByQuizIdOrderBySortOrderAsc(id);
        if (links.isEmpty()) throw new BusinessException("请先从题库选择至少一道题目");
        for (ChapterQuizQuestion link : links) {
            LearningQuestion question = requireQuestion(link.getQuestionId());
            if (!"PUBLISHED".equals(question.getStatus())) throw new BusinessException("考试中包含未审核通过的题目：" + question.getTitle());
        }
        exam.setStatus("PUBLISHED");
        examRepository.save(exam);
        notifyStudents(exam);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    @Transactional
    public Result<Void> delete(@PathVariable Long id) {
        ChapterQuiz exam = requireExam(id);
        requireCourseBuilder(exam.getCourseId(), currentUserId());
        if (!"DRAFT".equals(exam.getStatus())) throw new BusinessException("只能删除考试草稿，已发布考试请先归档");
        if (attemptRepository.existsByQuizId(id)) throw new BusinessException("已有作答记录，不能删除该考试");
        examQuestionRepository.deleteByQuizId(id);
        examRepository.delete(exam);
        return Result.success();
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('TEACHER')")
    public Result<Void> archive(@PathVariable Long id) {
        ChapterQuiz exam = requireExam(id);
        requireCourseBuilder(exam.getCourseId(), currentUserId());
        exam.setStatus("ARCHIVED");
        examRepository.save(exam);
        return Result.success();
    }

    private ChapterQuiz fromBody(ChapterQuiz exam, Map<String, Object> body, Long courseId) {
        exam.setCourseId(courseId);
        exam.setChapter(null);
        exam.setSectionId(null);
        exam.setTitle(text(body.get("title")));
        exam.setDescription(text(body.get("description")));
        exam.setTotalScore(intValue(body.get("totalScore"), 100));
        exam.setDurationMinutes(intValue(body.get("durationMinutes"), 60));
        exam.setAttemptLimit(intValue(body.get("attemptLimit"), 1));
        exam.setStartAt(dateTime(body.get("startAt")));
        exam.setEndAt(dateTime(body.get("endAt")));
        exam.setShuffleQuestions(boolValue(body.get("shuffleQuestions"), false));
        exam.setShuffleOptions(boolValue(body.get("shuffleOptions"), false));
        exam.setShowAnswerAfterSubmit(boolValue(body.get("showAnswerAfterSubmit"), false));
        exam.setAllowReviewAfterSubmit(boolValue(body.get("allowReviewAfterSubmit"), false));
        exam.setAssessmentType("EXAM");
        return exam;
    }

    private void replaceQuestions(ChapterQuiz exam, Object rawQuestions) {
        examQuestionRepository.deleteAll(examQuestionRepository.findByQuizIdOrderBySortOrderAsc(exam.getId()));
        if (!(rawQuestions instanceof Collection)) return;
        List<ChapterQuizQuestion> links = new ArrayList<>();
        for (Object raw : (Collection<?>) rawQuestions) {
            Long questionId;
            int score = 10;
            if (raw instanceof Map) {
                Map<?, ?> item = (Map<?, ?>) raw;
                questionId = longValue(item.get("questionId"));
                score = intValue(item.get("score"), 10);
            } else questionId = longValue(raw);
            if (questionId == null || score <= 0) continue;
            LearningQuestion question = requireQuestion(questionId);
            if (!exam.getCourseId().equals(question.getCourseId())) throw new BusinessException("组卷题目必须属于当前课程题库");
            links.add(ChapterQuizQuestion.builder().quizId(exam.getId()).questionId(questionId)
                    .sortOrder(links.size() + 1).score(score).build());
        }
        if (links.isEmpty()) return;
        if (exam.getTotalScore() < links.size()) throw new BusinessException("考试总分不能小于题目数量");
        int totalScore = exam.getTotalScore();
        int weightTotal = links.stream().mapToInt(ChapterQuizQuestion::getScore).sum();
        int assigned = 0;
        List<Double> remainders = new ArrayList<>();
        for (ChapterQuizQuestion link : links) {
            double exact = weightTotal == 0 ? 0 : (double) (totalScore - links.size()) * link.getScore() / weightTotal;
            int normalizedScore = 1 + (int) Math.floor(exact);
            link.setScore(normalizedScore);
            assigned += normalizedScore;
            remainders.add(exact - Math.floor(exact));
        }
        for (int i = 0; i < totalScore - assigned; i++) {
            int best = 0;
            for (int j = 1; j < remainders.size(); j++) if (remainders.get(j) > remainders.get(best)) best = j;
            links.get(best).setScore(links.get(best).getScore() + 1);
            remainders.set(best, -1d);
        }
        examQuestionRepository.saveAll(links);
    }

    private Map<String, Object> detailMap(ChapterQuiz exam, boolean teacher) {
        Map<String, Object> result = examMap(exam, null);
        result.put("questions", examQuestionRepository.findByQuizIdOrderBySortOrderAsc(exam.getId()).stream().map(link -> {
            LearningQuestion question = requireQuestion(link.getQuestionId());
            Map<String, Object> item = questionMap(question, teacher);
            item.put("score", link.getScore());
            item.put("sortOrder", link.getSortOrder());
            return item;
        }).collect(Collectors.toList()));
        return result;
    }

    private Map<String, Object> examMap(ChapterQuiz exam, QuizAttempt completed) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", exam.getId()); result.put("courseId", exam.getCourseId()); result.put("title", exam.getTitle());
        result.put("description", exam.getDescription()); result.put("totalScore", exam.getTotalScore());
        result.put("durationMinutes", exam.getDurationMinutes()); result.put("attemptLimit", exam.getAttemptLimit());
        result.put("startAt", exam.getStartAt()); result.put("endAt", exam.getEndAt());
        result.put("shuffleQuestions", exam.getShuffleQuestions()); result.put("shuffleOptions", exam.getShuffleOptions());
        result.put("showAnswerAfterSubmit", exam.getShowAnswerAfterSubmit()); result.put("status", exam.getStatus());
        result.put("allowReviewAfterSubmit", exam.getAllowReviewAfterSubmit());
        result.put("assessmentType", "EXAM"); result.put("questionCount", examQuestionRepository.findByQuizIdOrderBySortOrderAsc(exam.getId()).size());
        result.put("createdBy", exam.getCreatedBy());
        if (completed != null) { result.put("completed", true); result.put("lastScore", completed.getScore()); result.put("attemptStatus", completed.getStatus()); }
        else result.put("completed", false);
        return result;
    }

    private Map<String, Object> questionMap(LearningQuestion question, boolean teacher) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", question.getId()); result.put("title", question.getTitle()); result.put("stem", question.getStem());
        result.put("questionType", question.getQuestionType()); result.put("options", question.getOptions());
        result.put("difficulty", question.getDifficulty()); result.put("knowledgePoint", question.getKnowledgePoint());
        result.put("chapter", question.getChapter()); result.put("sectionId", question.getSectionId());
        if (teacher) { result.put("referenceAnswer", question.getReferenceAnswer()); result.put("analysis", resolvedAnalysis(question)); }
        return result;
    }

    private ChapterQuiz requireExam(Long id) {
        return examRepository.findById(id).filter(item -> "EXAM".equals(item.getAssessmentType()))
                .orElseThrow(() -> new NotFoundException("考试", id));
    }
    private LearningQuestion requireQuestion(Long id) { return questionRepository.findById(id).orElseThrow(() -> new NotFoundException("题目", id)); }
    private String resolvedAnalysis(LearningQuestion question) { if (StringUtils.hasText(question.getAnalysis())) return question.getAnalysis(); return "TEXT".equals(question.getQuestionType()) ? question.getReferenceAnswer() : question.getAnalysis(); }
    private QuizAttempt latestCompletedAttempt(Long examId, Long studentId) {
        return attemptRepository.findByQuizIdAndStudentIdOrderByAttemptNumberDesc(examId, studentId).stream()
                .filter(item -> Set.of("SUBMITTED", "REVIEWED", "PENDING_REVIEW").contains(item.getStatus())).findFirst().orElse(null);
    }
    private void validate(ChapterQuiz exam) {
        if (!StringUtils.hasText(exam.getTitle())) throw new BusinessException("请填写考试名称");
        if (exam.getTotalScore() == null || exam.getTotalScore() <= 0 || exam.getDurationMinutes() == null || exam.getDurationMinutes() <= 0 || exam.getAttemptLimit() == null || exam.getAttemptLimit() <= 0) throw new BusinessException("总分、时长和提交次数必须大于 0");
        if (exam.getEndAt() != null && exam.getStartAt() != null && exam.getEndAt().isBefore(exam.getStartAt())) throw new BusinessException("结束时间不能早于开始时间");
    }
    private void notifyStudents(ChapterQuiz exam) {
        List<Long> classroomIds = classroomRepository.findByCourseIdOrderByCreatedAtDesc(exam.getCourseId()).stream()
                .filter(item -> "ACTIVE".equals(item.getStatus())).map(Classroom::getId).collect(Collectors.toList());
        if (classroomIds.isEmpty()) return;
        classroomStudentRelationRepository.findByClassroomIdInAndStatus(classroomIds, "ACTIVE").stream()
                .map(ClassroomStudentRelation::getStudentId).distinct()
                .forEach(studentId -> notificationService.sendExamPublishedNotification(studentId, exam.getTitle(), exam.getId(), exam.getStartAt(), exam.getEndAt()));
    }
    private void requireCourseBuilder(Long courseId, Long userId) { if (!isAdmin() && !isCourseBuilder(courseId, userId)) throw new BusinessException("没有维护本课程考试的权限"); }
    private boolean isCourseBuilder(Long courseId, Long userId) { return courseRepository.findById(courseId).map(course -> userId.equals(course.getCreatedBy())).orElse(false) || courseMemberRepository.findByCourseIdAndUserId(courseId, userId).map(member -> "ACTIVE".equals(member.getStatus()) && Set.of("OWNER", "CO_TEACHER", "TEACHING_ASSISTANT").contains(member.getRole())).orElse(false); }
    private void requireStudentCourseAccess(Long courseId, Long studentId) { Course course = courseRepository.findById(courseId).orElseThrow(() -> new NotFoundException("课程", courseId)); if (!"ACTIVE".equals(course.getStatus())) throw new NotFoundException("课程", courseId); List<Long> classroomIds = classroomRepository.findByCourseIdOrderByCreatedAtDesc(courseId).stream().filter(item -> "ACTIVE".equals(item.getStatus())).map(Classroom::getId).collect(Collectors.toList()); if (classroomIds.isEmpty() || !classroomStudentRelationRepository.existsByClassroomIdInAndStudentIdAndStatus(classroomIds, studentId, "ACTIVE")) throw new BusinessException("您尚未加入该课程"); }
    private boolean isStudent() { return "STUDENT".equals(currentRole()); }
    private boolean isAdmin() { return Set.of("ADMIN", "SUPER_ADMIN").contains(currentRole()); }
    private Long currentUserId() { Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal(); if (principal instanceof com.vtr.security.CustomUserDetails) return ((com.vtr.security.CustomUserDetails) principal).getId(); throw new BusinessException(401, "未登录"); }
    private String currentRole() { Authentication auth = SecurityContextHolder.getContext().getAuthentication(); return auth.getAuthorities().stream().findFirst().map(item -> item.getAuthority().replace("ROLE_", "")).orElse("STUDENT"); }
    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private Long longValue(Object value) { try { return value == null || "".equals(String.valueOf(value)) ? null : Long.valueOf(String.valueOf(value)); } catch (NumberFormatException ex) { throw new BusinessException("参数格式无效"); } }
    private int intValue(Object value, int fallback) { try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); } catch (NumberFormatException ex) { return fallback; } }
    private boolean boolValue(Object value, boolean fallback) { return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value)); }
    private java.time.LocalDateTime dateTime(Object value) { if (value == null || !StringUtils.hasText(String.valueOf(value))) return null; try { return java.time.LocalDateTime.parse(String.valueOf(value).replace("Z", "")); } catch (Exception ex) { throw new BusinessException("时间格式无效"); } }
}
