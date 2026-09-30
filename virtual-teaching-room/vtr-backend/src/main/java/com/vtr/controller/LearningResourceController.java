package com.vtr.controller;

import com.vtr.common.Result;
import com.vtr.common.exception.BusinessException;
import com.vtr.common.exception.NotFoundException;
import com.vtr.entity.*;
import com.vtr.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/learning-resources")
@RequiredArgsConstructor
public class LearningResourceController {
    private final LearningQuestionRepository questionRepository;
    private final QuestionAttemptRepository attemptRepository;
    private final QuestionFavoriteRepository favoriteRepository;
    private final CourseRepository courseRepository;
    private final CourseMemberRepository courseMemberRepository;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomStudentRelationRepository classroomStudentRelationRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizAnswerRepository quizAnswerRepository;
    private final ChapterQuizRepository chapterQuizRepository;
    private final ChapterQuizQuestionRepository chapterQuizQuestionRepository;

    @GetMapping("/questions")
    @PreAuthorize("isAuthenticated()")
    public Result<List<Map<String, Object>>> questions(@RequestParam Long courseId, @RequestParam(required = false) String chapter, @RequestParam(required = false) Long sectionId, @RequestParam(required = false) Long assignmentId) {
        Long userId = currentUserId();
        String role = currentRole();
        if ("STUDENT".equals(role)) requireStudentCourseAccess(courseId, userId);
        List<LearningQuestion> items = questionsFor(courseId, chapter, sectionId);
        Set<Long> favoriteIds = "STUDENT".equals(role) ? favoriteQuestionIds(userId) : Set.of();
        Set<Long> answerQuestionIds = submittedAssignmentQuestionIds(assignmentId, courseId, userId, role);
        return Result.success(items.stream().filter(item -> canView(item, userId, role)).map(item -> questionMap(item,
                !"STUDENT".equals(role) || answerQuestionIds.contains(item.getId()), favoriteIds.contains(item.getId()))).collect(Collectors.toList()));
    }

    @PostMapping("/questions/{id}/favorite")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<Void> favorite(@PathVariable Long id) {
        LearningQuestion item = requireQuestion(id);
        Long studentId = currentUserId();
        requireStudentCourseAccess(item.getCourseId(), studentId);
        if (!"PUBLISHED".equals(item.getStatus())) throw new NotFoundException("练习题", id);
        favoriteRepository.findByQuestionIdAndStudentId(id, studentId).orElseGet(() ->
                favoriteRepository.save(QuestionFavorite.builder().questionId(id).studentId(studentId).build()));
        return Result.success();
    }

    @DeleteMapping("/questions/{id}/favorite")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<Void> unfavorite(@PathVariable Long id) {
        LearningQuestion item = requireQuestion(id);
        Long studentId = currentUserId();
        requireStudentCourseAccess(item.getCourseId(), studentId);
        favoriteRepository.findByQuestionIdAndStudentId(id, studentId).ifPresent(favoriteRepository::delete);
        return Result.success();
    }

    @GetMapping("/favorites")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<List<Map<String, Object>>> favorites(@RequestParam Long courseId, @RequestParam(required = false) String chapter, @RequestParam(required = false) Long sectionId) {
        Long studentId = currentUserId();
        requireStudentCourseAccess(courseId, studentId);
        return Result.success(favoriteRepository.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .map(favorite -> questionRepository.findById(favorite.getQuestionId()).orElse(null))
                .filter(Objects::nonNull)
                .filter(item -> courseId.equals(item.getCourseId()) && (chapter == null || chapter.equals(item.getChapter())) && (sectionId == null || sectionId.equals(item.getSectionId())) && "PUBLISHED".equals(item.getStatus()))
                .map(item -> questionMap(item, false, true))
                .collect(Collectors.toList()));
    }

