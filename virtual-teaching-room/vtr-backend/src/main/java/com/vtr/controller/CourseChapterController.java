package com.vtr.controller;

import com.vtr.common.Result;
import com.vtr.common.exception.BusinessException;
import com.vtr.common.exception.NotFoundException;
import com.vtr.dto.CourseChapterUpsertDTO;
import com.vtr.entity.Course;
import com.vtr.entity.CourseChapter;
import com.vtr.entity.CourseMember;
import com.vtr.entity.ChapterQuiz;
import com.vtr.repository.ClassroomRepository;
import com.vtr.repository.ClassroomStudentRelationRepository;
import com.vtr.repository.CourseChapterRepository;
import com.vtr.repository.CourseMemberRepository;
import com.vtr.repository.CourseRepository;
import com.vtr.repository.CourseSectionRepository;
import com.vtr.repository.CoursewareRepository;
import com.vtr.repository.LearningQuestionRepository;
import com.vtr.repository.ChapterQuizRepository;
import com.vtr.security.CustomUserDetails;
import com.vtr.vo.CourseChapterVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/courses/{courseId}/chapters")
@RequiredArgsConstructor
public class CourseChapterController {
    private final CourseRepository courseRepository;
    private final CourseChapterRepository chapterRepository;
    private final CourseSectionRepository sectionRepository;
    private final CourseMemberRepository courseMemberRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomStudentRelationRepository classroomStudentRelationRepository;
    private final CoursewareRepository coursewareRepository;
    private final LearningQuestionRepository questionRepository;
    private final ChapterQuizRepository quizRepository;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Result<List<CourseChapterVO>> list(@PathVariable Long courseId) {
        CustomUserDetails user = currentUser();
        Course course = requireCourse(courseId);
        boolean student = "STUDENT".equals(role());
        if (student) requireStudentAccess(course, user.getId());
        else requireWorkspaceAccess(course, user.getId());

        Set<String> names = new LinkedHashSet<>();
        List<CourseChapter> configured = chapterRepository.findByCourseIdAndStatusOrderBySortOrderAscIdAsc(courseId, "ACTIVE");
        // Keep the configured chapter title (including its display casing), while
        // matching legacy resource/question names by a canonical value.
        java.util.Map<String, String> activeTitles = new java.util.LinkedHashMap<>();
        for (CourseChapter item : configured) {
            String title = normalizeTitle(item.getTitle());
            if (!title.isBlank()) {
                activeTitles.putIfAbsent(canonicalTitle(title), title);
                names.add(title);
            }
        }
        if (student) {
            names.addAll(questionRepository.findDistinctPublishedChapterNames(courseId));
            names.addAll(coursewareRepository.findDistinctVisibleChapterNames(courseId, user.getId()));
        } else {
            names.addAll(questionRepository.findDistinctChapterNames(courseId));
            names.addAll(coursewareRepository.findDistinctChapterNames(courseId));
        }

        // Archived chapters remain in the database for audit/history, but must not be
        // reintroduced as legacy chapters from old resource/question titles.
        // Normalize names coming from legacy tables before filtering. Otherwise a
        // deleted "第 2 章" could reappear as "第 2 章 " (or with different case).
        Set<String> normalizedNames = new LinkedHashSet<>();
        for (String name : names) {
            String normalized = normalizeTitle(name);
            if (normalized.isBlank()) continue;
            normalizedNames.add(activeTitles.getOrDefault(canonicalTitle(normalized), normalized));
        }
        names = normalizedNames;

        Set<String> archivedTitles = chapterRepository.findByCourseId(courseId).stream()
                .filter(item -> "ARCHIVED".equals(item.getStatus()))
                .map(CourseChapter::getTitle)
                .map(this::canonicalTitle)
                .collect(java.util.stream.Collectors.toSet());
        names.removeIf(name -> archivedTitles.contains(canonicalTitle(name))
                && !activeTitles.containsKey(canonicalTitle(name)));

        List<CourseChapterVO> result = new ArrayList<>();
        for (String name : names) {
            CourseChapter chapter = configured.stream()
                    .filter(item -> canonicalTitle(name).equals(canonicalTitle(item.getTitle())))
                    .findFirst().orElse(null);
            result.add(toVO(courseId, chapter, name, student));
        }
        return Result.success(result);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    @Transactional
    public Result<Long> create(@PathVariable Long courseId, @RequestBody @Valid CourseChapterUpsertDTO dto) {
        Course course = requireManageableCourse(courseId);
        String title = normalizeTitle(dto.getTitle());
        if (chapterRepository.findByCourseIdAndStatusOrderBySortOrderAscIdAsc(courseId, "ACTIVE").stream()
                .anyMatch(item -> item.getTitle().equalsIgnoreCase(title))) {
            throw new BusinessException("该课程已存在同名章节");
        }
        int sortOrder = dto.getSortOrder() == null ? nextSortOrder(courseId) : normalizeSortOrder(dto.getSortOrder());
        shiftForInsert(courseId, sortOrder);
        CourseChapter chapter = CourseChapter.builder()
                .courseId(course.getId()).title(title).subtitle(blankToNull(dto.getSubtitle()))
                .description(blankToNull(dto.getDescription())).sortOrder(sortOrder)
                .status("ACTIVE").build();
        return Result.success(chapterRepository.save(chapter).getId());
    }

    @PostMapping("/sync-legacy")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    @Transactional
    public Result<Integer> syncLegacy(@PathVariable Long courseId) {
        Course course = requireManageableCourse(courseId);
        Set<String> names = new LinkedHashSet<>();
        names.addAll(coursewareRepository.findDistinctChapterNames(courseId));
        names.addAll(questionRepository.findDistinctChapterNames(courseId));

        List<CourseChapter> existing = chapterRepository.findByCourseId(courseId);
        Set<String> existingNames = existing.stream()
                .map(CourseChapter::getTitle)
                .map(this::canonicalTitle)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        int nextSortOrder = existing.stream()
                .map(CourseChapter::getSortOrder)
                .filter(item -> item != null)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0) + 1;
        int created = 0;
        for (String name : names) {
            String title = normalizeTitle(name);
            // Archived chapters remain historical records. Never recreate one
            // from a stale resource/question row during legacy synchronization.
            if (title.isBlank() || existingNames.contains(canonicalTitle(title))) continue;
            chapterRepository.save(CourseChapter.builder()
                    .courseId(course.getId())
                    .title(title)
                    .description("已从课程资源目录同步，可继续完善章节信息。")
                    .sortOrder(nextSortOrder++)
                    .status("ACTIVE")
                    .build());
            existingNames.add(canonicalTitle(title));
            created++;
        }
        return Result.success(created);
    }

