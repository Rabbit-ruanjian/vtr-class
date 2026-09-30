package com.vtr.repository;

import com.vtr.entity.Notice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NoticeRepository extends JpaRepository<Notice, Long> {

    @Query("SELECT n FROM Notice n WHERE n.isDeleted = false AND " +
            "(:type IS NULL OR n.type = :type) AND " +
            "(:status IS NULL OR n.status = :status) AND " +
            "(:keyword IS NULL OR n.title LIKE %:keyword%) AND " +
            "(:schoolId IS NULL OR n.schoolId IS NULL OR n.schoolId = :schoolId)")
    Page<Notice> findByConditions(@Param("type") Notice.NoticeType type,
                                  @Param("status") Notice.NoticeStatus status,
                                  @Param("keyword") String keyword,
                                  @Param("schoolId") Long schoolId,
                                  Pageable pageable);

    @Query("SELECT n FROM Notice n WHERE n.isDeleted = false AND n.status = 'PUBLISHED' AND " +
            "(n.publishTime IS NULL OR n.publishTime <= :now) AND " +
            "(n.expireTime IS NULL OR n.expireTime > :now) AND " +
            "(n.targetUser = 'ALL' OR n.targetUser = :userType) " +
            "AND (:schoolId IS NULL OR n.schoolId IS NULL OR n.schoolId = :schoolId) " +
            "ORDER BY n.isTop DESC, n.publishTime DESC")
    List<Notice> findActiveNotices(@Param("now") LocalDateTime now,
                                   @Param("userType") Notice.TargetUser userType,
                                   @Param("schoolId") Long schoolId);

    List<Notice> findByStatusAndIsDeletedFalse(Notice.NoticeStatus status);

    @Query("SELECT n FROM Notice n WHERE n.isDeleted = false AND n.status = 'PUBLISHED' AND n.isTop = true " +
            "AND (n.expireTime IS NULL OR n.expireTime > :now) " +
            "AND (:schoolId IS NULL OR n.schoolId IS NULL OR n.schoolId = :schoolId) " +
            "ORDER BY n.publishTime DESC, n.createdAt DESC")
    List<Notice> findTopNotices(@Param("now") LocalDateTime now, @Param("schoolId") Long schoolId);

    @Modifying
    @Query("UPDATE Notice n SET n.viewCount = n.viewCount + 1 WHERE n.id = :id")
    void incrementViewCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Notice n SET n.status = 'WITHDRAWN' WHERE n.id = :id")
    void withdraw(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Notice n SET n.isDeleted = true WHERE n.id = :id")
    void softDelete(@Param("id") Long id);
}