    @PostMapping("/questions")
    @PreAuthorize("hasRole('TEACHER')")
    public Result<Long> createQuestion(@RequestBody Map<String, Object> body) {
        Long userId = currentUserId(); Long courseId = longValue(body.get("courseId"));
        requireCourseBuilder(courseId, userId);
        LearningQuestion item = LearningQuestion.builder().title(text(body, "title")).stem(text(body, "stem"))
                .questionType(defaultText(body, "questionType", "SINGLE_CHOICE")).options(text(body, "options"))
                .referenceAnswer(text(body, "referenceAnswer")).analysis(text(body, "analysis"))
                .difficulty(intValue(body.get("difficulty"), 3)).knowledgePoint(text(body, "knowledgePoint"))
                .courseId(courseId).chapter(text(body, "chapter")).sectionId(longValue(body.get("sectionId"))).teacherId(userId).status("PUBLISHED").build();
        validateQuestion(item);
        return Result.success(questionRepository.save(item).getId());
    }

    @PutMapping("/questions/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public Result<Void> updateQuestion(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        LearningQuestion item = requireQuestion(id); requireOwner(item.getTeacherId());
        item.setTitle(text(body, "title")); item.setStem(text(body, "stem")); item.setQuestionType(defaultText(body, "questionType", item.getQuestionType()));
        item.setOptions(text(body, "options")); item.setReferenceAnswer(text(body, "referenceAnswer")); item.setAnalysis(text(body, "analysis"));
        item.setDifficulty(intValue(body.get("difficulty"), item.getDifficulty())); item.setKnowledgePoint(text(body, "knowledgePoint"));
        if (body.containsKey("sectionId")) item.setSectionId(longValue(body.get("sectionId")));
        item.setStatus("PUBLISHED"); item.setAuditRemark(null); validateQuestion(item); questionRepository.save(item);
        return Result.success();
    }

    @DeleteMapping("/questions/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public Result<Void> deleteQuestion(@PathVariable Long id) {
        LearningQuestion item = requireQuestion(id); requireOwner(item.getTeacherId());
        // 题目可能已经被作业、测验或答题记录引用，使用软删除避免破坏历史数据。
        item.setStatus("DELETED");
        item.setAuditRemark("教师删除题目");
        questionRepository.save(item);
        return Result.success();
    }

    @PostMapping("/questions/{id}/attempts")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<Map<String, Object>> attempt(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        LearningQuestion question = requireQuestion(id);
        requireStudentCourseAccess(question.getCourseId(), currentUserId());
        if (!"PUBLISHED".equals(question.getStatus())) throw new NotFoundException("练习题", id);
        String answer = text(body, "answer"); boolean subjective = "TEXT".equals(question.getQuestionType()) || "PROGRAMMING".equals(question.getQuestionType());
        boolean correct = !subjective && normalizeAnswer(answer, question.getQuestionType()).equals(normalizeAnswer(question.getReferenceAnswer(), question.getQuestionType()));
        QuestionAttempt attempt = QuestionAttempt.builder().questionId(id).studentId(currentUserId()).answer(answer).correct(subjective ? null : correct)
                .reviewStatus(subjective ? "PENDING" : "AUTO")
                .elapsedSeconds(intValue(body.get("elapsedSeconds"), 0)).submittedAt(LocalDateTime.now()).build();
        attemptRepository.save(attempt);
        Map<String, Object> result = new HashMap<>(); result.put("correct", subjective ? null : correct); result.put("pendingReview", subjective); result.put("reviewStatus", subjective ? "PENDING" : "AUTO"); result.put("analysis", resolvedAnalysis(question));
        if (!subjective) result.put("referenceAnswer", question.getReferenceAnswer());
        return Result.success(result);
    }

