package com.vtr.repository;

import com.vtr.entity.ForumPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Repository
public interface ForumPostRepository extends JpaRepository<ForumPost, Long> {

    // ========== 原有方法 ==========
    Page<ForumPost> findAllByOrderByPinnedDescCreateTimeDesc(Pageable pageable);

    Page<ForumPost> findAllByOrderByCreateTimeDesc(Pageable pageable);

    @Modifying
    @Transactional
    @Query("UPDATE ForumPost p SET p.replyCount = p.replyCount + 1 WHERE p.id = :postId")
    void incrementReplyCount(@Param("postId") Long postId);

    @Modifying
    @Transactional
    @Query("UPDATE ForumPost p SET p.replyCount = p.replyCount - 1 WHERE p.id = :postId AND p.replyCount > 0")
    void decrementReplyCount(@Param("postId") Long postId);

    @Modifying
    @Transactional
    @Query("UPDATE ForumPost p SET p.views = p.views + 1 WHERE p.id = :postId")
    void incrementViews(@Param("postId") Long postId);

    // ========== 新增审核相关方法 ==========

    /**
     * 查询已审核通过的帖子（前台展示用）- 按置顶优先、创建时间排序
     */
    @Query("SELECT p FROM ForumPost p WHERE p.auditStatus = 'APPROVED' ORDER BY p.pinned DESC, p.createTime DESC")
    Page<ForumPost> findApprovedPosts(Pageable pageable);

    @Query("SELECT p FROM ForumPost p WHERE p.auditStatus = 'APPROVED' " +
            "AND (:allSchools = true OR (:schoolId IS NULL AND p.schoolId IS NULL) OR p.schoolId = :schoolId) " +
            "AND ((:audience = 'ADMIN') " +
            "OR (:audience = 'PUBLIC' AND coalesce(nullif(p.audience, ''), 'ALL') = 'ALL') " +
            "OR coalesce(nullif(p.audience, ''), 'ALL') = 'ALL' OR p.audience = :audience) " +
            "AND (:courseId IS NULL OR p.courseId = :courseId) " +
            "AND (:postType IS NULL OR :postType = '' OR p.postType = :postType) " +
            "AND (:keyword IS NULL OR :keyword = '' OR lower(p.title) like lower(concat('%', :keyword, '%')) " +
            "OR lower(p.content) like lower(concat('%', :keyword, '%')) " +
            "OR lower(coalesce(p.courseName, '')) like lower(concat('%', :keyword, '%'))) " +
            "AND (:unresolved = false OR coalesce(p.solved, false) = false) " +
            "ORDER BY p.pinned DESC, p.createTime DESC")
    Page<ForumPost> findApprovedPosts(@Param("audience") String audience,
                                      @Param("schoolId") Long schoolId,
                                      @Param("allSchools") boolean allSchools,
                                      @Param("courseId") Long courseId,
                                      @Param("postType") String postType,
                                      @Param("keyword") String keyword,
                                      @Param("unresolved") boolean unresolved,
                                      Pageable pageable);

    /**
     * 查询待审核的帖子（管理员用）
     */
    @Query("SELECT p FROM ForumPost p WHERE p.auditStatus = 'PENDING' ORDER BY p.createTime ASC")
    Page<ForumPost> findPendingPosts(Pageable pageable);

    /**
     * 按审核状态查询
     */
    Page<ForumPost> findByAuditStatusOrderByCreateTimeDesc(String auditStatus, Pageable pageable);

    Page<ForumPost> findByAuditStatusAndSchoolIdOrderByCreateTimeDesc(String auditStatus, Long schoolId, Pageable pageable);

    Page<ForumPost> findByAuditStatusAndSchoolIdIsNullOrderByCreateTimeDesc(String auditStatus, Pageable pageable);

    Page<ForumPost> findBySchoolIdOrderByCreateTimeDesc(Long schoolId, Pageable pageable);

    Page<ForumPost> findBySchoolIdIsNullOrderByCreateTimeDesc(Pageable pageable);

    /**
     * 清除过期的置顶
     */
    @Modifying
    @Transactional
    @Query("UPDATE ForumPost p SET p.pinned = false, p.pinnedExpiry = null WHERE p.pinned = true AND p.pinnedExpiry < :now")
    int clearExpiredPins(@Param("now") LocalDateTime now);
}
