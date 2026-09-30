package com.vtr.service.impl;

import com.vtr.common.PageResult;
import com.vtr.common.exception.BusinessException;
import com.vtr.common.exception.NotFoundException;
import com.vtr.dto.ContentAuditDTO;
import com.vtr.dto.PageQueryDTO;
import com.vtr.entity.ContentAudit;
import com.vtr.entity.Courseware;
import com.vtr.entity.ForumComment;
import com.vtr.entity.ForumPost;
import com.vtr.entity.TeachingActivity;
import com.vtr.entity.User;
import com.vtr.event.CoursewareChangedEvent;
import com.vtr.repository.ContentAuditRepository;
import com.vtr.repository.CoursewareRepository;
import com.vtr.repository.ForumCommentRepository;
import com.vtr.repository.ForumPostRepository;
import com.vtr.repository.TeachingActivityRepository;
import com.vtr.repository.UserRepository;
import com.vtr.service.ContentAuditService;
import com.vtr.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentAuditServiceImpl implements ContentAuditService {

    private static final Set<String> MODERATION_RISK_LEVELS = Set.of("HIGH", "MEDIUM", "REPORTED", "APPEAL");

    private final ContentAuditRepository auditRepository;
    private final UserRepository userRepository;
    private final ForumPostRepository forumPostRepository;
    private final ForumCommentRepository forumCommentRepository;
    private final CoursewareRepository coursewareRepository;
    private final TeachingActivityRepository activityRepository;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public PageResult<ContentAudit> getPendingList(PageQueryDTO query) {
        // ========== 修改：添加 Sort.by("createdAt").ascending() 排序 ==========
        Page<ContentAudit> page = auditRepository.findByStatusOrderByCreatedAtAsc(
                ContentAudit.AuditStatus.PENDING,
                PageRequest.of(query.getPage() - 1, query.getSize(), Sort.by("createdAt").ascending())
        );

        return PageResult.of(page.getContent(), page.getTotalElements(),
                query.getPage(), query.getSize());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<ContentAudit> getPendingList(PageQueryDTO query, Set<Long> schoolIds) {
        Page<ContentAudit> page = auditRepository.findByStatusAndSchoolIdInOrderByCreatedAtAsc(
                ContentAudit.AuditStatus.PENDING, schoolIds,
                PageRequest.of(query.getPage() - 1, query.getSize(), Sort.by("createdAt").ascending()));
        return PageResult.of(page.getContent(), page.getTotalElements(), query.getPage(), query.getSize());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<ContentAudit> getList(PageQueryDTO query, String status, Set<Long> schoolIds) {
        Set<ContentAudit.AuditStatus> auditStatuses = null;
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            try {
                String normalized = status.trim().toUpperCase();
                if ("PASS".equals(normalized)) {
                    auditStatuses = Set.of(ContentAudit.AuditStatus.PASS, ContentAudit.AuditStatus.APPROVED);
                } else if ("REJECT".equals(normalized)) {
                    auditStatuses = Set.of(ContentAudit.AuditStatus.REJECT, ContentAudit.AuditStatus.REJECTED);
                } else {
                    auditStatuses = Set.of(ContentAudit.AuditStatus.valueOf(normalized));
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("审核状态无效");
            }
        }
        if (auditStatuses == null) {
            auditStatuses = Set.of(ContentAudit.AuditStatus.values());
        }
        PageRequest request = PageRequest.of(query.getPage() - 1, query.getSize(), Sort.by("createdAt").descending());
        Page<ContentAudit> page;
        if (schoolIds == null) {
            page = auditRepository.findModerationByStatusesAndRiskLevels(
                    auditStatuses, MODERATION_RISK_LEVELS, request);
        } else if (schoolIds.isEmpty()) {
            return PageResult.of(List.of(), 0L, query.getPage(), query.getSize());
        } else {
            page = auditRepository.findModerationByStatusesAndRiskLevelsAndSchoolIdIn(
                    auditStatuses, MODERATION_RISK_LEVELS, schoolIds, request);
        }
        return PageResult.of(page.getContent(), page.getTotalElements(), query.getPage(), query.getSize());
    }

    @Override
    @Transactional
    // ========== 修改：添加 reviewerName 参数，使用 audit 实体自带的 pass/reject 方法 ==========
    public void review(Long id, ContentAuditDTO dto, Long reviewerId, String reviewerName) {
        ContentAudit audit = auditRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("审核记录", id));
        if (audit.getStatus() != ContentAudit.AuditStatus.PENDING
                && audit.getStatus() != ContentAudit.AuditStatus.NEED_REVIEW) {
            throw new BusinessException("该内容已经处置，不能重复操作");
        }

        if ("PASS".equals(dto.getAction())) {
            applyDecision(audit, true, dto.getRemark(), reviewerId);
            audit.pass(reviewerId, reviewerName, dto.getRemark());
        } else if ("REJECT".equals(dto.getAction())) {
            if (dto.getRemark() == null || dto.getRemark().trim().isEmpty()) {
                throw new BusinessException("下架或驳回时必须填写原因");
            }
            applyDecision(audit, false, dto.getRemark(), reviewerId);
            audit.reject(reviewerId, reviewerName, dto.getRemark());
        } else {
            throw new IllegalArgumentException("无效的审核操作: " + dto.getAction());
        }

        auditRepository.save(audit);

        log.info("内容审核完成: id={}, action={}, reviewer={}", id, dto.getAction(), reviewerName);
    }

    @Override
    public Map<String, Object> getDashboardStatistics() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("pendingAudits", auditRepository.countByStatusAndRiskLevelIn(
                ContentAudit.AuditStatus.PENDING, MODERATION_RISK_LEVELS));

        // ========== 修改：完善按类型统计，使用 getDescription() ==========
        List<Object[]> typeStats = auditRepository.countByContentType();
        Map<String, Long> typeMap = new HashMap<>();
        for (Object[] stat : typeStats) {
            ContentAudit.ContentType type = (ContentAudit.ContentType) stat[0];
            Long count = (Long) stat[1];
            typeMap.put(type.getDescription(), count);
        }
        stats.put("auditsByType", typeMap);

        return stats;
    }

    @Override
    @Transactional
    public void createAudit(ContentAudit.ContentType type, Long contentId, String title,
                            String preview, Long authorId, String authorName) {
        createOrEscalate(type, contentId, title, preview, authorId, authorName,
                userRepository.findById(authorId).map(User::getSchoolId).orElse(null),
                "LOW", "内容进入人工复核队列");
        log.info("创建审核记录: type={}, contentId={}, author={}", type, contentId, authorName);
    }

    @Override
    @Transactional
    public ContentAudit createOrEscalate(ContentAudit.ContentType type, Long contentId, String title,
                                         String preview, Long authorId, String authorName, Long schoolId,
                                         String riskLevel, String details) {
        ContentAudit audit = auditRepository.findByContentTypeAndContentId(type, contentId).orElse(null);
        if (audit == null) {
            audit = ContentAudit.builder().contentType(type).contentId(contentId).submitCount(1).build();
        } else {
            audit.setSubmitCount((audit.getSubmitCount() == null ? 0 : audit.getSubmitCount()) + 1);
        }
        audit.setContentTitle(title);
        audit.setContentPreview(truncate(preview, 500));
        audit.setAuthorId(authorId);
        audit.setAuthorName(authorName);
        audit.setSchoolId(schoolId);
        if (!Set.of("REPORTED", "APPEAL").contains(audit.getRiskLevel())) {
            audit.setRiskLevel(riskLevel == null ? "MEDIUM" : riskLevel);
        }
        audit.setAutoCheckResult(appendDetail(audit.getAutoCheckResult(), details));
        audit.setStatus(ContentAudit.AuditStatus.PENDING);
        audit.setReviewerId(null);
        audit.setReviewerName(null);
        audit.setReviewedAt(null);
        audit.setReviewRemark(null);
        return auditRepository.save(audit);
    }

    @Override
    @Transactional
    public void resolveAutomatedRisk(ContentAudit.ContentType type, Long contentId, Long actorId,
                                     String actorName, String remark) {
        auditRepository.findByContentTypeAndContentId(type, contentId)
                .filter(audit -> audit.getStatus() == ContentAudit.AuditStatus.PENDING)
                .filter(audit -> "HIGH".equals(audit.getRiskLevel()))
                .ifPresent(audit -> {
                    audit.pass(actorId, actorName, remark);
                    auditRepository.save(audit);
                });
    }

    @Override
    @Transactional
    public void report(String targetType, Long targetId, String reason, Long reporterId, String reporterName) {
        if (reporterId == null) throw new BusinessException("请先登录后再举报");
        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new NotFoundException("用户", reporterId));
        String normalized = targetType == null ? "" : targetType.trim().toUpperCase();
        ContentAudit.ContentType type;
        String title;
        String preview;
        Long authorId;
        String authorName;
        Long schoolId;

        switch (normalized) {
            case "FORUM_POST", "POST" -> {
                ForumPost item = forumPostRepository.findById(targetId)
                        .orElseThrow(() -> new NotFoundException("社区帖子", targetId));
                if (!"APPROVED".equals(item.getAuditStatus())) throw new BusinessException("该帖子当前不可举报");
                type = ContentAudit.ContentType.FORUM_POST;
                title = item.getTitle(); preview = item.getContent(); authorId = item.getAuthorId();
                authorName = item.getAuthorName(); schoolId = item.getSchoolId();
            }
            case "FORUM_COMMENT", "COMMENT" -> {
                ForumComment item = forumCommentRepository.findById(targetId)
                        .orElseThrow(() -> new NotFoundException("社区评论", targetId));
                if (!"APPROVED".equals(item.getAuditStatus())) throw new BusinessException("该评论当前不可举报");
                type = ContentAudit.ContentType.FORUM_COMMENT;
                title = "社区评论 #" + targetId; preview = item.getContent(); authorId = item.getAuthorId();
                authorName = item.getAuthorName(); schoolId = item.getSchoolId();
            }
            case "COURSEWARE", "RESOURCE" -> {
                Courseware item = coursewareRepository.findById(targetId)
                        .orElseThrow(() -> new NotFoundException("教学资源", targetId));
                if (!"ACTIVE".equals(item.getStatus())) throw new BusinessException("该教学资源当前不可举报");
                type = ContentAudit.ContentType.COURSEWARE;
                title = item.getTitle(); preview = item.getDescription(); authorId = item.getTeacherId();
                authorName = userRepository.findById(authorId).map(User::getUsername).orElse("教师"); schoolId = item.getSchoolId();
            }
            case "ACTIVITY" -> {
                TeachingActivity item = activityRepository.findByIdAndIsDeletedFalse(targetId)
                        .orElseThrow(() -> new NotFoundException("教研活动", targetId));
                if (TeachingActivity.ActivityStatus.PENDING.name().equals(item.getStatus())
                        || TeachingActivity.ActivityStatus.REJECTED.name().equals(item.getStatus())) {
                    throw new BusinessException("该教研活动当前不可举报");
                }
                type = ContentAudit.ContentType.ACTIVITY;
                title = item.getTitle(); preview = item.getContent(); authorId = item.getOrganizerId();
                authorName = userRepository.findById(authorId).map(User::getUsername).orElse("活动组织者"); schoolId = item.getSchoolId();
            }
            default -> throw new BusinessException("暂不支持举报该类型内容");
        }

        if (reporter.getRole() != User.UserRole.SUPER_ADMIN
                && !java.util.Objects.equals(reporter.getSchoolId(), schoolId)) {
            throw new BusinessException("只能举报本校可见内容");
        }
        if (java.util.Objects.equals(reporterId, authorId)) throw new BusinessException("不能举报自己发布的内容");
        ContentAudit audit = createOrEscalate(type, targetId, title, preview, authorId, authorName, schoolId,
                "REPORTED", String.format("举报人：%s（%s），原因：%s", reporterName, reporterId, reason.trim()));
        notifyAdminsAboutReport(audit);
    }

    @Override
    @Transactional
    public void appeal(String targetType, Long targetId, String reason, Long appellantId, String appellantName) {
        if (appellantId == null) throw new BusinessException("请先登录后再申诉");
        ContentAudit.ContentType type = switch (targetType == null ? "" : targetType.trim().toUpperCase()) {
            case "FORUM_POST", "POST" -> ContentAudit.ContentType.FORUM_POST;
            case "FORUM_COMMENT", "COMMENT" -> ContentAudit.ContentType.FORUM_COMMENT;
            case "COURSEWARE", "RESOURCE" -> ContentAudit.ContentType.COURSEWARE;
            case "ACTIVITY" -> ContentAudit.ContentType.ACTIVITY;
            default -> throw new BusinessException("暂不支持申诉该类型内容");
        };
        ContentAudit audit = auditRepository.findByContentTypeAndContentId(type, targetId)
                .orElseThrow(() -> new BusinessException("该内容没有可申诉的风险处置记录"));
        if (!java.util.Objects.equals(audit.getAuthorId(), appellantId)) {
            throw new BusinessException("只能申诉自己发布的内容");
        }
        if (audit.getStatus() != ContentAudit.AuditStatus.REJECT
                && audit.getStatus() != ContentAudit.AuditStatus.REJECTED) {
            throw new BusinessException("只有已下架或被拒绝的内容可以申诉");
        }
        audit.setSubmitCount((audit.getSubmitCount() == null ? 0 : audit.getSubmitCount()) + 1);
        audit.setRiskLevel("APPEAL");
        audit.setAutoCheckResult(appendDetail(audit.getAutoCheckResult(),
                String.format("申诉人：%s（%s），理由：%s", appellantName, appellantId, reason.trim())));
        audit.setStatus(ContentAudit.AuditStatus.PENDING);
        audit.setReviewerId(null);
        audit.setReviewerName(null);
        audit.setReviewedAt(null);
        audit.setReviewRemark(null);
        auditRepository.save(audit);
        notifyAdminsAboutAppeal(audit);
    }

    @Override
    // ========== 新增方法 ==========
    public ContentAudit getByContentTypeAndContentId(ContentAudit.ContentType type, Long contentId) {
        return auditRepository.findByContentTypeAndContentId(type, contentId).orElse(null);
    }

    @Override
    public ContentAudit getById(Long id) {
        return auditRepository.findById(id).orElseThrow(() -> new NotFoundException("审核记录", id));
    }

    private void applyDecision(ContentAudit audit, boolean approved, String remark, Long reviewerId) {
        switch (audit.getContentType()) {
            case FORUM_POST -> forumPostRepository.findById(audit.getContentId()).ifPresent(post -> {
                if (approved) post.approve(reviewerId, remark); else post.reject(reviewerId, remark);
                forumPostRepository.save(post);
                notifyRemoval(post.getAuthorId(), approved, "社区帖子", post.getTitle(), remark, post.getId());
            });
            case FORUM_COMMENT -> forumCommentRepository.findById(audit.getContentId()).ifPresent(comment -> {
                boolean wasApproved = comment.isApproved();
                boolean wasPending = comment.isPending();
                if (approved) {
                    comment.approve(reviewerId, remark);
                    if (wasPending) forumPostRepository.incrementReplyCount(comment.getPostId());
                } else {
                    comment.reject(reviewerId, remark);
                    if (wasApproved) forumPostRepository.decrementReplyCount(comment.getPostId());
                }
                forumCommentRepository.save(comment);
                notifyRemoval(comment.getAuthorId(), approved, "社区评论", "评论 #" + comment.getId(), remark, comment.getPostId());
            });
            case COURSEWARE -> coursewareRepository.findById(audit.getContentId()).ifPresent(resource -> {
                resource.setStatus(approved ? "ACTIVE" : "REJECTED");
                resource.setAuditRemark(remark);
                resource.setReviewedBy(reviewerId);
                resource.setReviewedAt(LocalDateTime.now());
                coursewareRepository.save(resource);
                eventPublisher.publishEvent(CoursewareChangedEvent.syncStatus(resource.getId()));
                notifyRemoval(resource.getTeacherId(), approved, "教学资源", resource.getTitle(), remark, resource.getId());
            });
            case ACTIVITY -> activityRepository.findById(audit.getContentId()).ifPresent(activity -> {
                activity.setStatus(approved ? TeachingActivity.ActivityStatus.APPROVED.name()
                        : TeachingActivity.ActivityStatus.REJECTED.name());
                activity.setRejectReason(approved ? null : remark);
                activity.setUpdatedBy(reviewerId);
                activityRepository.save(activity);
                notifyRemoval(activity.getOrganizerId(), approved, "教研活动", activity.getTitle(), remark, activity.getId());
            });
            default -> { }
        }
    }

    private void notifyRemoval(Long userId, boolean approved, String type, String title, String remark, Long relatedId) {
        if (userId == null || approved) return;
        notificationService.sendNotification(userId, "CONTENT_MODERATION", type + "已被处置",
                String.format("您发布的%s“%s”已被下架。原因：%s", type, title, remark), relatedId);
    }

    private void notifyAdminsAboutReport(ContentAudit audit) {
        List<User> admins = new ArrayList<>(userRepository.findAllByRoleAndStatus(
                User.UserRole.ADMIN, User.UserStatus.ACTIVE));
        admins.addAll(userRepository.findAllByRoleAndStatus(
                User.UserRole.SUPER_ADMIN, User.UserStatus.ACTIVE));
        admins.stream()
                .filter(admin -> admin.getRole() == User.UserRole.SUPER_ADMIN
                        || (audit.getSchoolId() == null && Boolean.TRUE.equals(admin.getAdminScopeUnbound()))
                        || (audit.getSchoolId() != null && audit.getSchoolId().equals(admin.getSchoolId())))
                .forEach(admin -> notificationService.sendNotification(
                        admin.getId(), "MODERATION_REPORTED", "有新的内容举报待处置",
                        "用户举报了“" + audit.getContentTitle() + "”，请前往管理中心的风险与举报队列处理。",
                        audit.getId()));
    }

    private void notifyAdminsAboutAppeal(ContentAudit audit) {
        List<User> admins = new ArrayList<>(userRepository.findAllByRoleAndStatus(
                User.UserRole.ADMIN, User.UserStatus.ACTIVE));
        admins.addAll(userRepository.findAllByRoleAndStatus(
                User.UserRole.SUPER_ADMIN, User.UserStatus.ACTIVE));
        admins.stream()
                .filter(admin -> admin.getRole() == User.UserRole.SUPER_ADMIN
                        || (audit.getSchoolId() == null && Boolean.TRUE.equals(admin.getAdminScopeUnbound()))
                        || (audit.getSchoolId() != null && audit.getSchoolId().equals(admin.getSchoolId())))
                .forEach(admin -> notificationService.sendNotification(
                        admin.getId(), "MODERATION_APPEAL", "有新的内容申诉待处理",
                        "发布者对“" + audit.getContentTitle() + "”的处置结果提出申诉，请前往管理中心的风险与举报队列处理。",
                        audit.getId()));
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }

    private String appendDetail(String existing, String details) {
        if (details == null || details.isBlank()) return existing;
        if (existing == null || existing.isBlank()) return details;
        return truncate(existing + "\n" + details, 4000);
    }
}
