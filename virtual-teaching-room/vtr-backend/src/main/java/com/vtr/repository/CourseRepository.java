package com.vtr.repository;
import com.vtr.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findByStatusOrderByCourseNameAsc(String status);

    long countByStatus(String status);

    List<Course> findBySchoolIdAndStatusOrderByCourseNameAsc(Long schoolId, String status);

    List<Course> findByCreatedByAndStatusOrderByCourseNameAsc(Long createdBy, String status);

    List<Course> findByCreatedByOrderByCourseNameAsc(Long createdBy);

    List<Course> findAllByOrderByCourseNameAsc();

    List<Course> findBySchoolIdOrderByCourseNameAsc(Long schoolId);

    boolean existsByCourseCode(String courseCode);

    boolean existsByCourseCodeAndIdNot(String courseCode, Long id);

    Optional<Course> findByCourseCode(String courseCode);
}
