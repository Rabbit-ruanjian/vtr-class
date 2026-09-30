package com.vtr.repository;

import com.vtr.entity.Submission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    /**
     * 根据作业ID查询所有提交记录（分页）
     */
    Page<Submission> findByAssignmentId(Long assignmentId, Pageable pageable);

    /**
     * 根据作业ID查询所有提交记录（不分页）
     */
    List<Submission> findByAssignmentIdOrderBySubmittedAtDesc(Long assignmentId);

    /**
     * 根据作业ID和学生ID查询提交记录
     */
    List<Submission> findByAssignmentIdAndStudentId(Long assignmentId, Long studentId);

    /**
     * 根据作业ID和学生ID统计提交次数
     */
    @Query("SELECT COUNT(s) FROM Submission s WHERE s.assignment.id = :assignmentId AND s.student.id = :studentId")
    long countByAssignmentIdAndStudentId(@Param("assignmentId") Long assignmentId, @Param("studentId") Long studentId);

    /**
     * 统计作业的提交次数
     */
    @Query("SELECT COUNT(s) FROM Submission s WHERE s.assignment.id = :assignmentId AND (:studentId IS NULL OR s.student.id = :studentId)")
    long countByAssignmentAndStudent(@Param("assignmentId") Long assignmentId, @Param("studentId") Long studentId);

    /**
     * 统计作业的不同学生数量
     */
    @Query("SELECT COUNT(DISTINCT s.student.id) FROM Submission s WHERE s.assignment.id = :assignmentId")
    Integer countDistinctStudentsByAssignmentId(@Param("assignmentId") Long assignmentId);

    /**
     * 计算作业的平均分
     */
    @Query("SELECT AVG(s.totalScore) FROM Submission s WHERE s.assignment.id = :assignmentId AND s.totalScore IS NOT NULL")
    Double calculateAverageScore(@Param("assignmentId") Long assignmentId);

    /**
     * 查询学生最近的一次提交
     */
    @Query("SELECT s FROM Submission s WHERE s.assignment.id = :assignmentId AND s.student.id = :studentId ORDER BY s.submittedAt DESC")
    List<Submission> findRecentByAssignmentAndStudent(@Param("assignmentId") Long assignmentId,
                                                      @Param("studentId") Long studentId,
                                                      Pageable pageable);

    /**
     * 根据学生ID查询提交记录（分页）
     */
    Page<Submission> findByStudentId(Long studentId, Pageable pageable);

    /**
     * 根据作业的教师ID查询提交记录（分页）
     */
    @Query("SELECT s FROM Submission s WHERE s.assignment.teacher.id = :teacherId")
    Page<Submission> findByAssignmentTeacherId(@Param("teacherId") Long teacherId, Pageable pageable);

    /**
     * 查询待评测的提交记录
     */
    @Query("SELECT s FROM Submission s WHERE s.status = 'PENDING' OR s.status = 'EVALUATING'")
    List<Submission> findPendingSubmissions();
}