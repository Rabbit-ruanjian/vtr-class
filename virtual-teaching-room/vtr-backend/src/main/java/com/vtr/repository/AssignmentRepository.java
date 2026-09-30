package com.vtr.repository;

import com.vtr.entity.Assignment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    // ========== 基础查询（@Where 注解会自动过滤 is_deleted = false） ==========

    // 教师的所有作业
    Page<Assignment> findByTeacherId(Long teacherId, Pageable pageable);

    Page<Assignment> findByTeacherIdAndCourseId(Long teacherId, Long courseId, Pageable pageable);

    Page<Assignment> findByCourseId(Long courseId, Pageable pageable);

    long countByCourseId(Long courseId);

    // 教师特定状态的作业
    Page<Assignment> findByTeacherIdAndStatus(Long teacherId, Assignment.AssignmentStatus status, Pageable pageable);

    // 特定状态的作业
    Page<Assignment> findByStatus(Assignment.AssignmentStatus status, Pageable pageable);

    // 所有已发布的作业
    List<Assignment> findByStatus(Assignment.AssignmentStatus status);

    // 当前课程已发布的作业
    List<Assignment> findByCourseIdAndStatus(Long courseId, Assignment.AssignmentStatus status);

    // 活跃作业（未截止且已发布）- 不需要加 is_deleted 条件，因为 @Where 会自动处理
    @Query("SELECT a FROM Assignment a WHERE a.deadline > :now AND a.status = 'PUBLISHED'")
    List<Assignment> findActiveAssignments(@Param("now") LocalDateTime now);

    // 软删除作业
    @Modifying
    @Transactional
    @Query("UPDATE Assignment a SET a.isDeleted = true WHERE a.id = :id")
    void softDelete(@Param("id") Long id);

    // ========== 学生可见作业查询（通过班级关联，不使用 TeacherStudentRelation） ==========
    @Query("SELECT DISTINCT a FROM Assignment a " +
            "WHERE a.status = 'PUBLISHED' " +
            "AND (" +
            "   (a.publishType = 'ALL' OR a.publishType IS NULL) " +
            "   AND EXISTS (SELECT 1 FROM Classroom c " +
            "               JOIN ClassroomStudentRelation csr ON csr.classroomId = c.id " +
            "               WHERE ((a.courseId IS NULL AND c.teacherId = a.teacher.id) " +
            "                   OR (a.courseId IS NOT NULL AND c.courseId = a.courseId)) " +
            "               AND c.status = 'ACTIVE' AND csr.studentId = :studentId AND csr.status = 'ACTIVE')" +
            "   OR " +
            "   (a.publishType = 'SELECTED' AND EXISTS (SELECT 1 FROM AssignmentStudentVisibility v WHERE v.assignment.id = a.id AND v.student.id = :studentId))" +
            ") " +
            "AND (a.courseId IS NULL OR EXISTS (SELECT 1 FROM Course c2 WHERE c2.id = a.courseId AND c2.status = 'ACTIVE')) " +
            "AND (a.courseId IS NULL OR EXISTS (SELECT 1 FROM Classroom c3 " +
            "             JOIN ClassroomStudentRelation csr3 ON csr3.classroomId = c3.id " +
            "             WHERE c3.courseId = a.courseId AND c3.status = 'ACTIVE' " +
            "             AND csr3.studentId = :studentId AND csr3.status = 'ACTIVE')) " +
            "ORDER BY a.deadline ASC")
    Page<Assignment> findVisibleAssignmentsForStudent(@Param("studentId") Long studentId, Pageable pageable);

    @Query("SELECT DISTINCT a FROM Assignment a " +
            "WHERE a.courseId = :courseId AND a.status = 'PUBLISHED' " +
            "AND EXISTS (SELECT 1 FROM Course c0 WHERE c0.id = a.courseId AND c0.status = 'ACTIVE') " +
            "AND ((a.publishType = 'ALL' OR a.publishType IS NULL) " +
            "AND EXISTS (SELECT 1 FROM Classroom c JOIN ClassroomStudentRelation csr ON csr.classroomId = c.id " +
            "WHERE c.courseId = a.courseId AND c.status = 'ACTIVE' " +
            "AND csr.studentId = :studentId AND csr.status = 'ACTIVE') " +
            "OR (a.publishType = 'SELECTED' AND EXISTS (SELECT 1 FROM AssignmentStudentVisibility v " +
            "WHERE v.assignment.id = a.id AND v.student.id = :studentId))) " +
            "AND EXISTS (SELECT 1 FROM Classroom c3 JOIN ClassroomStudentRelation csr3 ON csr3.classroomId = c3.id " +
            "WHERE c3.courseId = a.courseId AND c3.status = 'ACTIVE' " +
            "AND csr3.studentId = :studentId AND csr3.status = 'ACTIVE') " +
            "ORDER BY a.deadline ASC")
    Page<Assignment> findVisibleAssignmentsForStudentAndCourse(@Param("studentId") Long studentId, @Param("courseId") Long courseId, Pageable pageable);

    // ========== 删除用户时使用 ==========

    /**
     * 关闭指定教师的所有已发布作业（不删除，只是关闭）
     * @param teacherId 教师ID
     * @return 被关闭的作业数量
     */
    @Modifying
    @Transactional
    @Query("UPDATE Assignment a SET a.status = 'CLOSED' WHERE a.teacher.id = :teacherId AND a.status = 'PUBLISHED'")
    int closeByTeacherId(@Param("teacherId") Long teacherId);
}
