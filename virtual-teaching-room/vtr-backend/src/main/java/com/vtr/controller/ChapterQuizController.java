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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chapter-quizzes")
@RequiredArgsConstructor
public class ChapterQuizController {
    private static final Set<String> COMPLETED_ATTEMPT_STATUSES = Set.of("SUBMITTED", "REVIEWED", "PENDING_REVIEW");

    private final ChapterQuizRepository quizRepository;
    private final ChapterQuizQuestionRepository quizQuestionRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizAnswerRepository quizAnswerRepository;
    private final LearningQuestionRepository questionRepository;
    private final CourseRepository courseRepository;
    private final CourseMemberRepository courseMemberRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomStudentRelationRepository classroomStudentRelationRepository;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Result<List<Map<String, Object>>> list(@RequestParam Long courseId, @RequestParam Long sectionId) {
        Long userId = currentUserId();
        if (isStudent()) {
            requireStudentCourseAccess(courseId, userId);
            List<Map<String, Object>> result = new ArrayList<>();
            for (ChapterQuiz quiz : quizRepository.findByCourseIdAndSectionIdAndStatusOrderByCreatedAtDesc(courseId, sectionId, "PUBLISHED")) {
                QuizAttempt completedAttempt = latestCompletedAttempt(quiz.getId(), userId);
                if (isAvailableToStudent(quiz) || completedAttempt != null) {
                    result.add(quizMap(quiz, false, completedAttempt));
                }
            }
            return Result.success(result);
        }
        requireCourseBuilder(courseId, userId);
        return Result.success(quizRepository.findByCourseIdAndSectionIdOrderByCreatedAtDesc(courseId, sectionId)
                .stream().map(item -> quizMap(item, true)).collect(Collectors.toList()));
    }

