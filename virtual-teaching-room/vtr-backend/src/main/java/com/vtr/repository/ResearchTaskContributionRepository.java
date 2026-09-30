package com.vtr.repository;

import com.vtr.entity.ResearchTaskContribution;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ResearchTaskContributionRepository extends JpaRepository<ResearchTaskContribution, Long> {
    Optional<ResearchTaskContribution> findByTaskIdAndContributorId(Long taskId, Long contributorId);
    List<ResearchTaskContribution> findByTaskIdOrderByClaimedAtAsc(Long taskId);
}
