package com.vtr.repository;

import com.vtr.entity.ChapterQuiz;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChapterQuizRepository extends JpaRepository<ChapterQuiz, Long> {
    List<ChapterQuiz> findByCourseIdAndSectionIdOrderByCreatedAtDesc(Long courseId, Long sectionId);
    List<ChapterQuiz> findByCourseIdAndSectionIdAndStatusOrderByCreatedAtDesc(Long courseId, Long sectionId, String status);
    List<ChapterQuiz> findByCourseIdAndChapterOrderByCreatedAtDesc(Long courseId, String chapter);
    List<ChapterQuiz> findByCourseIdAndAssessmentTypeOrderByCreatedAtDesc(Long courseId, String assessmentType);
}