    @GetMapping("/completed-sections")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<Set<Long>> completedSections(@RequestParam Long courseId) {
        Long studentId = currentUserId();
        requireStudentCourseAccess(courseId, studentId);
        return Result.success(quizAttemptRepository.findCompletedSectionIds(courseId, studentId, COMPLETED_ATTEMPT_STATUSES));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        ChapterQuiz quiz = requireQuiz(id);
        boolean teacher = !isStudent();
        if (teacher) requireCourseBuilder(quiz.getCourseId(), currentUserId());
        else {
            requireStudentCourseAccess(quiz.getCourseId(), currentUserId());
            if (!"PUBLISHED".equals(quiz.getStatus())) throw new NotFoundException("章节测验", id);
        }
        return Result.success(detailMap(quiz, teacher, null));
    }

    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    @Transactional
    public Result<Long> create(@RequestBody Map<String, Object> body) {
        Long courseId = longValue(body.get("courseId"));
        requireCourseBuilder(courseId, currentUserId());
        ChapterQuiz quiz = quizFromBody(new ChapterQuiz(), body, courseId);
        quiz.setCreatedBy(currentUserId());
        quiz.setStatus("DRAFT");
        validateQuiz(quiz);
        ChapterQuiz saved = quizRepository.save(quiz);
        replaceQuestions(saved, body.get("questions"));
        return Result.success(saved.getId());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    @Transactional
    public Result<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        ChapterQuiz quiz = requireQuiz(id);
        requireCourseBuilder(quiz.getCourseId(), currentUserId());
        if (!"DRAFT".equals(quiz.getStatus())) throw new BusinessException("已发布测验请先撤回后再编辑");
        quizFromBody(quiz, body, quiz.getCourseId());
        validateQuiz(quiz);
        quizRepository.save(quiz);
        replaceQuestions(quiz, body.get("questions"));
        return Result.success();
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('TEACHER')")
    @Transactional
    public Result<Void> publish(@PathVariable Long id) {
        ChapterQuiz quiz = requireQuiz(id);
        requireCourseBuilder(quiz.getCourseId(), currentUserId());
        List<ChapterQuizQuestion> links = quizQuestionRepository.findByQuizIdOrderBySortOrderAsc(id);
        if (links.isEmpty()) throw new BusinessException("请先组卷，至少选择一道题目");
        for (ChapterQuizQuestion link : links) {
            LearningQuestion question = requireQuestion(link.getQuestionId());
            if (!"PUBLISHED".equals(question.getStatus())) throw new BusinessException("测验中包含未审核通过的题目：" + question.getTitle());
        }
        quiz.setStatus("PUBLISHED");
        quizRepository.save(quiz);
        return Result.success();
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('TEACHER')")
    public Result<Void> archive(@PathVariable Long id) {
        ChapterQuiz quiz = requireQuiz(id);
        requireCourseBuilder(quiz.getCourseId(), currentUserId());
        quiz.setStatus("ARCHIVED");
        quizRepository.save(quiz);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    @Transactional
    public Result<Void> delete(@PathVariable Long id) {
        ChapterQuiz quiz = requireQuiz(id);
        requireCourseBuilder(quiz.getCourseId(), currentUserId());
        if (!"DRAFT".equals(quiz.getStatus())) throw new BusinessException("只能删除小节测验草稿，已发布测验请先归档");
        if (quizAttemptRepository.existsByQuizId(id)) throw new BusinessException("已有作答记录，不能删除该测验");
        quizQuestionRepository.deleteByQuizId(id);
        quizRepository.delete(quiz);
        return Result.success();
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('STUDENT')")
    @Transactional
    public Result<Map<String, Object>> start(@PathVariable Long id) {
        ChapterQuiz quiz = requireQuiz(id);
        requireStudentCourseAccess(quiz.getCourseId(), currentUserId());
        if (!"PUBLISHED".equals(quiz.getStatus()) || !isAvailableToStudent(quiz)) throw new BusinessException("当前测验尚未开放");
        QuizAttempt current = quizAttemptRepository.findFirstByQuizIdAndStudentIdAndStatusOrderByAttemptNumberDesc(id, currentUserId(), "IN_PROGRESS").orElse(null);
        if (current != null && (current.getExpiresAt() == null || current.getExpiresAt().isAfter(LocalDateTime.now()))) return Result.success(detailMap(quiz, false, current));
        if (current != null) submitExpired(current, quiz);
        List<QuizAttempt> history = quizAttemptRepository.findByQuizIdAndStudentIdOrderByAttemptNumberDesc(id, currentUserId());
        if (history.size() >= safeInt(quiz.getAttemptLimit(), 1)) throw new BusinessException("该测验已达到提交次数上限");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(safeInt(quiz.getDurationMinutes(), 30));
        QuizAttempt attempt = quizAttemptRepository.save(QuizAttempt.builder().quizId(id).studentId(currentUserId())
                .attemptNumber(history.size() + 1).startedAt(now).expiresAt(expiresAt).status("IN_PROGRESS").build());
        return Result.success(detailMap(quiz, false, attempt));
    }

    @GetMapping("/{id}/attempts/me")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<List<Map<String, Object>>> myAttempts(@PathVariable Long id) {
        ChapterQuiz quiz = requireQuiz(id);
        requireStudentCourseAccess(quiz.getCourseId(), currentUserId());
        return Result.success(quizAttemptRepository.findByQuizIdAndStudentIdOrderByAttemptNumberDesc(id, currentUserId()).stream().map(this::attemptMap).collect(Collectors.toList()));
    }

    @GetMapping("/{id}/attempts/me/{attemptId}")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<Map<String, Object>> myAttemptDetail(@PathVariable Long id, @PathVariable Long attemptId) {
        ChapterQuiz quiz = requireQuiz(id);
        Long studentId = currentUserId();
        requireStudentCourseAccess(quiz.getCourseId(), studentId);
        QuizAttempt attempt = quizAttemptRepository.findById(attemptId)
                .filter(item -> id.equals(item.getQuizId()) && studentId.equals(item.getStudentId()))
                .orElseThrow(() -> new NotFoundException("测验作答", attemptId));
        if (!isCompletedAttempt(attempt)) throw new BusinessException("该测验尚未提交");
        Map<String, Object> result = detailMap(quiz, false, attempt);
        result.putAll(attemptMap(attempt));
        result.put("quizTitle", quiz.getTitle());
        result.put("totalScore", quiz.getTotalScore());
        result.put("pendingReview", "PENDING_REVIEW".equals(attempt.getStatus()));
        if ("EXAM".equals(quiz.getAssessmentType()) && !Boolean.TRUE.equals(quiz.getAllowReviewAfterSubmit())) {
            result.remove("questions");
            result.put("canReviewPaper", false);
            return Result.success(result);
        }
        result.put("canReviewPaper", true);
        List<Map<String, Object>> answers = new ArrayList<>();
        for (ChapterQuizQuestion link : quizQuestionRepository.findByQuizIdOrderBySortOrderAsc(id)) {
            QuizAnswer answer = quizAnswerRepository.findByAttemptIdAndQuestionId(attemptId, link.getQuestionId()).orElse(null);
            if (answer != null) answers.add(answerMap(answer, requireQuestion(link.getQuestionId()), quiz.getShowAnswerAfterSubmit()));
        }
        result.put("answers", answers);
        return Result.success(result);
    }

    @PostMapping("/attempts/{attemptId}/answers")
    @PreAuthorize("hasRole('STUDENT')")
    @Transactional
    public Result<Void> saveAnswers(@PathVariable Long attemptId, @RequestBody Map<String, Object> body) {
        QuizAttempt attempt = requireStudentAttempt(attemptId);
        if (!"IN_PROGRESS".equals(attempt.getStatus())) throw new BusinessException("该测验已经提交，不能继续答题");
        ChapterQuiz quiz = requireQuiz(attempt.getQuizId());
        if (attempt.getExpiresAt() != null && attempt.getExpiresAt().isBefore(LocalDateTime.now())) throw new BusinessException("答题时间已结束，请提交试卷");
        Object rawAnswers = body.get("answers");
        if (!(rawAnswers instanceof Collection)) throw new BusinessException("答题数据格式无效");
        Map<Long, Integer> scoreMap = quizQuestionRepository.findByQuizIdOrderBySortOrderAsc(quiz.getId()).stream().collect(Collectors.toMap(ChapterQuizQuestion::getQuestionId, ChapterQuizQuestion::getScore));
        for (Object raw : (Collection<?>) rawAnswers) {
            if (!(raw instanceof Map)) continue;
            Map<?, ?> item = (Map<?, ?>) raw;
            Long questionId = longValue(item.get("questionId"));
            if (!scoreMap.containsKey(questionId)) continue;
            String answer = answerText(item.get("answer"));
            QuizAnswer saved = quizAnswerRepository.findByAttemptIdAndQuestionId(attemptId, questionId).orElseGet(() -> QuizAnswer.builder().attemptId(attemptId).questionId(questionId).build());
            saved.setAnswer(answer);
            saved.setAnsweredAt(LocalDateTime.now());
            quizAnswerRepository.save(saved);
        }
        return Result.success();
    }

    @PostMapping("/attempts/{attemptId}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    @Transactional
    public Result<Map<String, Object>> submit(@PathVariable Long attemptId, @RequestBody(required = false) Map<String, Object> body) {
        QuizAttempt attempt = requireStudentAttempt(attemptId);
        if (!"IN_PROGRESS".equals(attempt.getStatus())) return Result.success(attemptMap(attempt));
        ChapterQuiz quiz = requireQuiz(attempt.getQuizId());
        if (body != null && body.containsKey("answers")) saveAnswerCollection(attempt, body.get("answers"));
        int total = 0;
        boolean pending = false;
        List<Map<String, Object>> answers = new ArrayList<>();
        for (ChapterQuizQuestion link : quizQuestionRepository.findByQuizIdOrderBySortOrderAsc(quiz.getId())) {
            LearningQuestion question = requireQuestion(link.getQuestionId());
            QuizAnswer answer = quizAnswerRepository.findByAttemptIdAndQuestionId(attemptId, question.getId()).orElse(null);
            if (answer == null || !StringUtils.hasText(answer.getAnswer())) continue;
            boolean subjective = isSubjective(question);
            if (subjective) {
                answer.setCorrect(null); answer.setScore(0); answer.setReviewStatus("PENDING"); pending = true;
            } else {
                boolean correct = normalizeAnswer(answer.getAnswer(), question.getQuestionType()).equals(normalizeAnswer(question.getReferenceAnswer(), question.getQuestionType()));
                answer.setCorrect(correct); answer.setScore(correct ? link.getScore() : 0); answer.setReviewStatus("AUTO"); total += answer.getScore();
            }
            quizAnswerRepository.save(answer);
            answers.add(answerMap(answer, question, quiz.getShowAnswerAfterSubmit()));
        }
        attempt.setScore(total);
        attempt.setStatus(pending ? "PENDING_REVIEW" : "SUBMITTED");
        attempt.setSubmittedAt(LocalDateTime.now());
        quizAttemptRepository.save(attempt);
        Map<String, Object> result = attemptMap(attempt);
        result.put("quizTitle", quiz.getTitle()); result.put("totalScore", quiz.getTotalScore()); result.put("answers", answers); result.put("pendingReview", pending);
        return Result.success(result);
    }

    @GetMapping("/review/pending")
    @PreAuthorize("hasRole('TEACHER')")
    public Result<List<Map<String, Object>>> pending(@RequestParam Long courseId, @RequestParam(required = false) Long sectionId) {
        requireCourseBuilder(courseId, currentUserId());
        List<Map<String, Object>> result = new ArrayList<>();
        for (QuizAnswer answer : quizAnswerRepository.findByReviewStatusOrderByAnsweredAtAsc("PENDING")) {
            QuizAttempt attempt = quizAttemptRepository.findById(answer.getAttemptId()).orElse(null);
            ChapterQuiz quiz = attempt == null ? null : quizRepository.findById(attempt.getQuizId()).orElse(null);
            if (quiz == null || !courseId.equals(quiz.getCourseId()) || (sectionId != null && !sectionId.equals(quiz.getSectionId()))) continue;
            LearningQuestion question = requireQuestion(answer.getQuestionId());
            Map<String, Object> item = new HashMap<>(); item.put("answerId", answer.getId()); item.put("attemptId", attempt.getId()); item.put("quizId", quiz.getId()); item.put("quizTitle", quiz.getTitle()); item.put("questionId", question.getId()); item.put("questionTitle", question.getTitle()); item.put("stem", question.getStem()); item.put("answer", answer.getAnswer()); item.put("studentId", attempt.getStudentId()); item.put("maxScore", quizQuestionRepository.findByQuizIdOrderBySortOrderAsc(quiz.getId()).stream().filter(link -> link.getQuestionId().equals(question.getId())).map(ChapterQuizQuestion::getScore).findFirst().orElse(0)); item.put("answeredAt", answer.getAnsweredAt()); result.add(item);
        }
        return Result.success(result);
    }

    @PostMapping("/answers/{answerId}/review")
    @PreAuthorize("hasRole('TEACHER')")
    @Transactional
    public Result<Map<String, Object>> reviewAnswer(@PathVariable Long answerId, @RequestBody Map<String, Object> body) {
        QuizAnswer answer = quizAnswerRepository.findById(answerId).orElseThrow(() -> new NotFoundException("测验答案", answerId));
        QuizAttempt attempt = quizAttemptRepository.findById(answer.getAttemptId()).orElseThrow(() -> new NotFoundException("测验作答", answer.getAttemptId()));
        ChapterQuiz quiz = requireQuiz(attempt.getQuizId()); requireCourseBuilder(quiz.getCourseId(), currentUserId());
        int maxScore = quizQuestionRepository.findByQuizIdOrderBySortOrderAsc(quiz.getId()).stream().filter(link -> link.getQuestionId().equals(answer.getQuestionId())).map(ChapterQuizQuestion::getScore).findFirst().orElse(0);
        int score = intValue(body.get("score"), -1);
        if (score < 0 || score > maxScore) throw new BusinessException("评分必须在 0-" + maxScore + " 分之间");
        answer.setScore(score); answer.setCorrect(score == maxScore); answer.setReviewStatus("REVIEWED"); answer.setTeacherComment(text(body.get("comment"))); quizAnswerRepository.save(answer);
        int total = quizAnswerRepository.findByAttemptIdOrderByIdAsc(attempt.getId()).stream().map(item -> item.getScore() == null ? 0 : item.getScore()).reduce(0, Integer::sum);
        boolean pending = quizAnswerRepository.findByAttemptIdOrderByIdAsc(attempt.getId()).stream().anyMatch(item -> "PENDING".equals(item.getReviewStatus()));
        attempt.setScore(total); if (!pending) attempt.setStatus("REVIEWED"); quizAttemptRepository.save(attempt);
        return Result.success(Map.of("score", score, "totalScore", total, "reviewStatus", answer.getReviewStatus(), "attemptStatus", attempt.getStatus()));
    }

    private ChapterQuiz quizFromBody(ChapterQuiz quiz, Map<String, Object> body, Long courseId) {
        quiz.setCourseId(courseId); quiz.setChapter(text(body.get("chapter"))); quiz.setSectionId(longValue(body.get("sectionId"))); quiz.setTitle(text(body.get("title"))); quiz.setDescription(text(body.get("description")));
        quiz.setTotalScore(intValue(body.get("totalScore"), 100)); quiz.setDurationMinutes(intValue(body.get("durationMinutes"), 30)); quiz.setAttemptLimit(intValue(body.get("attemptLimit"), 1));
        quiz.setStartAt(dateTime(body.get("startAt"))); quiz.setEndAt(null); quiz.setShuffleQuestions(boolValue(body.get("shuffleQuestions"), false)); quiz.setShuffleOptions(boolValue(body.get("shuffleOptions"), false)); quiz.setShowAnswerAfterSubmit(boolValue(body.get("showAnswerAfterSubmit"), true));
        quiz.setAllowReviewAfterSubmit(boolValue(body.get("allowReviewAfterSubmit"), true));
        return quiz;
    }

    private void replaceQuestions(ChapterQuiz quiz, Object rawQuestions) {
        quizQuestionRepository.deleteAll(quizQuestionRepository.findByQuizIdOrderBySortOrderAsc(quiz.getId()));
        if (!(rawQuestions instanceof Collection)) return;
        List<ChapterQuizQuestion> links = new ArrayList<>();
        for (Object raw : (Collection<?>) rawQuestions) {
            Long questionId; int score = 10;
            if (raw instanceof Map) { Map<?, ?> item = (Map<?, ?>) raw; questionId = longValue(item.get("questionId")); score = intValue(item.get("score"), 10); }
            else questionId = longValue(raw);
            if (questionId == null || score <= 0) continue;
            LearningQuestion question = requireQuestion(questionId);
            if (!quiz.getCourseId().equals(question.getCourseId()) || (quiz.getSectionId() != null && !quiz.getSectionId().equals(question.getSectionId()))) throw new BusinessException("组卷题目必须属于当前小节");
            links.add(ChapterQuizQuestion.builder().quizId(quiz.getId()).questionId(questionId).sortOrder(links.size() + 1).score(score).build());
        }
        if (links.isEmpty()) return;
        if (safeInt(quiz.getTotalScore(), 0) < links.size()) throw new BusinessException("测验总分不能小于题目数量");

        // Keep every question's score positive and make the linked scores add up to the quiz total.
        int totalScore = quiz.getTotalScore();
        int distributable = totalScore - links.size();
        int weightTotal = links.stream().mapToInt(link -> link.getScore()).sum();
        int assigned = 0;
        List<Double> remainders = new ArrayList<>();
        for (ChapterQuizQuestion link : links) {
            double exact = weightTotal == 0 ? 0 : (double) distributable * link.getScore() / weightTotal;
            int score = 1 + (int) Math.floor(exact);
            link.setScore(score);
            assigned += score;
            remainders.add(exact - Math.floor(exact));
        }
        for (int i = 0; i < totalScore - assigned; i++) {
            int bestIndex = 0;
            for (int j = 1; j < remainders.size(); j++) {
                if (remainders.get(j) > remainders.get(bestIndex)) bestIndex = j;
            }
            links.get(bestIndex).setScore(links.get(bestIndex).getScore() + 1);
            remainders.set(bestIndex, -1d);
        }
        quizQuestionRepository.saveAll(links);
    }

    private Map<String, Object> detailMap(ChapterQuiz quiz, boolean teacher, QuizAttempt attempt) {
        Map<String, Object> map = quizMap(quiz, teacher); List<Map<String, Object>> items = new ArrayList<>();
        Map<Long, QuizAnswer> answerMap = attempt == null ? Map.of() : quizAnswerRepository.findByAttemptIdOrderByIdAsc(attempt.getId()).stream().collect(Collectors.toMap(QuizAnswer::getQuestionId, item -> item, (left, right) -> right));
        for (ChapterQuizQuestion link : quizQuestionRepository.findByQuizIdOrderBySortOrderAsc(quiz.getId())) {
            LearningQuestion question = requireQuestion(link.getQuestionId()); Map<String, Object> item = questionMap(question, teacher); item.put("score", link.getScore()); item.put("sortOrder", link.getSortOrder());
            QuizAnswer answer = answerMap.get(question.getId()); if (answer != null) item.put("answer", answer.getAnswer()); items.add(item);
        }
        map.put("questions", items); if (attempt != null) map.put("attempt", attemptMap(attempt)); return map;
    }

    private Map<String, Object> quizMap(ChapterQuiz quiz, boolean teacher) { return quizMap(quiz, teacher, null); }
    private Map<String, Object> quizMap(ChapterQuiz quiz, boolean teacher, QuizAttempt completedAttempt) { Map<String, Object> map = new LinkedHashMap<>(); map.put("id", quiz.getId()); map.put("courseId", quiz.getCourseId()); map.put("chapter", quiz.getChapter()); map.put("sectionId", quiz.getSectionId()); map.put("assessmentType", quiz.getAssessmentType()); map.put("title", quiz.getTitle()); map.put("description", quiz.getDescription()); map.put("totalScore", quiz.getTotalScore()); map.put("durationMinutes", quiz.getDurationMinutes()); map.put("attemptLimit", quiz.getAttemptLimit()); map.put("startAt", quiz.getStartAt()); map.put("endAt", quiz.getEndAt()); map.put("shuffleQuestions", quiz.getShuffleQuestions()); map.put("shuffleOptions", quiz.getShuffleOptions()); map.put("showAnswerAfterSubmit", quiz.getShowAnswerAfterSubmit()); map.put("allowReviewAfterSubmit", quiz.getAllowReviewAfterSubmit()); map.put("status", quiz.getStatus()); map.put("questionCount", quizQuestionRepository.findByQuizIdOrderBySortOrderAsc(quiz.getId()).size()); if (teacher) map.put("createdBy", quiz.getCreatedBy()); else { map.put("completed", completedAttempt != null); map.put("attemptStatus", completedAttempt == null ? null : completedAttempt.getStatus()); map.put("lastScore", completedAttempt == null ? null : completedAttempt.getScore()); map.put("lastAttemptId", completedAttempt == null ? null : completedAttempt.getId()); } return map; }
    private Map<String, Object> questionMap(LearningQuestion q, boolean teacher) { Map<String, Object> map = new LinkedHashMap<>(); map.put("id", q.getId()); map.put("title", q.getTitle()); map.put("stem", q.getStem()); map.put("questionType", q.getQuestionType()); map.put("options", q.getOptions()); map.put("difficulty", q.getDifficulty()); map.put("knowledgePoint", q.getKnowledgePoint()); if (teacher) { map.put("referenceAnswer", q.getReferenceAnswer()); map.put("analysis", resolvedAnalysis(q)); } return map; }
    private Map<String, Object> attemptMap(QuizAttempt item) { Map<String, Object> map = new LinkedHashMap<>(); map.put("id", item.getId()); map.put("quizId", item.getQuizId()); map.put("attemptNumber", item.getAttemptNumber()); map.put("startedAt", item.getStartedAt()); map.put("expiresAt", item.getExpiresAt()); map.put("submittedAt", item.getSubmittedAt()); map.put("status", item.getStatus()); map.put("score", item.getScore()); return map; }
    private QuizAttempt latestCompletedAttempt(Long quizId, Long studentId) { return quizAttemptRepository.findByQuizIdAndStudentIdOrderByAttemptNumberDesc(quizId, studentId).stream().filter(this::isCompletedAttempt).findFirst().orElse(null); }
    private boolean isCompletedAttempt(QuizAttempt attempt) { return attempt != null && COMPLETED_ATTEMPT_STATUSES.contains(attempt.getStatus()); }
    private Map<String, Object> answerMap(QuizAnswer answer, LearningQuestion question, boolean showAnswer) { Map<String, Object> map = new LinkedHashMap<>(); map.put("questionId", question.getId()); map.put("answer", answer.getAnswer()); map.put("correct", answer.getCorrect()); map.put("score", answer.getScore()); map.put("reviewStatus", answer.getReviewStatus()); if (showAnswer && !isSubjective(question)) { map.put("referenceAnswer", question.getReferenceAnswer()); map.put("analysis", resolvedAnalysis(question)); } if (answer.getTeacherComment() != null) map.put("teacherComment", answer.getTeacherComment()); return map; }
    private void saveAnswerCollection(QuizAttempt attempt, Object rawAnswers) { if (!(rawAnswers instanceof Collection)) throw new BusinessException("答题数据格式无效"); for (Object raw : (Collection<?>) rawAnswers) { if (!(raw instanceof Map)) continue; Map<?, ?> item = (Map<?, ?>) raw; Long questionId = longValue(item.get("questionId")); if (questionId == null) continue; QuizAnswer answer = quizAnswerRepository.findByAttemptIdAndQuestionId(attempt.getId(), questionId).orElseGet(() -> QuizAnswer.builder().attemptId(attempt.getId()).questionId(questionId).build()); answer.setAnswer(answerText(item.get("answer"))); answer.setAnsweredAt(LocalDateTime.now()); quizAnswerRepository.save(answer); } }
    private void submitExpired(QuizAttempt attempt, ChapterQuiz quiz) { attempt.setStatus("SUBMITTED"); attempt.setSubmittedAt(LocalDateTime.now()); quizAttemptRepository.save(attempt); }
    private ChapterQuiz requireQuiz(Long id) { return quizRepository.findById(id).orElseThrow(() -> new NotFoundException("章节测验", id)); }
    private LearningQuestion requireQuestion(Long id) { return questionRepository.findById(id).orElseThrow(() -> new NotFoundException("题目", id)); }
    private QuizAttempt requireStudentAttempt(Long id) { QuizAttempt item = quizAttemptRepository.findById(id).orElseThrow(() -> new NotFoundException("测验作答", id)); if (!currentUserId().equals(item.getStudentId())) throw new BusinessException("无权访问该测验作答"); return item; }
    private void requireCourseBuilder(Long courseId, Long userId) { if (!isAdmin() && !isCourseBuilder(courseId, userId)) throw new BusinessException("没有维护本课程章节测验的权限"); }
    private boolean isCourseBuilder(Long courseId, Long userId) { return courseRepository.findById(courseId).map(course -> userId.equals(course.getCreatedBy())).orElse(false) || courseMemberRepository.findByCourseIdAndUserId(courseId, userId).map(member -> "ACTIVE".equals(member.getStatus()) && Set.of("OWNER", "CO_TEACHER", "TEACHING_ASSISTANT").contains(member.getRole())).orElse(false); }
    private void requireStudentCourseAccess(Long courseId, Long studentId) { Course course = courseRepository.findById(courseId).orElseThrow(() -> new NotFoundException("课程", courseId)); if (!"ACTIVE".equals(course.getStatus())) throw new NotFoundException("课程", courseId); List<Long> classroomIds = classroomRepository.findByCourseIdOrderByCreatedAtDesc(courseId).stream().filter(item -> "ACTIVE".equals(item.getStatus())).map(Classroom::getId).collect(Collectors.toList()); if (classroomIds.isEmpty() || !classroomStudentRelationRepository.existsByClassroomIdInAndStudentIdAndStatus(classroomIds, studentId, "ACTIVE")) throw new BusinessException("您尚未加入该课程"); }
    private boolean isStudent() { return "STUDENT".equals(currentRole()); }
    private boolean isAdmin() { String role = currentRole(); return "ADMIN".equals(role) || "SUPER_ADMIN".equals(role); }
    private boolean isAvailableToStudent(ChapterQuiz quiz) { LocalDateTime now = LocalDateTime.now(); return (quiz.getStartAt() == null || !now.isBefore(quiz.getStartAt())) && (quiz.getEndAt() == null || now.isBefore(quiz.getEndAt())); }
    private String resolvedAnalysis(LearningQuestion question) { if (StringUtils.hasText(question.getAnalysis())) return question.getAnalysis(); return "TEXT".equals(question.getQuestionType()) ? question.getReferenceAnswer() : question.getAnalysis(); }
    private boolean isSubjective(LearningQuestion question) { return "TEXT".equals(question.getQuestionType()) || "PROGRAMMING".equals(question.getQuestionType()); }
    private String currentRole() { Authentication auth = SecurityContextHolder.getContext().getAuthentication(); return auth.getAuthorities().stream().findFirst().map(item -> item.getAuthority().replace("ROLE_", "")).orElse("STUDENT"); }
    private Long currentUserId() { Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal(); if (principal instanceof com.vtr.security.CustomUserDetails) return ((com.vtr.security.CustomUserDetails) principal).getId(); throw new BusinessException(401, "未登录"); }
    private void validateQuiz(ChapterQuiz quiz) { if (!StringUtils.hasText(quiz.getTitle())) throw new BusinessException("请填写测验名称"); if (quiz.getSectionId() == null) throw new BusinessException("章节测验必须关联小节"); if (safeInt(quiz.getTotalScore(), 0) <= 0 || safeInt(quiz.getDurationMinutes(), 0) <= 0 || safeInt(quiz.getAttemptLimit(), 0) <= 0) throw new BusinessException("总分、时长和提交次数必须大于 0"); }
    private int safeInt(Integer value, int fallback) { return value == null ? fallback : value; }
    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private Long longValue(Object value) { try { return value == null || "".equals(String.valueOf(value)) ? null : Long.valueOf(String.valueOf(value)); } catch (NumberFormatException ex) { throw new BusinessException("参数格式无效"); } }
    private int intValue(Object value, int fallback) { try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); } catch (NumberFormatException ex) { return fallback; } }
    private boolean boolValue(Object value, boolean fallback) { return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value)); }
    private LocalDateTime dateTime(Object value) { if (value == null || !StringUtils.hasText(String.valueOf(value))) return null; try { return LocalDateTime.parse(String.valueOf(value).replace("Z", "")); } catch (Exception ex) { throw new BusinessException("时间格式无效"); } }
    private String answerText(Object value) { if (value == null) return ""; if (value instanceof Collection) return ((Collection<?>) value).stream().map(String::valueOf).collect(Collectors.joining(",")); return String.valueOf(value); }
    private String normalize(String value) { return String.valueOf(value).trim().replaceAll("\\s+", "").toLowerCase(); }
    private String normalizeAnswer(String value, String questionType) { String normalized = normalize(value); if ("SINGLE_CHOICE".equals(questionType)) return normalizeChoiceToken(normalized); if ("FILL".equals(questionType)) return normalizeFillAnswer(normalized); if (!"MULTIPLE_CHOICE".equals(questionType)) return normalized; return Arrays.stream(normalized.replace("[", "").replace("]", "").split("[,，、;；|]")).map(String::trim).filter(StringUtils::hasText).map(this::normalizeChoiceToken).sorted().collect(Collectors.joining(",")); }
    private String normalizeFillAnswer(String value) { return Arrays.stream(String.valueOf(value == null ? "" : value).replace("[", "").replace("]", "").split("[,，、;；|/\\n]+")) .map(this::normalize).filter(StringUtils::hasText).collect(Collectors.joining("|")); }
    private String normalizeChoiceToken(String value) { return value.replaceFirst("^(?:选项)?([a-z])(?:[.、)）:]|\\s).*$", "$1"); }
}