    @PutMapping("/{chapterId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    @Transactional
    public Result<Void> update(@PathVariable Long courseId, @PathVariable Long chapterId,
                               @RequestBody @Valid CourseChapterUpsertDTO dto) {
        requireManageableCourse(courseId);
        CourseChapter chapter = chapterRepository.findByIdAndCourseId(chapterId, courseId)
                .orElseThrow(() -> new NotFoundException("课程章节", chapterId));
        String title = normalizeTitle(dto.getTitle());
        if (chapterRepository.findByCourseIdAndStatusOrderBySortOrderAscIdAsc(courseId, "ACTIVE").stream()
                .anyMatch(item -> !item.getId().equals(chapterId) && item.getTitle().equalsIgnoreCase(title))) {
            throw new BusinessException("该课程已存在同名章节");
        }
        int oldSortOrder = chapter.getSortOrder() == null ? nextSortOrder(courseId) : chapter.getSortOrder();
        int newSortOrder = dto.getSortOrder() == null ? oldSortOrder : normalizeSortOrder(dto.getSortOrder());
        if (newSortOrder != oldSortOrder) repositionChapter(courseId, chapterId, oldSortOrder, newSortOrder);
        chapter.setTitle(title);
        if (dto.getSubtitle() != null) chapter.setSubtitle(blankToNull(dto.getSubtitle()));
        if (dto.getDescription() != null) chapter.setDescription(blankToNull(dto.getDescription()));
        if (dto.getSortOrder() != null) chapter.setSortOrder(dto.getSortOrder());
        chapterRepository.save(chapter);
        return Result.success();
    }

    @PostMapping("/{chapterId}/archive")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    @Transactional
    public Result<Void> archive(@PathVariable Long courseId, @PathVariable Long chapterId) {
        requireManageableCourse(courseId);
        CourseChapter chapter = chapterRepository.findByIdAndCourseId(chapterId, courseId)
                .orElseThrow(() -> new NotFoundException("课程章节", chapterId));
        archiveChapterContent(courseId, chapter);
        chapter.setStatus("ARCHIVED");
        chapterRepository.save(chapter);
        return Result.success();
    }

    /**
     * Keeps chapter removal safe and consistent: its content is hidden from the course together
     * with the chapter instead of leaving inaccessible resources, questions, or quizzes behind.
     */
    private void archiveChapterContent(Long courseId, CourseChapter chapter) {
        List<com.vtr.entity.CourseSection> sections = sectionRepository
                .findByCourseIdAndStatusOrderByChapterIdAscSortOrderAscIdAsc(courseId, "ACTIVE").stream()
                .filter(section -> chapter.getId().equals(section.getChapterId()))
                .collect(java.util.stream.Collectors.toList());
        sections.forEach(section -> section.setStatus("ARCHIVED"));
        if (!sections.isEmpty()) sectionRepository.saveAll(sections);
        List<Long> sectionIds = sections.stream().map(section -> section.getId()).collect(java.util.stream.Collectors.toList());

        List<com.vtr.entity.Courseware> resources = coursewareRepository.findByCourseIdAndChapter(courseId, chapter.getTitle());
        resources.forEach(item -> item.setStatus("ARCHIVED"));
        if (!resources.isEmpty()) coursewareRepository.saveAll(resources);
        List<com.vtr.entity.LearningQuestion> questions = questionRepository
                .findByCourseIdAndChapterOrderByCreatedAtDesc(courseId, chapter.getTitle());
        questions.forEach(item -> item.setStatus("ARCHIVED"));
        if (!questions.isEmpty()) questionRepository.saveAll(questions);

        for (Long sectionId : sectionIds) {
            List<ChapterQuiz> quizzes = quizRepository.findByCourseIdAndSectionIdOrderByCreatedAtDesc(courseId, sectionId);
            quizzes.forEach(quiz -> quiz.setStatus("ARCHIVED"));
            if (!quizzes.isEmpty()) quizRepository.saveAll(quizzes);
        }
        List<ChapterQuiz> chapterQuizzes = quizRepository.findByCourseIdAndChapterOrderByCreatedAtDesc(courseId, chapter.getTitle());
        chapterQuizzes.forEach(quiz -> quiz.setStatus("ARCHIVED"));
        if (!chapterQuizzes.isEmpty()) quizRepository.saveAll(chapterQuizzes);
    }

    private CourseChapterVO toVO(Long courseId, CourseChapter chapter, String title, boolean student) {
        CourseChapterVO vo = new CourseChapterVO();
        vo.setChapterId(chapter == null ? null : chapter.getId());
        vo.setCourseId(courseId);
        vo.setTitle(title);
        vo.setSubtitle(chapter == null ? null : chapter.getSubtitle());
        vo.setDescription(chapter == null ? "已汇聚该章节的教学资源，可继续学习。" : chapter.getDescription());
        vo.setSortOrder(chapter == null ? Integer.MAX_VALUE : chapter.getSortOrder());
        vo.setStatus(chapter == null ? "LEGACY" : chapter.getStatus());
        vo.setResourceCount(coursewareRepository.findByCourseIdAndChapter(courseId, title).stream()
                .filter(item -> !Set.of("DELETED", "ARCHIVED").contains(item.getStatus()) && (!student || "ACTIVE".equals(item.getStatus()))).count());
        vo.setQuestionCount(questionRepository.findByCourseIdAndChapterOrderByCreatedAtDesc(courseId, title).stream()
                .filter(item -> !Set.of("DELETED", "ARCHIVED").contains(item.getStatus()) && (!student || "PUBLISHED".equals(item.getStatus()))).count());
        return vo;
    }

    private Course requireCourse(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new NotFoundException("课程", courseId));
    }

