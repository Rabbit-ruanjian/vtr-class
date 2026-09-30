package com.vtr.repository;

import com.vtr.entity.TeachingActivity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface TeachingActivityRepository extends JpaRepository<TeachingActivity, Long> {

    // 分页查询（自动过滤已删除）
    Page<TeachingActivity> findByIsDeletedFalse(Pageable pageable);

    java.util.Optional<TeachingActivity> findByIdAndIsDeletedFalse(Long id);

    java.util.Optional<TeachingActivity> findByIdAndIsDeletedFalseAndStatus(Long id, String status);

    // 按状态查询
    Page<TeachingActivity> findByStatusAndIsDeletedFalse(String status, Pageable pageable);

    long countByStatusAndIsDeletedFalse(String status);

    // 按类型查询
    Page<TeachingActivity> findByTypeAndIsDeletedFalse(String type, Pageable pageable);

    // 按组织者查询
    Page<TeachingActivity> findByOrganizerIdAndIsDeletedFalse(Long organizerId, Pageable pageable);

    Page<TeachingActivity> findByCourseIdAndIsDeletedFalse(Long courseId, Pageable pageable);

    long countByCourseIdAndIsDeletedFalse(Long courseId);

    // 按ID列表查询（分页）- 新增
    Page<TeachingActivity> findByIdInAndIsDeletedFalse(List<Long> ids, Pageable pageable);

    Page<TeachingActivity> findByIdInAndCourseIdAndIsDeletedFalse(List<Long> ids, Long courseId, Pageable pageable);

    Page<TeachingActivity> findByIdInAndSchoolIdAndIsDeletedFalse(List<Long> ids, Long schoolId, Pageable pageable);

    Page<TeachingActivity> findByIdInAndCourseIdAndSchoolIdAndIsDeletedFalse(List<Long> ids, Long courseId, Long schoolId, Pageable pageable);

    @Query("SELECT a FROM TeachingActivity a WHERE a.isDeleted = false " +
            "AND (:organizerId IS NULL OR a.organizerId = :organizerId) " +
            "AND (:courseId IS NULL OR a.courseId = :courseId) " +
            "AND (:classroomId IS NULL OR a.classroomId = :classroomId) " +
            "AND (:status IS NULL OR a.status = :status) " +
            "AND (:type IS NULL OR a.type = :type) " +
            "AND (:keyword IS NULL OR a.title LIKE %:keyword% OR a.content LIKE %:keyword% OR a.location LIKE %:keyword%) " +
            "AND (:schoolLevelOnly = false OR (a.courseId IS NULL AND a.classroomId IS NULL)) " +
            "AND (:schoolId IS NULL OR a.schoolId = :schoolId)")
    Page<TeachingActivity> findByFilters(@Param("organizerId") Long organizerId,
                                         @Param("courseId") Long courseId,
                                         @Param("classroomId") Long classroomId,
                                         @Param("status") String status,
                                         @Param("type") String type,
                                         @Param("keyword") String keyword,
                                         @Param("schoolLevelOnly") boolean schoolLevelOnly,
                                         @Param("schoolId") Long schoolId,
                                         Pageable pageable);

    /**
     * 活动大厅查询：教师可以查看本校所有已发布、进行中或已结束的活动，
     * 但不展示其他教师尚未审核或已驳回的草稿。
     */
    @Query("SELECT a FROM TeachingActivity a WHERE a.isDeleted = false " +
            "AND a.status NOT IN ('PENDING', 'REJECTED') " +
            "AND (:courseId IS NULL OR a.courseId = :courseId) " +
            "AND (:classroomId IS NULL OR a.classroomId = :classroomId) " +
            "AND (:type IS NULL OR a.type = :type) " +
            "AND (:keyword IS NULL OR a.title LIKE %:keyword% OR a.content LIKE %:keyword% OR a.location LIKE %:keyword%) " +
            "AND (:schoolId IS NULL OR a.schoolId = :schoolId)")
    Page<TeachingActivity> findForActivityHall(@Param("courseId") Long courseId,
                                                @Param("classroomId") Long classroomId,
                                                @Param("type") String type,
                                                @Param("keyword") String keyword,
                                                @Param("schoolId") Long schoolId,
                                                Pageable pageable);

    // 关键字搜索
    @Query("SELECT a FROM TeachingActivity a WHERE a.isDeleted = false AND " +
            "(a.title LIKE %:keyword% OR a.content LIKE %:keyword% OR a.location LIKE %:keyword%)")
    Page<TeachingActivity> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    // 组合查询
    @Query("SELECT a FROM TeachingActivity a WHERE a.isDeleted = false " +
            "AND (:status IS NULL OR a.status = :status) " +
            "AND (:type IS NULL OR a.type = :type) " +
            "AND (:keyword IS NULL OR a.title LIKE %:keyword% OR a.content LIKE %:keyword%)")
    Page<TeachingActivity> findByConditions(@Param("status") String status,
                                            @Param("type") String type,
                                            @Param("keyword") String keyword,
                                            Pageable pageable);

    // 获取即将开始的活动
    @Query("SELECT a FROM TeachingActivity a WHERE a.isDeleted = false " +
            "AND a.status = 'APPROVED' AND a.activityTime > :now " +
            "ORDER BY a.activityTime ASC")
    List<TeachingActivity> findUpcomingActivities(@Param("now") LocalDateTime now, Pageable pageable);

    // 获取进行中的活动
    @Query("SELECT a FROM TeachingActivity a WHERE a.isDeleted = false " +
            "AND a.status = 'APPROVED' AND a.activityTime <= :now " +
            "AND a.activityTime > :oneHourAgo")
    List<TeachingActivity> findOngoingActivities(@Param("now") LocalDateTime now,
                                                 @Param("oneHourAgo") LocalDateTime oneHourAgo);

    // 软删除
    @Modifying
    @Transactional
    @Query("UPDATE TeachingActivity a SET a.isDeleted = true WHERE a.id = :id")
    void softDelete(@Param("id") Long id);

    // 更新参与人数
    @Modifying
    @Transactional
    @Query("UPDATE TeachingActivity a SET a.currentParticipants = a.currentParticipants + 1 WHERE a.id = :id AND a.currentParticipants < a.maxParticipants")
    int incrementParticipants(@Param("id") Long id);

    @Modifying
    @Transactional
    @Query("UPDATE TeachingActivity a SET a.currentParticipants = a.currentParticipants - 1 WHERE a.id = :id AND a.currentParticipants > 0")
    int decrementParticipants(@Param("id") Long id);

    // 增加浏览次数
    @Modifying
    @Transactional
    @Query("UPDATE TeachingActivity a SET a.viewCount = a.viewCount + 1 WHERE a.id = :id")
    void incrementViewCount(@Param("id") Long id);

    @Modifying
    @Transactional
    @Query("UPDATE TeachingActivity a SET a.isPinned = :pinned WHERE a.id = :id")
    void updatePinned(@Param("id") Long id, @Param("pinned") Boolean pinned);

    @Modifying
    @Transactional
    @Query(value = "UPDATE teaching_activity SET status = 'ONGOING' " +
            "WHERE is_deleted = false AND status = 'APPROVED' AND activity_time <= NOW() " +
            "AND DATE_ADD(activity_time, INTERVAL duration MINUTE) > NOW()", nativeQuery = true)
    int markStartedActivitiesOngoing();

    @Modifying
    @Transactional
    @Query(value = "UPDATE teaching_activity SET status = 'ENDED_PENDING_ARCHIVE' " +
            "WHERE is_deleted = false AND status IN ('APPROVED', 'ONGOING') " +
            "AND DATE_ADD(activity_time, INTERVAL duration MINUTE) <= NOW()", nativeQuery = true)
    int markFinishedActivitiesCompleted();
}
