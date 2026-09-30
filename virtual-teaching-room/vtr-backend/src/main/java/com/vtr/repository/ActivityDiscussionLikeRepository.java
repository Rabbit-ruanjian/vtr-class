package com.vtr.repository;

import com.vtr.entity.ActivityDiscussionLike;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityDiscussionLikeRepository extends JpaRepository<ActivityDiscussionLike, Long> {
    boolean existsByDiscussionIdAndTeacherId(Long discussionId, Long teacherId);
}
