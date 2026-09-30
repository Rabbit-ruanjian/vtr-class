package com.vtr.repository;

import com.vtr.entity.ActivityOutcome;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ActivityOutcomeRepository extends JpaRepository<ActivityOutcome, Long> {
    List<ActivityOutcome> findByActivityIdOrderByCreatedAtDesc(Long activityId);
    long countByActivityIdAndStatus(Long activityId, String status);
}