    @GetMapping("/attempts/pending")
    @PreAuthorize("hasRole('TEACHER')")
    public Result<List<Map<String, Object>>> pendingAttempts(@RequestParam Long courseId, @RequestParam(required = false) Long sectionId) {
        if (!isCourseBuilder(courseId, currentUserId())) throw new BusinessException("没有查看本课程批改任务的权限");
        List<Map<String, Object>> result = attemptRepository.findByReviewStatusOrderBySubmittedAtAsc("PENDING").stream().map(attempt -> {
            LearningQuestion question = questionRepository.findById(attempt.getQuestionId()).orElse(null);
            if (question == null || !courseId.equals(question.getCourseId()) || (sectionId != null && !sectionId.equals(question.getSectionId()))) return null;
            Map<String, Object> map = new HashMap<>(); map.put("id", attempt.getId()); map.put("questionId", question.getId()); map.put("title", question.getTitle()); map.put("stem", question.getStem()); map.put("answer", attempt.getAnswer()); map.put("studentId", attempt.getStudentId()); map.put("submittedAt", attempt.getSubmittedAt()); return map;
        }).filter(Objects::nonNull).collect(Collectors.toList());
        return Result.success(result);
    }

    @PostMapping("/attempts/{id}/review")
    @PreAuthorize("hasRole('TEACHER')")
    public Result<Map<String, Object>> reviewAttempt(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        QuestionAttempt attempt = attemptRepository.findById(id).orElseThrow(() -> new NotFoundException("答题记录", id));
        LearningQuestion question = requireQuestion(attempt.getQuestionId());
        if (!isCourseBuilder(question.getCourseId(), currentUserId())) throw new BusinessException("没有批改本课程答案的权限");
        int score = intValue(body.get("score"), -1);
        if (score < 0 || score > 100) throw new BusinessException("评分范围为 0-100");
        attempt.setScore(score); attempt.setCorrect(score >= 60); attempt.setReviewStatus("REVIEWED"); attempt.setReviewedBy(currentUserId()); attempt.setReviewedAt(LocalDateTime.now()); attemptRepository.save(attempt);
        return Result.success(Map.of("score", score, "correct", attempt.getCorrect(), "reviewStatus", attempt.getReviewStatus()));
    }

    @PostMapping("/{kind}/{id}/review")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> review(@PathVariable String kind, @PathVariable Long id, @RequestBody Map<String, Object> body) {
        String status = "APPROVE".equalsIgnoreCase(text(body, "action")) ? "PUBLISHED" : "REJECTED";
        String remark = text(body, "remark"); if ("REJECTED".equals(status) && !StringUtils.hasText(remark)) throw new BusinessException("驳回时请填写原因");
        if ("questions".equals(kind)) { LearningQuestion item = requireQuestion(id); item.setStatus(status); item.setAuditRemark(remark); questionRepository.save(item); }
        else throw new BusinessException("资源类型无效");
        return Result.success();
    }

    @PostMapping("/{kind}/{id}/archive")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> archive(@PathVariable String kind, @PathVariable Long id) {
        if ("questions".equals(kind)) { LearningQuestion item = requireQuestion(id); item.setStatus("ARCHIVED"); questionRepository.save(item); }
        else throw new BusinessException("资源类型无效");
        return Result.success();
    }

    @GetMapping("/attempts/me")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<List<Map<String, Object>>> myAttempts(@RequestParam Long courseId, @RequestParam(required = false) String chapter, @RequestParam(required = false) Long sectionId) {
        requireStudentCourseAccess(courseId, currentUserId());
        return Result.success(attemptRepository.findByStudentIdOrderBySubmittedAtDesc(currentUserId()).stream().map(attempt -> {
            LearningQuestion question = questionRepository.findById(attempt.getQuestionId()).orElse(null); if (question == null || !courseId.equals(question.getCourseId()) || (chapter != null && !chapter.equals(question.getChapter())) || (sectionId != null && !sectionId.equals(question.getSectionId()))) return null;
            Map<String, Object> map = new HashMap<>(); map.put("id", attempt.getId()); map.put("questionId", question.getId()); map.put("title", question.getTitle()); map.put("chapter", question.getChapter()); map.put("sectionId", question.getSectionId()); map.put("correct", attempt.getCorrect()); map.put("reviewStatus", attempt.getReviewStatus()); map.put("submittedAt", attempt.getSubmittedAt()); map.put("elapsedSeconds", attempt.getElapsedSeconds()); return map;
        }).filter(Objects::nonNull).collect(Collectors.toList()));
    }

