package com.vtr.repository;

import com.vtr.entity.ChapterQuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChapterQuizQuestionRepository extends JpaRepository<ChapterQuizQuestion, Long> {
    List<ChapterQuizQuestion> findByQuizIdOrderBySortOrderAsc(Long quizId);
    void deleteByQuizId(Long quizId);
}
