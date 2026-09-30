package com.vtr.controller;

import com.vtr.common.PageResult;
import com.vtr.common.Result;
import com.vtr.dto.*;
import com.vtr.entity.TeachingActivity;
import com.vtr.service.TeachingActivityService;
import com.vtr.vo.ActivityVO;
import com.vtr.vo.DiscussionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/teaching-activities")
@RequiredArgsConstructor
public class TeachingActivityController {

    private final TeachingActivityService activityService;

    // ========== 活动管理 ==========

    /**
     * 创建教研活动
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<Long> createActivity(@RequestBody @Valid ActivityCreateDTO dto) {
        Long userId = getCurrentUserId();
        log.info("创建教研活动: userId={}, title={}", userId, dto.getTitle());
        return Result.success(activityService.createActivity(dto, userId));
    }

    /**
     * 更新教研活动
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<Void> updateActivity(@PathVariable Long id,
                                       @RequestBody @Valid ActivityUpdateDTO dto) {
        Long userId = getCurrentUserId();
        boolean isAdmin = hasRole("ADMIN") || hasRole("SUPER_ADMIN");
        log.info("更新教研活动: id={}, userId={}", id, userId);
        activityService.updateActivity(id, dto, userId, isAdmin);
        return Result.success();
    }

    /**
     * 删除教研活动
     * 管理员可以删除任何活动，创建者可以删除自己创建的活动。
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<Void> deleteActivity(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        boolean isAdmin = hasRole("ADMIN") || hasRole("SUPER_ADMIN");
        log.info("删除教研活动: id={}, userId={}, isAdmin={}", id, userId, isAdmin);
        activityService.deleteActivity(id, userId, isAdmin);
        return Result.success();
    }

    /**
     * 获取活动详情
     */
    @GetMapping("/{id}")
    public Result<ActivityVO> getActivityById(@PathVariable Long id) {
        Long userId = getCurrentUserIdSilently();
        log.debug("获取教研活动详情: id={}, userId={}", id, userId);
        return Result.success(activityService.getActivityById(id, userId, hasRole("ADMIN") || hasRole("SUPER_ADMIN")));
    }

    /**
     * 分页查询活动列表
     */
    @GetMapping
    public Result<PageResult<ActivityVO>> queryActivities(@Valid ActivityQueryDTO query) {
        Long userId = getCurrentUserIdSilently();
        // Empty form values must behave like omitted filters. Otherwise the
        // repository query would require type/status to equal an empty string.
        if (query.getStatus() != null && query.getStatus().trim().isEmpty()) query.setStatus(null);
        if (query.getType() != null && query.getType().trim().isEmpty()) query.setType(null);
        if (query.getKeyword() != null && query.getKeyword().trim().isEmpty()) query.setKeyword(null);
        boolean privileged = hasRole("ADMIN") || hasRole("SUPER_ADMIN");
        // 未登录访客只能浏览已通过审核的活动；登录后的学生和教师可以查看
        // 本校所有非草稿、非驳回活动，包括已结束和已取消的历史活动。
        if (userId == null && !privileged && !Boolean.TRUE.equals(query.getMyCreated())
                && !Boolean.TRUE.equals(query.getMyParticipation())) {
            query.setStatus(TeachingActivity.ActivityStatus.APPROVED.name());
        }
        log.debug("查询教研活动列表: page={}, size={}, userId={}", query.getPage(), query.getSize(), userId);
        return Result.success(activityService.queryActivities(query, userId));
    }

    /**
     * 审核活动（管理员）
     */
    @PutMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> reviewActivity(@PathVariable Long id,
                                       @RequestBody @Valid ActivityReviewDTO dto) {
        Long adminId = getCurrentUserId();
        log.info("审核教研活动: id={}, adminId={}, action={}", id, adminId, dto.getAction());
        activityService.reviewActivity(id, dto, adminId);
        return Result.success();
    }