    @GetMapping("/mistakes/me")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<List<Map<String, Object>>> myMistakes(@RequestParam Long courseId) {
        Long studentId = currentUserId();
        requireStudentCourseAccess(courseId, studentId);
        Map<Long, Map<String, Object>> mistakes = new LinkedHashMap<>();
        for (QuestionAttempt attempt : attemptRepository.findByStudentIdOrderBySubmittedAtDesc(studentId)) {
            LearningQuestion question = questionRepository.findById(attempt.getQuestionId()).orElse(null);
            if (question == null || !courseId.equals(question.getCourseId()) || !Boolean.FALSE.equals(attempt.getCorrect())) continue;
            mistakes.putIfAbsent(question.getId(), mistakeMap(question, attempt.getAnswer(), question.getReferenceAnswer(), question.getAnalysis(), "练习", attempt.getSubmittedAt()));
        }
        for (QuizAttempt attempt : quizAttemptRepository.findByStudentIdOrderBySubmittedAtDesc(studentId)) {
            ChapterQuiz quiz = chapterQuizRepository.findById(attempt.getQuizId()).orElse(null);
            if (quiz == null || !courseId.equals(quiz.getCourseId()) || !"PUBLISHED".equals(quiz.getStatus()) || !Set.of("SUBMITTED", "REVIEWED", "PENDING_REVIEW").contains(attempt.getStatus())) continue;
            for (QuizAnswer answer : quizAnswerRepository.findByAttemptIdOrderByIdAsc(attempt.getId())) {
                if (!Boolean.FALSE.equals(answer.getCorrect())) continue;
                LearningQuestion question = questionRepository.findById(answer.getQuestionId()).orElse(null);
                if (question == null) continue;
                mistakes.putIfAbsent(question.getId(), mistakeMap(question, answer.getAnswer(), question.getReferenceAnswer(), question.getAnalysis(), quiz.getTitle(), attempt.getSubmittedAt()));
            }
        }
        for (Assignment assignment : assignmentRepository.findByCourseIdAndStatus(courseId, Assignment.AssignmentStatus.PUBLISHED)) {
            Submission submission = latestAssignmentSubmission(assignment.getId(), studentId);
            if (submission == null || !StringUtils.hasText(assignment.getQuestionIds())) continue;
            Map<Integer, String> submittedAnswers = parseAssignmentAnswers(submission.getCode());
            List<Long> questionIds = parseQuestionIds(assignment.getQuestionIds());
            for (int index = 0; index < questionIds.size(); index++) {
                LearningQuestion question = questionRepository.findById(questionIds.get(index)).orElse(null);
                String answer = submittedAnswers.get(index);
                if (question == null || !isObjectiveQuestion(question) || !StringUtils.hasText(answer)
                        || answersMatch(answer, question.getReferenceAnswer(), question.getQuestionType())) continue;
                mistakes.putIfAbsent(question.getId(), mistakeMap(question, answer, question.getReferenceAnswer(), question.getAnalysis(), "作业：" + assignment.getTitle(), submission.getSubmittedAt()));
            }
        }
        return Result.success(mistakes.values().stream().sorted((left, right) -> String.valueOf(right.get("submittedAt")).compareTo(String.valueOf(left.get("submittedAt")))).collect(Collectors.toList()));
    }

