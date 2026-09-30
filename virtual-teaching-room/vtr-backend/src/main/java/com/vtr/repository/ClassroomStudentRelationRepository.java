package com.vtr.repository;

import com.vtr.entity.ClassroomStudentRelation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface ClassroomStudentRelationRepository extends JpaRepository<ClassroomStudentRelation, Long> {

    Page<ClassroomStudentRelation> findByClassroomIdAndStatus(Long classroomId, String status, Pageable pageable);

    List<ClassroomStudentRelation> findByClassroomIdAndStatus(Long classroomId, String status);

    List<ClassroomStudentRelation> findByStudentIdAndStatus(Long studentId, String status);

    // 根据班级ID列表查询
    List<ClassroomStudentRelation> findByClassroomIdInAndStatus(List<Long> classroomIds, String status);

    boolean existsByClassroomIdAndStudentIdAndStatus(Long classroomId, Long studentId, String status);

    // 根据班级ID列表和学生ID查询是否存在
    @Query("SELECT CASE WHEN COUNT(csr) > 0 THEN true ELSE false END FROM ClassroomStudentRelation csr " +
            "WHERE csr.classroomId IN :classroomIds AND csr.studentId = :studentId AND csr.status = :status")
    boolean existsByClassroomIdInAndStudentIdAndStatus(@Param("classroomIds") List<Long> classroomIds,
                                                       @Param("studentId") Long studentId,
                                                       @Param("status") String status);

    /** 学生是否曾经加入过某门课程，课程或班级归档后仍保留历史关系。 */
    @Query("SELECT CASE WHEN COUNT(csr) > 0 THEN true ELSE false END " +
            "FROM ClassroomStudentRelation csr JOIN Classroom c ON c.id = csr.classroomId " +
            "WHERE c.courseId = :courseId AND csr.studentId = :studentId")
    boolean existsByStudentIdAndCourseId(@Param("studentId") Long studentId,
                                         @Param("courseId") Long courseId);

    Optional<ClassroomStudentRelation> findByClassroomIdAndStudentIdAndStatus(Long classroomId, Long studentId, String status);

    @Query("SELECT csr FROM ClassroomStudentRelation csr WHERE csr.studentId = :studentId AND csr.status = 'ACTIVE' " +
            "AND csr.classroomId IN (SELECT c.id FROM Classroom c WHERE c.teacherId = :teacherId)")
    Optional<ClassroomStudentRelation> findByStudentIdAndTeacherId(@Param("studentId") Long studentId,
                                                                   @Param("teacherId") Long teacherId);

    @Modifying
    @Transactional
    @Query("UPDATE ClassroomStudentRelation csr SET csr.status = 'REMOVED', csr.removedAt = CURRENT_TIMESTAMP " +
            "WHERE csr.classroomId = :classroomId AND csr.studentId = :studentId")
    int removeStudentFromClassroom(@Param("classroomId") Long classroomId, @Param("studentId") Long studentId);

    @Modifying
    @Transactional
    @Query("UPDATE ClassroomStudentRelation csr SET csr.status = 'REMOVED', csr.removedAt = CURRENT_TIMESTAMP " +
            "WHERE csr.classroomId = :classroomId")
    int removeAllStudentsFromClassroom(@Param("classroomId") Long classroomId);

    @Modifying
    @Transactional
    @Query("UPDATE ClassroomStudentRelation csr SET csr.status = 'REMOVED', csr.removedAt = CURRENT_TIMESTAMP " +
            "WHERE csr.studentId = :studentId")
    int removeAllByStudentId(@Param("studentId") Long studentId);

    long countByClassroomIdAndStatus(Long classroomId, String status);

    @Query("SELECT COUNT(DISTINCT csr.studentId) FROM ClassroomStudentRelation csr " +
            "WHERE csr.classroomId IN :classroomIds AND csr.status = :status")
    long countDistinctStudentsByClassroomIdsAndStatus(@Param("classroomIds") List<Long> classroomIds,
                                                       @Param("status") String status);

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO classroom_student_relation (classroom_id, student_id, student_number, status, created_at) " +
            "VALUES (:classroomId, :studentId, :studentNumber, 'ACTIVE', NOW()) " +
            "ON DUPLICATE KEY UPDATE status = 'ACTIVE', removed_at = NULL",
            nativeQuery = true)
    int insertOrReactivate(@Param("classroomId") Long classroomId,
                           @Param("studentId") Long studentId,
                           @Param("studentNumber") String studentNumber);
}
