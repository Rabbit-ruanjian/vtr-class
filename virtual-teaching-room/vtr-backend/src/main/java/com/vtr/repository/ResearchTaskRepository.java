package com.vtr.repository;

import com.vtr.entity.ResearchTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ResearchTaskRepository extends JpaRepository<ResearchTask, Long> {
    List<ResearchTask> findByCourseIdOrderByCreatedAtDesc(Long courseId);
}
