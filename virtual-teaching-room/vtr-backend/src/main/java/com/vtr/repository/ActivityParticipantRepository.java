package com.vtr.repository;

import com.vtr.entity.ActivityParticipant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface ActivityParticipantRepository extends JpaRepository<ActivityParticipant, Long> {

    // 查询活动的所有参与者
    List<ActivityParticipant> findByActivityId(Long activityId);

    // 查询活动中状态为 CONFIRMED 的参与者
    @Query("SELECT p FROM ActivityParticipant p WHERE p.activityId = :activityId AND p.status = 'CONFIRMED'")
    List<ActivityParticipant> findByActivityIdAndStatusConfirmed(@Param("activityId") Long activityId);

    Page<ActivityParticipant> findByActivityId(Long activityId, Pageable pageable);

    // 查询教师参与的所有活动（只统计 CONFIRMED 状态）
    @Query("SELECT p FROM ActivityParticipant p WHERE p.teacherId = :teacherId AND p.status = 'CONFIRMED'")
    Page<ActivityParticipant> findByTeacherIdAndStatusConfirmed(@Param("teacherId") Long teacherId, Pageable pageable);

    /**
     * 检查教师是否已确认参与活动（只检查 CONFIRMED 状态）
     * 用于判断用户是否真正参与（已确认）
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM ActivityParticipant p WHERE p.activityId = :activityId AND p.teacherId = :teacherId AND p.status = 'CONFIRMED'")
    boolean existsConfirmedByActivityIdAndTeacherId(@Param("activityId") Long activityId, @Param("teacherId") Long teacherId);

    /**
     * 检查教师是否曾参与过活动（包括所有状态）
     * 用于查找已存在的记录（包括 CANCELLED 状态）
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM ActivityParticipant p WHERE p.activityId = :activityId AND p.teacherId = :teacherId")
    boolean existsByActivityIdAndTeacherId(@Param("activityId") Long activityId, @Param("teacherId") Long teacherId);

    // 获取参与记录（不限制状态）
    Optional<ActivityParticipant> findByActivityIdAndTeacherId(Long activityId, Long teacherId);

    // 取消参与（更新状态为 CANCELLED）
    @Modifying
    @Transactional
    @Query("UPDATE ActivityParticipant p SET p.status = 'CANCELLED' WHERE p.activityId = :activityId AND p.teacherId = :teacherId AND p.status = 'CONFIRMED'")
    int cancelParticipation(@Param("activityId") Long activityId, @Param("teacherId") Long teacherId);

    @Modifying
    @Transactional
    @Query("UPDATE ActivityParticipant p SET p.status = 'CANCELLED' WHERE p.activityId = :activityId AND p.status = 'CONFIRMED'")
    int cancelByActivityId(@Param("activityId") Long activityId);

    // 签到
    @Modifying
    @Transactional
    @Query("UPDATE ActivityParticipant p SET p.checkInTime = CURRENT_TIMESTAMP WHERE p.activityId = :activityId AND p.teacherId = :teacherId AND p.status = 'CONFIRMED'")
    int checkIn(@Param("activityId") Long activityId, @Param("teacherId") Long teacherId);

    // 提交反馈
    @Modifying
    @Transactional
    @Query("UPDATE ActivityParticipant p SET p.feedback = :feedback, p.rating = :rating WHERE p.activityId = :activityId AND p.teacherId = :teacherId AND p.status = 'CONFIRMED'")
    int submitFeedback(@Param("activityId") Long activityId,
                       @Param("teacherId") Long teacherId,
                       @Param("feedback") String feedback,
                       @Param("rating") Integer rating);

    // 统计活动参与人数（只统计 CONFIRMED 状态）
    @Query("SELECT COUNT(p) FROM ActivityParticipant p WHERE p.activityId = :activityId AND p.status = 'CONFIRMED'")
    long countByActivityIdAndStatusConfirmed(@Param("activityId") Long activityId);

    // 统计已签到人数
    @Query("SELECT COUNT(p) FROM ActivityParticipant p WHERE p.activityId = :activityId AND p.status = 'CONFIRMED' AND p.checkInTime IS NOT NULL")
    long countCheckedInByActivityId(@Param("activityId") Long activityId);

    // 检查用户是否已签到
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM ActivityParticipant p WHERE p.activityId = :activityId AND p.teacherId = :teacherId AND p.checkInTime IS NOT NULL")
    boolean hasCheckedIn(@Param("activityId") Long activityId, @Param("teacherId") Long teacherId);

    // 获取教师参与的活动ID列表（只统计 CONFIRMED 状态）
    @Query("SELECT p.activityId FROM ActivityParticipant p WHERE p.teacherId = :teacherId AND p.status = 'CONFIRMED'")
    List<Long> findActivityIdsByTeacherId(@Param("teacherId") Long teacherId);
}
