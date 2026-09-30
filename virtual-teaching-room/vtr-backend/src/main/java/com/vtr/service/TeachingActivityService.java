package com.vtr.service;

import com.vtr.common.PageResult;
import com.vtr.dto.*;
import com.vtr.vo.ActivityVO;
import com.vtr.vo.DiscussionVO;

import java.util.Map;

public interface TeachingActivityService {

    // ========== 活动管理 ==========

    /**
     * 创建教研活动
     * @param dto 活动创建信息
     * @param organizerId 组织者ID
     * @return 活动ID
     */
    Long createActivity(ActivityCreateDTO dto, Long organizerId);

    /**
     * 更新教研活动
     * @param id 活动ID
     * @param dto 更新信息
     * @param userId 当前用户ID
     * @param isAdmin 是否管理员
     */
    void updateActivity(Long id, ActivityUpdateDTO dto, Long userId, boolean isAdmin);

    /**
     * 删除教研活动（软删除）
     * @param id 活动ID
     * @param userId 当前用户ID
     * @param isAdmin 是否管理员
     */
    void deleteActivity(Long id, Long userId, boolean isAdmin);

    /**
     * 获取活动详情
     * @param id 活动ID
     * @param userId 当前用户ID（可为null）
     * @return 活动详情VO
     */
    ActivityVO getActivityById(Long id, Long userId, boolean isAdmin);

    /**
     * 分页查询活动列表
     * @param query 查询条件
     * @param userId 当前用户ID
     * @return 分页结果
     */
    PageResult<ActivityVO> queryActivities(ActivityQueryDTO query, Long userId);

    /**
     * 审核活动（管理员）
     * @param id 活动ID
     * @param dto 审核信息
     * @param adminId 管理员ID
     */
    void reviewActivity(Long id, ActivityReviewDTO dto, Long adminId);

    /**
     * 取消活动（组织者）
     * @param id 活动ID
     * @param userId 组织者ID
     */
    void cancelActivity(Long id, Long userId, boolean isAdmin, String cancelReason);

    void archiveActivity(Long id, Long userId, boolean isAdmin);

    void updatePinned(Long id, boolean pinned);

    // ========== 活动参与 ==========

    /**
     * 参与活动
     * <p>会检查活动状态、是否满员、是否已参与等</p>
     * @param activityId 活动ID
     * @param teacherId 教师ID
     * @throws com.vtr.common.exception.BusinessException 如果活动未通过审核、已满员、已参与或已结束
     */
    void joinActivity(Long activityId, Long teacherId);

    /**
     * 取消参与
     * @param activityId 活动ID
     * @param teacherId 教师ID
     * @throws com.vtr.common.exception.BusinessException 如果活动已开始或未参与
     */
    void cancelJoinActivity(Long activityId, Long teacherId);

    /**
     * 签到
     * @param activityId 活动ID
     * @param teacherId 教师ID
     * @throws com.vtr.common.exception.BusinessException 如果未参与或已签到
     */
    void checkIn(Long activityId, Long teacherId);

    /**
     * 提交活动反馈
     * @param activityId 活动ID
     * @param teacherId 教师ID
     * @param feedback 反馈内容
     * @param rating 评分（1-5）
     */
    void submitFeedback(Long activityId, Long teacherId, String feedback, Integer rating);

    /**
     * 检查用户是否已确认参与活动（只检查 CONFIRMED 状态）
     * <p>用于判断用户是否真正参与（已确认）</p>
     * @param activityId 活动ID
     * @param userId 用户ID
     * @return true-已参与，false-未参与
     */
    boolean isParticipant(Long activityId, Long userId);

    /**
     * 检查用户是否曾参与过活动（包括所有状态）
     * <p>用于防止重复加入，包括 CONFIRMED、CANCELLED 等状态</p>
     * @param activityId 活动ID
     * @param userId 用户ID
     * @return true-曾参与过，false-从未参与
     */
    boolean hasEverParticipated(Long activityId, Long userId);

    /**
     * 获取签到统计信息
     * @param activityId 活动ID
     * @return 签到统计信息，包含已签到人数、总参与人数、签到率
     */
    Map<String, Object> getCheckinStats(Long activityId, Long userId, boolean isAdmin);

    // ========== 活动讨论 ==========

    /**
     * 发表评论
     * @param dto 评论信息
     * @param teacherId 教师ID
     * @return 评论ID
     */
    Long addDiscussion(DiscussionCreateDTO dto, Long teacherId);

    /**
     * 删除评论
     * @param id 评论ID
     * @param userId 当前用户ID
     */
    void deleteDiscussion(Long id, Long userId);

    /**
     * 点赞评论
     * @param id 评论ID
     */
    void likeDiscussion(Long id, Long userId);

    /**
     * 获取活动讨论列表
     * @param activityId 活动ID
     * @param page 页码
     * @param size 每页大小
     * @return 分页结果
     */
    PageResult<DiscussionVO> getDiscussions(Long activityId, Integer page, Integer size, Long userId, boolean isAdmin);

    // ========== 辅助方法 ==========

    /**
     * 增加浏览次数
     * @param id 活动ID
     */
    void incrementViewCount(Long id);
}
