package com.vtr.repository;

import com.vtr.entity.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    List<TestCase> findByAssignmentIdOrderBySortOrderAsc(Long assignmentId);

    List<TestCase> findByAssignmentIdAndIsPublicTrueOrderBySortOrderAsc(Long assignmentId);

    @Query("SELECT t FROM TestCase t WHERE t.assignment.id = :assignmentId ORDER BY t.sortOrder ASC")
    List<TestCase> findByAssignmentId(@Param("assignmentId") Long assignmentId);

    @Modifying
    @Query("DELETE FROM TestCase t WHERE t.assignment.id = :assignmentId")
    void deleteByAssignmentId(@Param("assignmentId") Long assignmentId);

    long countByAssignmentId(Long assignmentId);

    long countByAssignmentIdAndIsPublicTrue(Long assignmentId);
}