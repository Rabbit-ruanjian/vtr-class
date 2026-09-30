package com.vtr.repository;

import com.vtr.entity.QuestionAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface QuestionAttemptRepository extends JpaRepository<QuestionAttempt, Long> {
    List<QuestionAttempt> findByStudentIdOrderBySubmittedAtDesc(Long studentId);
    List<QuestionAttempt> findByQuestionIdIn(List<Long> questionIds);
    List<QuestionAttempt> findByReviewStatusOrderBySubmittedAtAsc(String reviewStatus);
}
