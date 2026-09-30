package com.vtr.repository;

import com.vtr.entity.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {
    boolean existsByQuizId(Long quizId);
    List<QuizAttempt> findByQuizIdAndStudentIdOrderByAttemptNumberDesc(Long quizId, Long studentId);
    List<QuizAttempt> findByStudentIdOrderBySubmittedAtDesc(Long studentId);
    Optional<QuizAttempt> findFirstByQuizIdAndStudentIdAndStatusOrderByAttemptNumberDesc(Long quizId, Long studentId, String status);
    List<QuizAttempt> findByStatusOrderBySubmittedAtAsc(String status);

    @Query("select distinct q.sectionId from ChapterQuiz q, QuizAttempt a "
            + "where q.id = a.quizId and q.courseId = :courseId and q.status = 'PUBLISHED' "
            + "and q.sectionId is not null and a.studentId = :studentId and a.status in :statuses")
    Set<Long> findCompletedSectionIds(@Param("courseId") Long courseId,
                                      @Param("studentId") Long studentId,
                                      @Param("statuses") Set<String> statuses);
}
