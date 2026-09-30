package com.vtr.repository;
import com.vtr.entity.AcademicClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
public interface AcademicClassRepository extends JpaRepository<AcademicClass, Long> {
    boolean existsBySchoolIdAndClassCode(Long schoolId, String classCode);
    boolean existsBySchoolIdAndClassCodeAndIdNot(Long schoolId, String classCode, Long id);
    List<AcademicClass> findBySchoolIdAndStatusOrderByGradeDescMajorAscClassCodeAsc(Long schoolId, String status);
    List<AcademicClass> findByStatusOrderByGradeDescMajorAscClassCodeAsc(String status);
    @Query("SELECT a FROM AcademicClass a WHERE a.status = :status "
            + "AND (:college IS NULL OR a.college = :college) "
            + "AND (:grade IS NULL OR a.grade = :grade) "
            + "AND (:major IS NULL OR a.major = :major) "
            + "AND (:keyword IS NULL OR LOWER(a.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(a.college) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(a.major) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(a.grade) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(a.classCode) LIKE LOWER(CONCAT('%', :keyword, '%'))) ")
    Page<AcademicClass> search(@Param("status") String status, @Param("keyword") String keyword, @Param("college") String college, @Param("grade") String grade, @Param("major") String major, Pageable pageable);
    @Query("SELECT a FROM AcademicClass a WHERE a.schoolId = :schoolId AND a.status = :status "
            + "AND (:college IS NULL OR a.college = :college) "
            + "AND (:grade IS NULL OR a.grade = :grade) "
            + "AND (:major IS NULL OR a.major = :major) "
            + "AND (:keyword IS NULL OR LOWER(a.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(a.college) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(a.major) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(a.grade) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(a.classCode) LIKE LOWER(CONCAT('%', :keyword, '%'))) ")
    Page<AcademicClass> searchBySchool(@Param("schoolId") Long schoolId, @Param("status") String status, @Param("keyword") String keyword, @Param("college") String college, @Param("grade") String grade, @Param("major") String major, Pageable pageable);

    // 学院/年级聚合统计：classCount 为该分组行政班数量，studentCount 统计名单中有效学生人数。
    @Query("SELECT a.college AS college, a.grade AS grade, COUNT(DISTINCT a.id) AS classCount, "
            + "SUM((SELECT COUNT(r.id) FROM AcademicClassStudentRoster r WHERE r.academicClassId = a.id AND r.status = 'ACTIVE')) AS studentCount "
            + "FROM AcademicClass a WHERE a.status = :status "
            + "GROUP BY a.college, a.grade")
    List<CollegeGradeCount> summarize(@Param("status") String status);

    @Query("SELECT a.college AS college, a.grade AS grade, COUNT(DISTINCT a.id) AS classCount, "
            + "SUM((SELECT COUNT(r.id) FROM AcademicClassStudentRoster r WHERE r.academicClassId = a.id AND r.status = 'ACTIVE')) AS studentCount "
            + "FROM AcademicClass a WHERE a.schoolId = :schoolId AND a.status = :status "
            + "GROUP BY a.college, a.grade")
    List<CollegeGradeCount> summarizeBySchool(@Param("schoolId") Long schoolId, @Param("status") String status);

    interface CollegeGradeCount {
        String getCollege();
        String getGrade();
        long getClassCount();
        Long getStudentCount();
    }
}
