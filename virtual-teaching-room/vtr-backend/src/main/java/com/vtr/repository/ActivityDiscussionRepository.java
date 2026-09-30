package com.vtr.repository;

import com.vtr.entity.ActivityDiscussion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface ActivityDiscussionRepository extends JpaRepository<ActivityDiscussion, Long> {

    // 获取活动的讨论（顶级评论）
    Page<ActivityDiscussion> findByActivityIdAndParentIdAndIsDeletedFalse(Long activityId, Long parentId, Pageable pageable);

    // 获取活动的所有讨论
    List<ActivityDiscussion> findByActivityIdAndIsDeletedFalseOrderByCreatedAtDesc(Long activityId);

    // 获取子评论
    List<ActivityDiscussion> findByParentIdAndIsDeletedFalseOrderByCreatedAtAsc(Long parentId);

    Optional<ActivityDiscussion> findByIdAndIsDeletedFalse(Long id);

    // 统计活动讨论数
    long countByActivityIdAndIsDeletedFalse(Long activityId);

    // 软删除评论
    @Modifying
    @Transactional
    @Query("UPDATE ActivityDiscussion d SET d.isDeleted = true WHERE d.id = :id")
    void softDelete(@Param("id") Long id);

    // 增加点赞数
    @Modifying
    @Transactional
    @Query("UPDATE ActivityDiscussion d SET d.likeCount = d.likeCount + 1 WHERE d.id = :id AND d.isDeleted = false")
    int incrementLikeCount(@Param("id") Long id);
}
