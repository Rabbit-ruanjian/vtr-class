package com.vtr.repository;

import com.vtr.entity.QuizAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuizAnswerRepository extends JpaRepository<QuizAnswer, Long> {
    List<QuizAnswer> findByAttemptIdOrderByIdAsc(Long attemptId);
    List<QuizAnswer> findByReviewStatusOrderByAnsweredAtAsc(String reviewStatus);
    Optional<QuizAnswer> findByAttemptIdAndQuestionId(Long attemptId, Long questionId);
}
