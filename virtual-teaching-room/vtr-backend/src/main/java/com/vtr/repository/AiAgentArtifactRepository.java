package com.vtr.repository;

import com.vtr.entity.AiAgentArtifact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiAgentArtifactRepository extends JpaRepository<AiAgentArtifact, Long> {
    List<AiAgentArtifact> findByCourseIdOrderByCreatedAtDesc(Long courseId);
}
