package com.vtr.repository;

import com.vtr.entity.VideoLearningProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VideoLearningProgressRepository extends JpaRepository<VideoLearningProgress, Long> {
    Optional<VideoLearningProgress> findByCoursewareIdAndStudentId(Long coursewareId, Long studentId);
    long countByCoursewareId(Long coursewareId);
    long countByCoursewareIdAndCompletedTrue(Long coursewareId);
}
