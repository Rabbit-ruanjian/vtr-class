package com.vtr.repository;

import com.vtr.entity.CourseMember;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CourseMemberRepository extends JpaRepository<CourseMember, Long> {
    List<CourseMember> findByCourseIdAndStatusOrderByJoinedAtAsc(Long courseId, String status);
    List<CourseMember> findByUserIdAndStatus(Long userId, String status);
    Optional<CourseMember> findByCourseIdAndUserId(Long courseId, Long userId);
    long countByCourseIdAndStatus(Long courseId, String status);
}