    private Submission latestAssignmentSubmission(Long assignmentId, Long studentId) {
        List<Submission> submissions = submissionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId);
        return submissions.stream().filter(item -> Boolean.TRUE.equals(item.getIsFinal())).findFirst()
                .orElseGet(() -> submissions.stream().max(Comparator.comparing(Submission::getSubmittedAt, Comparator.nullsLast(Comparator.naturalOrder()))).orElse(null));
    }

    private Map<Integer, String> parseAssignmentAnswers(String content) {
        Map<Integer, String> answers = new HashMap<>();
        if (!StringUtils.hasText(content)) return answers;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:^|\\r?\\n)第\\s*(\\d+)\\s*题\\s*[：:]\\s*([\\s\\S]*?)(?=\\r?\\n第\\s*\\d+\\s*题\\s*[：:]|$)").matcher(content);
        while (matcher.find()) answers.put(Integer.parseInt(matcher.group(1)) - 1, matcher.group(2).trim());
        return answers;
    }

    private List<Long> parseQuestionIds(String value) {
        if (!StringUtils.hasText(value)) return List.of();
        return Arrays.stream(value.split(","))
                .map(String::trim).filter(StringUtils::hasText)
                .map(this::longValue).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private boolean isObjectiveQuestion(LearningQuestion question) {
        return !Set.of("TEXT", "PROGRAMMING").contains(question.getQuestionType());
    }

    private boolean answersMatch(String actual, String expected, String questionType) {
        if (!StringUtils.hasText(expected)) return false;
        if ("MULTIPLE_CHOICE".equals(questionType)) {
            return splitAnswer(actual).stream().map(item -> normalizeChoiceToken(normalize(item))).sorted().collect(Collectors.joining(","))
                    .equals(splitAnswer(expected).stream().map(item -> normalizeChoiceToken(normalize(item))).sorted().collect(Collectors.joining(",")));
        }
        if ("FILL".equals(questionType)) {
            return splitAnswer(actual).stream().map(this::normalize).collect(Collectors.joining("|"))
                    .equals(splitAnswer(expected).stream().map(this::normalize).collect(Collectors.joining("|")));
        }
        if ("JUDGMENT".equals(questionType)) return judgmentKey(actual).equals(judgmentKey(expected));
        return normalizeChoiceToken(normalize(actual)).equals(normalizeChoiceToken(normalize(expected)));
    }

    private List<String> splitAnswer(String value) {
        return Arrays.stream(String.valueOf(value == null ? "" : value).split("[,，、;；|/\\n]+"))
                .map(String::trim).filter(StringUtils::hasText).collect(Collectors.toList());
    }

    private String judgmentKey(String value) {
        String normalized = normalize(value);
        if (Set.of("正确", "对", "yes", "true", "1").contains(normalized)) return "correct";
        if (Set.of("错误", "错", "no", "false", "0").contains(normalized)) return "wrong";
        return normalized;
    }

    private Map<String, Object> mistakeMap(LearningQuestion question, String answer, String referenceAnswer, String analysis, String source, LocalDateTime submittedAt) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", question.getId()); map.put("questionId", question.getId()); map.put("title", question.getTitle());
        map.put("stem", question.getStem()); map.put("questionType", question.getQuestionType()); map.put("options", question.getOptions());
        map.put("chapter", question.getChapter()); map.put("sectionId", question.getSectionId()); map.put("answer", answer);
        map.put("referenceAnswer", referenceAnswer); map.put("analysis", analysis); map.put("source", source); map.put("submittedAt", submittedAt);
        return map;
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<Map<String, Object>> statistics(@RequestParam Long courseId, @RequestParam String chapter, @RequestParam(required = false) Long sectionId) {
        if (!isAdmin() && !isCourseBuilder(courseId, currentUserId())) throw new BusinessException("没有查看本课程统计的权限");
        List<LearningQuestion> questions = questionsFor(courseId, chapter, sectionId).stream()
                .filter(item -> !"DELETED".equals(item.getStatus())).collect(Collectors.toList());
        List<QuestionAttempt> attempts = questions.isEmpty() ? List.of() : attemptRepository.findByQuestionIdIn(questions.stream().map(LearningQuestion::getId).collect(Collectors.toList()));
        long correct = attempts.stream().filter(item -> Boolean.TRUE.equals(item.getCorrect())).count(); Map<String, Object> map = new HashMap<>();
        map.put("questionCount", questions.size()); map.put("attemptCount", attempts.size());
        map.put("correctRate", attempts.isEmpty() ? 0 : Math.round(correct * 10000.0 / attempts.size()) / 100.0); return Result.success(map);
    }

    private Map<String, Object> questionMap(LearningQuestion item, boolean includeAnswer, boolean favorited) { Map<String, Object> map = new HashMap<>(); map.put("id", item.getId()); map.put("title", item.getTitle()); map.put("stem", item.getStem()); map.put("questionType", item.getQuestionType()); map.put("options", item.getOptions()); map.put("difficulty", item.getDifficulty()); map.put("knowledgePoint", item.getKnowledgePoint()); map.put("chapter", item.getChapter()); map.put("sectionId", item.getSectionId()); map.put("status", item.getStatus()); map.put("auditRemark", item.getAuditRemark()); map.put("teacherId", item.getTeacherId()); map.put("favorited", favorited); if (includeAnswer) { map.put("referenceAnswer", item.getReferenceAnswer()); map.put("analysis", resolvedAnalysis(item)); } return map; }
    private Set<Long> submittedAssignmentQuestionIds(Long assignmentId, Long courseId, Long userId, String role) {
        if (!"STUDENT".equals(role) || assignmentId == null) return Set.of();
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new NotFoundException("作业", assignmentId));
        if (!courseId.equals(assignment.getCourseId())) throw new BusinessException("作业不属于当前课程");
        if (submissionRepository.findByAssignmentIdAndStudentId(assignmentId, userId).isEmpty()) return Set.of();
        if (!StringUtils.hasText(assignment.getQuestionIds())) return Set.of();
        Set<Long> ids = new HashSet<>();
        for (String value : assignment.getQuestionIds().split(",")) {
            try {
                ids.add(Long.valueOf(value.trim()));
            } catch (NumberFormatException ignored) {
                // 兼容历史数据中的异常题目 ID，忽略该项即可。
            }
        }
        return ids;
    }
    private List<LearningQuestion> questionsFor(Long courseId, String chapter, Long sectionId) { if (sectionId != null) return questionRepository.findByCourseIdAndSectionIdOrderByCreatedAtDesc(courseId, sectionId); if (StringUtils.hasText(chapter)) return questionRepository.findByCourseIdAndChapterOrderByCreatedAtDesc(courseId, chapter); return questionRepository.findByCourseIdOrderByCreatedAtDesc(courseId); }
    private Set<Long> favoriteQuestionIds(Long studentId) { return favoriteRepository.findByStudentIdOrderByCreatedAtDesc(studentId).stream().map(QuestionFavorite::getQuestionId).collect(Collectors.toSet()); }
    private boolean canView(LearningQuestion item, Long userId, String role) {
        if ("DELETED".equals(item.getStatus())) return false;
        if (isAdmin() || userId.equals(item.getTeacherId())) return true;
        // 历史题目可能缺少课程归属，不能让这类数据触发 findById(null) 导致整个题库接口 500。
        boolean courseBuilder = "TEACHER".equals(role) && item.getCourseId() != null && isCourseBuilder(item.getCourseId(), userId);
        return courseBuilder || "PUBLISHED".equals(item.getStatus());
    }
    private LearningQuestion requireQuestion(Long id) { return questionRepository.findById(id).orElseThrow(() -> new NotFoundException("题目", id)); }
    private void requireOwner(Long teacherId) { if (!currentUserId().equals(teacherId)) throw new BusinessException("只能维护自己创建的资源"); }
    private void requireCourseBuilder(Long courseId, Long userId) { if (courseId == null || !isCourseBuilder(courseId, userId)) throw new BusinessException("只能建设自己负责课程的资源"); }
    private boolean isCourseBuilder(Long courseId, Long userId) {
        if (courseId == null || userId == null) return false;
        return courseRepository.findById(courseId).map(course -> userId.equals(course.getCreatedBy())).orElse(false)
                || courseMemberRepository.findByCourseIdAndUserId(courseId, userId).map(member -> "ACTIVE".equals(member.getStatus()) && Set.of("OWNER", "CO_TEACHER", "TEACHING_ASSISTANT").contains(member.getRole())).orElse(false);
    }
    private void requireStudentCourseAccess(Long courseId, Long studentId) {
        Course course = courseRepository.findById(courseId).orElseThrow(() -> new NotFoundException("课程", courseId));
        if (!"ACTIVE".equals(course.getStatus())) throw new NotFoundException("课程", courseId);
        List<Long> classroomIds = classroomRepository.findByCourseIdOrderByCreatedAtDesc(courseId).stream()
                .filter(item -> "ACTIVE".equals(item.getStatus())).map(Classroom::getId).collect(Collectors.toList());
        if (classroomIds.isEmpty() || !classroomStudentRelationRepository.existsByClassroomIdInAndStudentIdAndStatus(classroomIds, studentId, "ACTIVE")) {
            throw new BusinessException("您尚未加入该课程");
        }
    }
    private void validateQuestion(LearningQuestion item) { if (!StringUtils.hasText(item.getTitle()) || !StringUtils.hasText(item.getStem()) || !StringUtils.hasText(item.getReferenceAnswer())) throw new BusinessException("请完整填写题目、题干和参考答案"); if (item.getDifficulty() < 1 || item.getDifficulty() > 5) throw new BusinessException("难度范围为 1-5"); if ("TEXT".equals(item.getQuestionType()) && !StringUtils.hasText(item.getAnalysis())) item.setAnalysis(item.getReferenceAnswer()); }
    private String resolvedAnalysis(LearningQuestion item) { if (StringUtils.hasText(item.getAnalysis())) return item.getAnalysis(); return "TEXT".equals(item.getQuestionType()) ? item.getReferenceAnswer() : item.getAnalysis(); }
    private Long currentUserId() { Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal(); if (principal instanceof com.vtr.security.CustomUserDetails) return ((com.vtr.security.CustomUserDetails) principal).getId(); throw new BusinessException(401, "未登录"); }
    private String currentRole() { Authentication auth = SecurityContextHolder.getContext().getAuthentication(); return auth.getAuthorities().stream().findFirst().map(item -> item.getAuthority().replace("ROLE_", "")).orElse("STUDENT"); }
    private boolean isAdmin() { String role = currentRole(); return "ADMIN".equals(role) || "SUPER_ADMIN".equals(role); }
    private String text(Map<String, Object> body, String key) { Object value = body.get(key); return value == null ? "" : String.valueOf(value).trim(); }
    private String defaultText(Map<String, Object> body, String key, String fallback) { String value = text(body, key); return StringUtils.hasText(value) ? value : fallback; }
    private Long longValue(Object value) { try { return value == null ? null : Long.valueOf(String.valueOf(value)); } catch (NumberFormatException ex) { throw new BusinessException("课程参数无效"); } }
    private int intValue(Object value, int fallback) { try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); } catch (NumberFormatException ex) { return fallback; } }
    private String normalize(String value) { return String.valueOf(value).trim().replaceAll("\\s+", "").toLowerCase(); }
    private String normalizeAnswer(String value, String questionType) {
        String normalized = normalize(value);
        if ("SINGLE_CHOICE".equals(questionType)) return normalizeChoiceToken(normalized);
        if ("FILL".equals(questionType)) return normalizeFillAnswer(normalized);
        if (!"MULTIPLE_CHOICE".equals(questionType)) return normalized;
        return Arrays.stream(normalized.replace("[", "").replace("]", "").split("[,，、;；|]"))
                .map(String::trim).filter(StringUtils::hasText).map(this::normalizeChoiceToken).sorted().collect(Collectors.joining(","));
    }
    private String normalizeFillAnswer(String value) {
        return Arrays.stream(String.valueOf(value == null ? "" : value).replace("[", "").replace("]", "").split("[,，、;；|/\\n]+"))
                .map(this::normalize).filter(StringUtils::hasText).collect(Collectors.joining("|"));
    }
    private String normalizeChoiceToken(String value) { return value.replaceFirst("^(?:选项)?([a-z])(?:[.、)）:]|\\s).*$", "$1"); }
}