    /**
     * 取消活动
     */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<Void> cancelActivity(@PathVariable Long id,
                                       @RequestParam(required = false) String reason) {
        Long userId = getCurrentUserId();
        log.info("取消教研活动: id={}, userId={}", id, userId);
        activityService.cancelActivity(id, userId, hasRole("ADMIN") || hasRole("SUPER_ADMIN"), reason);
        return Result.success();
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<Void> archiveActivity(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        activityService.archiveActivity(id, userId, hasRole("ADMIN") || hasRole("SUPER_ADMIN"));
        return Result.success();
    }

    @PutMapping("/{id}/pinned")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> updatePinned(@PathVariable Long id, @RequestParam boolean pinned) {
        activityService.updatePinned(id, pinned);
        return Result.success();
    }

    // ========== 活动参与 ==========

    /**
     * 参与活动
     */
    @PostMapping("/{id}/join")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> joinActivity(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        log.info("参与教研活动: activityId={}, userId={}", id, userId);
        activityService.joinActivity(id, userId);
        return Result.success();
    }

    /**
     * 取消参与
     */
    @DeleteMapping("/{id}/join")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> cancelJoinActivity(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        log.info("取消参与教研活动: activityId={}, userId={}", id, userId);
        activityService.cancelJoinActivity(id, userId);
        return Result.success();
    }

    /**
     * 签到
     */
    @PostMapping("/{id}/checkin")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> checkIn(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        log.info("签到: activityId={}, userId={}", id, userId);
        activityService.checkIn(id, userId);
        return Result.success();
    }

    /**
     * 获取签到统计
     */
    @GetMapping("/{id}/checkin-stats")
    public Result<Map<String, Object>> getCheckinStats(@PathVariable Long id) {
        log.info("获取签到统计: activityId={}", id);
        Map<String, Object> stats = activityService.getCheckinStats(id, getCurrentUserIdSilently(), hasRole("ADMIN") || hasRole("SUPER_ADMIN"));
        return Result.success(stats);
    }

    /**
     * 提交反馈
     */
    @PostMapping("/{id}/feedback")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> submitFeedback(@PathVariable Long id,
                                       @RequestParam String feedback,
                                       @RequestParam(required = false, defaultValue = "0") Integer rating) {
        Long userId = getCurrentUserId();
        log.info("提交反馈: activityId={}, userId={}", id, userId);
        activityService.submitFeedback(id, userId, feedback, rating);
        return Result.success();
    }

    // ========== 活动讨论 ==========

    /**
     * 发表评论
     */
    @PostMapping("/discussions")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<Long> addDiscussion(@RequestBody @Valid DiscussionCreateDTO dto) {
        Long teacherId = getCurrentUserId();
        log.info("发表评论: activityId={}, teacherId={}", dto.getActivityId(), teacherId);
        return Result.success(activityService.addDiscussion(dto, teacherId));
    }

    /**
     * 删除评论
     */
    @DeleteMapping("/discussions/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<Void> deleteDiscussion(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        log.info("删除评论: id={}, userId={}", id, userId);
        activityService.deleteDiscussion(id, userId);
        return Result.success();
    }

    /**
     * 点赞评论
     */
    @PostMapping("/discussions/{id}/like")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> likeDiscussion(@PathVariable Long id) {
        log.info("点赞评论: id={}", id);
        activityService.likeDiscussion(id, getCurrentUserId());
        return Result.success();
    }

    /**
     * 获取活动讨论列表
     */
    @GetMapping("/{id}/discussions")
    public Result<PageResult<DiscussionVO>> getDiscussions(@PathVariable Long id,
                                                           @RequestParam(defaultValue = "1") @javax.validation.constraints.Min(1) Integer page,
                                                           @RequestParam(defaultValue = "20") @javax.validation.constraints.Min(1) @javax.validation.constraints.Max(100) Integer size) {
        log.debug("获取活动讨论: activityId={}, page={}, size={}", id, page, size);
        return Result.success(activityService.getDiscussions(id, page, size, getCurrentUserIdSilently(), hasRole("ADMIN") || hasRole("SUPER_ADMIN")));
    }


    // ========== 辅助方法 ==========

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() ||
                "anonymousUser".equals(authentication.getPrincipal())) {
            throw new RuntimeException("未找到已认证的用户信息");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof com.vtr.security.CustomUserDetails) {
            return ((com.vtr.security.CustomUserDetails) principal).getId();
        }
        if (principal instanceof Long) {
            return (Long) principal;
        }
        if (principal instanceof Integer) {
            return ((Integer) principal).longValue();
        }
        if (principal instanceof String) {
            try {
                return Long.parseLong((String) principal);
            } catch (NumberFormatException e) {
                throw new RuntimeException("无法解析用户ID: " + principal);
            }
        }
        throw new RuntimeException("无法从 SecurityContext 中获取用户ID，principal类型: " + principal.getClass().getName());
    }

    private Long getCurrentUserIdSilently() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated() ||
                    "anonymousUser".equals(authentication.getPrincipal())) {
                return null;
            }
            Object principal = authentication.getPrincipal();
            if (principal instanceof com.vtr.security.CustomUserDetails) {
                return ((com.vtr.security.CustomUserDetails) principal).getId();
            }
            if (principal instanceof Long) {
                return (Long) principal;
            }
            if (principal instanceof Integer) {
                return ((Integer) principal).longValue();
            }
            if (principal instanceof String) {
                try {
                    return Long.parseLong((String) principal);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return false;
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role) || a.getAuthority().equals(role));
    }
}
