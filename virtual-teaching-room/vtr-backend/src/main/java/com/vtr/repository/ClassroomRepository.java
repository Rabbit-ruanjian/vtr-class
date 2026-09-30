package com.vtr.repository;

import com.vtr.entity.Classroom;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface ClassroomRepository extends JpaRepository<Classroom, Long> {

    // 获取教师的班级列表（按状态）
    Page<Classroom> findByTeacherIdAndStatus(Long teacherId, String status, Pageable pageable);

    List<Classroom> findByTeacherIdAndStatus(Long teacherId, String status);

    List<Classroom> findByCourseIdOrderByCreatedAtDesc(Long courseId);

    long countByCourseIdAndStatus(Long courseId, String status);

    // 根据ID、教师ID和状态查询
    Optional<Classroom> findByIdAndTeacherIdAndStatus(Long id, Long teacherId, String status);

    // 根据ID和教师ID查询（不限状态）
    Optional<Classroom> findByIdAndTeacherId(Long id, Long teacherId);

    Optional<Classroom> findByInviteCodeAndStatus(String inviteCode, String status);

    boolean existsByInviteCode(String inviteCode);

    // 教师搜索班级（按状态）
    @Query("SELECT c FROM Classroom c WHERE c.teacherId = :teacherId AND c.status = :status " +
            "AND (c.className LIKE %:keyword% OR c.grade LIKE %:keyword%)")
    Page<Classroom> searchByKeywordAndStatus(@Param("teacherId") Long teacherId,
                                             @Param("keyword") String keyword,
                                             @Param("status") String status,
                                             Pageable pageable);

    // 管理员搜索班级（所有教师的班级）
    @Query("SELECT c FROM Classroom c WHERE (c.className LIKE %:keyword% OR c.grade LIKE %:keyword%)")
    Page<Classroom> searchByKeywordForAdmin(@Param("keyword") String keyword, Pageable pageable);

    // 管理员按状态搜索班级
    @Query("SELECT c FROM Classroom c WHERE c.status = :status AND (c.className LIKE %:keyword% OR c.grade LIKE %:keyword%)")
    Page<Classroom> searchByKeywordAndStatusForAdmin(@Param("keyword") String keyword,
                                                     @Param("status") String status,
                                                     Pageable pageable);

    // 根据状态查询所有班级（不分教师）
    Page<Classroom> findByStatus(String status, Pageable pageable);

    // 统计教师的班级数
    long countByTeacherIdAndStatus(Long teacherId, String status);

    // 更新班级学生数
    @Modifying
    @Transactional
    @Query("UPDATE Classroom c SET c.studentCount = " +
            "(SELECT COUNT(csr) FROM ClassroomStudentRelation csr WHERE csr.classroomId = c.id AND csr.status = 'ACTIVE') " +
            "WHERE c.id = :classroomId")
    void updateStudentCount(@Param("classroomId") Long classroomId);
}
