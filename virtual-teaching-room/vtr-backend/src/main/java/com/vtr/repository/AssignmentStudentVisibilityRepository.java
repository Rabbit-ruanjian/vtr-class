package com.vtr.repository;

import com.vtr.entity.Assignment;
import com.vtr.entity.AssignmentStudentVisibility;
import com.vtr.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface AssignmentStudentVisibilityRepository extends JpaRepository<AssignmentStudentVisibility, Long> {

    // 获取作业可见的学生列表
    List<AssignmentStudentVisibility> findByAssignmentId(Long assignmentId);

    Page<AssignmentStudentVisibility> findByAssignmentId(Long assignmentId, Pageable pageable);

    // 获取学生可见的作业ID列表
    @Query("SELECT a.assignment.id FROM AssignmentStudentVisibility a WHERE a.student.id = :studentId")
    List<Long> findAssignmentIdsByStudentId(@Param("studentId") Long studentId);

    // 检查学生是否可见该作业
    boolean existsByAssignmentIdAndStudentId(Long assignmentId, Long studentId);

    // 删除作业的所有可见性记录
    @Modifying
    @Transactional
    void deleteByAssignmentId(Long assignmentId);

    // 批量添加可见性
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO assignment_student_visibility (assignment_id, student_id, created_at) VALUES (:assignmentId, :studentId, NOW())", nativeQuery = true)
    void batchInsertVisibility(@Param("assignmentId") Long assignmentId, @Param("studentId") Long studentId);
}