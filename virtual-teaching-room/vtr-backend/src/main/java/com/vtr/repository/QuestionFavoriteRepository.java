package com.vtr.repository;

import com.vtr.entity.QuestionFavorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuestionFavoriteRepository extends JpaRepository<QuestionFavorite, Long> {
    Optional<QuestionFavorite> findByQuestionIdAndStudentId(Long questionId, Long studentId);
    List<QuestionFavorite> findByStudentIdOrderByCreatedAtDesc(Long studentId);
}