    private Course requireManageableCourse(Long courseId) {
        Course course = requireCourse(courseId);
        if (!"ACTIVE".equals(course.getStatus())) throw new BusinessException("已归档课程不能维护章节");
        CustomUserDetails user = currentUser();
        if (user.isAdmin() || user.getId().equals(course.getCreatedBy()) || courseMemberRepository.findByCourseIdAndUserId(courseId, user.getId())
                .map(member -> "ACTIVE".equals(member.getStatus()) && Set.of("OWNER", "CO_TEACHER", "TEACHING_ASSISTANT").contains(member.getRole())).orElse(false)) {
            return course;
        }
        throw new BusinessException("无权维护该课程章节");
    }

    private void requireWorkspaceAccess(Course course, Long userId) {
        if (currentUser().isAdmin() || userId.equals(course.getCreatedBy()) || courseMemberRepository.findByCourseIdAndUserId(course.getId(), userId)
                .map(CourseMember::getStatus).map("ACTIVE"::equals).orElse(false)) return;
        throw new BusinessException("无权查看该课程");
    }

    private void requireStudentAccess(Course course, Long studentId) {
        if (!"ACTIVE".equals(course.getStatus())) throw new BusinessException("课程已归档");
        List<Long> classroomIds = classroomRepository.findByCourseIdOrderByCreatedAtDesc(course.getId()).stream()
                .filter(item -> "ACTIVE".equals(item.getStatus())).map(item -> item.getId()).toList();
        if (classroomIds.isEmpty() || !classroomStudentRelationRepository.existsByClassroomIdInAndStudentIdAndStatus(classroomIds, studentId, "ACTIVE")) {
            throw new BusinessException("您尚未加入该课程");
        }
    }

