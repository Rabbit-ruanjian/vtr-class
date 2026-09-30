package com.vtr.repository;

import com.vtr.entity.CourseChapter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CourseChapterRepository extends JpaRepository<CourseChapter, Long> {
    List<CourseChapter> findByCourseId(Long courseId);
    List<CourseChapter> findByCourseIdAndStatusOrderBySortOrderAscIdAsc(Long courseId, String status);
    Optional<CourseChapter> findByIdAndCourseId(Long id, Long courseId);
    Optional<CourseChapter> findByCourseIdAndTitle(Long courseId, String title);
}
