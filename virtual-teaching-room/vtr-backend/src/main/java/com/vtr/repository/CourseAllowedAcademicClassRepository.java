package com.vtr.repository;

import com.vtr.entity.CourseAllowedAcademicClass;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseAllowedAcademicClassRepository extends JpaRepository<CourseAllowedAcademicClass, Long> {
    List<CourseAllowedAcademicClass> findByCourseIdOrderByAcademicClassIdAsc(Long courseId);
    void deleteByCourseId(Long courseId);
}
