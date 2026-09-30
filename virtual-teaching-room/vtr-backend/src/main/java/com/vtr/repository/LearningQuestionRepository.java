package com.vtr.repository;

import com.vtr.entity.LearningQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface LearningQuestionRepository extends JpaRepository<LearningQuestion, Long> {
    List<LearningQuestion> findByCourseIdOrderByCreatedAtDesc(Long courseId);
    List<LearningQuestion> findByCourseIdAndChapterOrderByCreatedAtDesc(Long courseId, String chapter);
    List<LearningQuestion> findByCourseIdAndSectionIdOrderByCreatedAtDesc(Long courseId, Long sectionId);

    @Query("SELECT DISTINCT q.chapter FROM LearningQuestion q WHERE q.courseId = :courseId " +
            "AND q.chapter IS NOT NULL AND TRIM(q.chapter) <> '' " +
            "AND q.status NOT IN ('ARCHIVED', 'DELETED')")
    List<String> findDistinctChapterNames(@Param("courseId") Long courseId);

    @Query("SELECT DISTINCT q.chapter FROM LearningQuestion q WHERE q.courseId = :courseId " +
            "AND q.status = 'PUBLISHED' AND q.chapter IS NOT NULL AND TRIM(q.chapter) <> ''")
    List<String> findDistinctPublishedChapterNames(@Param("courseId") Long courseId);
}
