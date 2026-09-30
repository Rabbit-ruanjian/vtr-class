package com.vtr.repository;

import com.vtr.entity.ForumComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface ForumCommentRepository extends JpaRepository<ForumComment, Long> {

    // 查询已审核通过的评论（前台展示用）
    List<ForumComment> findByPostIdAndAuditStatusOrderByCreateTimeAsc(Long postId, String auditStatus);

    // 前台展示所有未被明确拒绝的评论，兼容历史待审核数据
    List<ForumComment> findByPostIdAndAuditStatusNotOrderByCreateTimeAsc(Long postId, String auditStatus);

    // 查询所有评论（管理员用）
    List<ForumComment> findByPostIdOrderByCreateTimeAsc(Long postId);

    // 查询待审核的评论（分页）
    Page<ForumComment> findByAuditStatusOrderByCreateTimeAsc(String auditStatus, Pageable pageable);

    Page<ForumComment> findByAuditStatusAndSchoolIdOrderByCreateTimeAsc(String auditStatus, Long schoolId, Pageable pageable);

    Page<ForumComment> findByAuditStatusAndSchoolIdIsNullOrderByCreateTimeAsc(String auditStatus, Pageable pageable);

    Page<ForumComment> findBySchoolIdOrderByCreateTimeDesc(Long schoolId, Pageable pageable);

    Page<ForumComment> findBySchoolIdIsNullOrderByCreateTimeDesc(Pageable pageable);

    Page<ForumComment> findAllByOrderByCreateTimeDesc(Pageable pageable);

    @Modifying
    @Transactional
    @Query("DELETE FROM ForumComment c WHERE c.postId = :postId")
    void deleteByPostId(@Param("postId") Long postId);

}