    private int nextSortOrder(Long courseId) {
        return chapterRepository.findByCourseIdAndStatusOrderBySortOrderAscIdAsc(courseId, "ACTIVE").stream()
                .map(CourseChapter::getSortOrder).filter(item -> item != null).mapToInt(Integer::intValue).max().orElse(0) + 1;
    }

    private int normalizeSortOrder(Integer value) {
        if (value == null || value < 1) throw new BusinessException("排序号必须从1开始");
        return value;
    }

    private void shiftForInsert(Long courseId, int sortOrder) {
        List<CourseChapter> chapters = chapterRepository.findByCourseIdAndStatusOrderBySortOrderAscIdAsc(courseId, "ACTIVE");
        chapters.stream().filter(item -> item.getSortOrder() != null && item.getSortOrder() >= sortOrder)
                .forEach(item -> item.setSortOrder(item.getSortOrder() + 1));
        if (!chapters.isEmpty()) chapterRepository.saveAll(chapters);
    }

    private void repositionChapter(Long courseId, Long chapterId, int oldSortOrder, int newSortOrder) {
        List<CourseChapter> chapters = chapterRepository.findByCourseIdAndStatusOrderBySortOrderAscIdAsc(courseId, "ACTIVE");
        for (CourseChapter item : chapters) {
            if (item.getId().equals(chapterId) || item.getSortOrder() == null) continue;
            if (newSortOrder < oldSortOrder && item.getSortOrder() >= newSortOrder && item.getSortOrder() < oldSortOrder) {
                item.setSortOrder(item.getSortOrder() + 1);
            } else if (newSortOrder > oldSortOrder && item.getSortOrder() > oldSortOrder && item.getSortOrder() <= newSortOrder) {
                item.setSortOrder(item.getSortOrder() - 1);
            }
        }
        if (!chapters.isEmpty()) chapterRepository.saveAll(chapters);
    }

    private String normalizeTitle(String value) {
        return value == null ? "" : value.trim();
    }

    private String canonicalTitle(String value) {
        return normalizeTitle(value).toLowerCase(java.util.Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private CustomUserDetails currentUser() {
        return (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private String role() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .findFirst().map(item -> item.getAuthority().replace("ROLE_", "")).orElse("STUDENT");
    }
}
