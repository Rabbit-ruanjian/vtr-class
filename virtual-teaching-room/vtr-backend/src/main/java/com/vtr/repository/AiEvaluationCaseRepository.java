package com.vtr.repository;

import com.vtr.entity.AiEvaluationCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiEvaluationCaseRepository extends JpaRepository<AiEvaluationCase, Long> {
    List<AiEvaluationCase> findByCourseIdOrderByCreatedAtDesc(Long courseId);
    List<AiEvaluationCase> findByCourseIdAndEnabledTrueOrderByCreatedAtAsc(Long courseId);
}
