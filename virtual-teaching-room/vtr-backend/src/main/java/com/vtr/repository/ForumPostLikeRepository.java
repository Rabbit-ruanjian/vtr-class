package com.vtr.repository;

import com.vtr.entity.ForumPostLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ForumPostLikeRepository extends JpaRepository<ForumPostLike, Long> {
    Optional<ForumPostLike> findByPostIdAndUserId(Long postId, Long userId);
}
