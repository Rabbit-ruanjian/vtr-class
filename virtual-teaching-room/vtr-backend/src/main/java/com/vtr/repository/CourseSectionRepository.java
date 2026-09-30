package com.vtr.repository;

import com.vtr.entity.CourseSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CourseSectionRepository extends JpaRepository<CourseSection, Long> {
    List<CourseSection> findByCourseIdAndStatusOrderByChapterIdAscSortOrderAscIdAsc(Long courseId, String status);
    Optional<CourseSection> findByIdAndCourseId(Long id, Long courseId);
    Optional<CourseSection> findByIdAndChapterIdAndCourseId(Long id, Long chapterId, Long courseId);
}
