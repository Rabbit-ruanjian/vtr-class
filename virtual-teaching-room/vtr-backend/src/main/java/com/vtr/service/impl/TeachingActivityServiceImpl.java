package com.vtr.service.impl;

import com.vtr.common.PageResult;
import com.vtr.common.exception.BusinessException;
import com.vtr.common.exception.NotFoundException;
import com.vtr.dto.*;
import com.vtr.entity.*;
import com.vtr.repository.*;
import com.vtr.service.TeachingActivityService;
import com.vtr.service.NotificationService;
import com.vtr.service.AdminScopeService;
import com.vtr.service.ContentAuditService;
import com.vtr.service.ContentRiskService;
import com.vtr.vo.ActivityOrganizerVO;
import com.vtr.vo.ActivityVO;
import com.vtr.vo.DiscussionVO;
import com.vtr.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeachingActivityServiceImpl implements TeachingActivityService {

    private final TeachingActivityRepository activityRepository;
    private final ActivityParticipantRepository participantRepository;
    private final ActivityDiscussionRepository discussionRepository;
    private final ActivityDiscussionLikeRepository discussionLikeRepository;
    private final ActivityAttachmentRepository attachmentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final CourseRepository courseRepository;
    private final ClassroomRepository classroomRepository;
    private final AdminScopeService adminScopeService;
    private final ContentAuditService contentAuditService;
    private final ContentRiskService contentRiskService;

    // ========== 活动管理 ==========

    @Override
    @Transactional
    public Long createActivity(ActivityCreateDTO dto, Long organizerId) {
        User organizer = userRepository.findById(organizerId)
                .orElseThrow(() -> new NotFoundException("用户", organizerId));

        // 修复：使用 User 实体的 role 字段进行比较
        String role = organizer.getRole() != null ? organizer.getRole().name() : "";
        boolean canCreate = organizer.isTeacher() || organizer.isAdmin() || "SUPER_ADMIN".equals(role);
        if (!canCreate) {
            throw new BusinessException("只有教师可以创建教研活动");
        }
        if (organizer.getSchoolId() == null) {
            throw new BusinessException("当前账号未绑定学校，不能发布教研活动");
        }

        TeachingActivity activity = new TeachingActivity();
        BeanUtils.copyProperties(dto, activity);
        validateScope(activity.getCourseId(), activity.getClassroomId(), organizerId, organizer.isAdmin());
        normalizeRegistrationDeadline(activity);
        activity.setOrganizerId(organizerId);
        activity.setSchoolId(organizer.getSchoolId());
        boolean internalActivity = activity.getCourseId() != null || activity.getClassroomId() != null;
        ContentRiskService.RiskAssessment risk = assessActivityRisk(activity);
        boolean riskReview = internalActivity && risk.requiresManualReview();
        activity.setStatus(internalActivity && !riskReview
                ? TeachingActivity.ActivityStatus.APPROVED.name()
                : TeachingActivity.ActivityStatus.PENDING.name());
        activity.setCurrentParticipants(0);
        activity.setViewCount(0);
        activity.setCreatedBy(organizerId);

        activityRepository.save(activity);
        if (riskReview) {
            queueActivityRisk(activity, organizer, risk);
        } else if (!internalActivity) {
            notifyAdminsAboutPendingActivity(activity, organizerId);
        }

        log.info("创建教研活动: id={}, title={}, organizerId={}, role={}",
                activity.getId(), activity.getTitle(), organizerId, role);

        return activity.getId();
    }

    @Override
    @Transactional
    public void updateActivity(Long id, ActivityUpdateDTO dto, Long userId, boolean isAdmin) {
        TeachingActivity activity = getEntityById(id);
        requireActivitySchool(activity, userId);

        if (!isAdmin && !activity.getOrganizerId().equals(userId)) {
            throw new BusinessException("只有活动组织者可以修改");
        }

        boolean approvedInternalBeforeStart = TeachingActivity.ActivityStatus.APPROVED.name().equals(activity.getStatus())
                && (activity.getCourseId() != null || activity.getClassroomId() != null)
                && activity.getActivityTime() != null && activity.getActivityTime().isAfter(LocalDateTime.now());
        if (!isAdmin
                && !TeachingActivity.ActivityStatus.PENDING.name().equals(activity.getStatus())
                && !TeachingActivity.ActivityStatus.REJECTED.name().equals(activity.getStatus())
                && !approvedInternalBeforeStart) {
            throw new BusinessException("校内课程活动可在开始前修改；校级活动仅可在待审核或已退回时修改");
        }

        if (StringUtils.hasText(dto.getTitle())) activity.setTitle(dto.getTitle());
        if (dto.getCoverUrl() != null) activity.setCoverUrl(dto.getCoverUrl());
        if (dto.getOrganizerUnit() != null) activity.setOrganizerUnit(dto.getOrganizerUnit());
        if (dto.getSponsor() != null) activity.setSponsor(dto.getSponsor());
        if (StringUtils.hasText(dto.getContent())) activity.setContent(dto.getContent());
        if (StringUtils.hasText(dto.getType())) activity.setType(dto.getType());
        if (dto.getActivityTime() != null) activity.setActivityTime(dto.getActivityTime());
        if (StringUtils.hasText(dto.getLocation())) activity.setLocation(dto.getLocation());
        if (dto.getDuration() != null) activity.setDuration(dto.getDuration());
        if (dto.getMaxParticipants() != null) activity.setMaxParticipants(dto.getMaxParticipants());
        if (dto.getCourseId() != null) activity.setCourseId(dto.getCourseId());
        if (dto.getClassroomId() != null) activity.setClassroomId(dto.getClassroomId());
        if (dto.getRegistrationDeadline() != null) activity.setRegistrationDeadline(dto.getRegistrationDeadline());
        if (activity.getActivityTime() == null || !activity.getActivityTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException("活动时间必须晚于当前时间");
        }
        if (activity.getRegistrationDeadline() != null
                && activity.getRegistrationDeadline().isAfter(activity.getActivityTime())) {
            throw new BusinessException("报名截止时间不能晚于活动时间");
        }
        if (activity.getMaxParticipants() == null || activity.getMaxParticipants() < 1) {
            throw new BusinessException("最大参与人数必须至少为1");
        }
        long confirmedParticipants = participantRepository.countByActivityIdAndStatusConfirmed(id);
        if (activity.getMaxParticipants() < confirmedParticipants) {
            throw new BusinessException("最大参与人数不能低于当前已报名人数");
        }
        validateScope(activity.getCourseId(), activity.getClassroomId(), userId, isAdmin);
        normalizeRegistrationDeadline(activity);

        if (!isAdmin) {
            User organizer = userRepository.findById(userId)
                    .orElseThrow(() -> new NotFoundException("用户", userId));
            boolean internalActivity = activity.getCourseId() != null || activity.getClassroomId() != null;
            ContentRiskService.RiskAssessment risk = assessActivityRisk(activity);
            boolean riskReview = internalActivity && risk.requiresManualReview();
            activity.setStatus(internalActivity && !riskReview
                    ? TeachingActivity.ActivityStatus.APPROVED.name()
                    : TeachingActivity.ActivityStatus.PENDING.name());
            activity.setRejectReason(null);
            if (riskReview) {
                queueActivityRisk(activity, organizer, risk);
            } else {
                contentAuditService.resolveAutomatedRisk(ContentAudit.ContentType.ACTIVITY, activity.getId(),
                        userId, displayName(organizer), internalActivity
                                ? "组织者已修改内容，自动风险复核通过"
                                : "内容已转入校级活动发布申请队列");
                if (!internalActivity) notifyAdminsAboutPendingActivity(activity, userId);
            }
        }

        activity.setUpdatedBy(userId);
        activityRepository.save(activity);
        log.info("更新教研活动: id={}, userId={}", id, userId);
    }

    @Override
    @Transactional
    public void updatePinned(Long id, boolean pinned) {
        getEntityById(id);
        activityRepository.updatePinned(id, pinned);
        log.info("更新教研活动置顶状态: id={}, pinned={}", id, pinned);
    }

    @Override
    @Transactional
    public void deleteActivity(Long id, Long userId, boolean isAdmin) {
        TeachingActivity activity = getEntityById(id);
        requireActivitySchool(activity, userId);

        // 权限检查：管理员可以删除任何活动；创建者可以删除自己创建的活动。
        if (!isAdmin) {
            if (!activity.getOrganizerId().equals(userId)) {
                throw new BusinessException("只有活动组织者或管理员可以删除");
            }
        }

        activityRepository.softDelete(id);
        attachmentRepository.deleteByActivityId(id);
        log.info("删除教研活动: id={}, userId={}, isAdmin={}", id, userId, isAdmin);
    }

    @Override
    public ActivityVO getActivityById(Long id, Long userId, boolean isAdmin) {
        TeachingActivity activity = getEntityById(id);
        requireActivitySchool(activity, userId);

        boolean privateStatus = TeachingActivity.ActivityStatus.PENDING.name().equals(activity.getStatus())
                || TeachingActivity.ActivityStatus.REJECTED.name().equals(activity.getStatus());
        boolean visibleToUser = !privateStatus
                || isAdmin
                || (userId != null && activity.getOrganizerId().equals(userId));
        if (!visibleToUser) {
            throw new NotFoundException("教研活动", id);
        }

        activityRepository.incrementViewCount(id);

        ActivityVO vo = convertToVO(activity);

        List<ActivityParticipant> participants = participantRepository.findByActivityIdAndStatusConfirmed(id);
        vo.setParticipantCount(participants.size());

        // 检查是否满员
        int maxParticipants = activity.getMaxParticipants() != null ? activity.getMaxParticipants() : 50;
        vo.setIsFull(participants.size() >= maxParticipants);

        if (userId != null) {
            vo.setIsJoined(participantRepository.existsConfirmedByActivityIdAndTeacherId(id, userId));
            vo.setHasCheckedIn(participantRepository.hasCheckedIn(id, userId));
            vo.setIsOrganizer(activity.getOrganizerId().equals(userId));
        } else {
            vo.setIsJoined(false);
            vo.setHasCheckedIn(false);
            vo.setIsOrganizer(false);
        }

        // Participant contact details are available only to the organizer and administrators.
        if (isAdmin || (userId != null && activity.getOrganizerId().equals(userId))) {
            List<UserVO> participantVOs = participants.stream()
                    .map(p -> getUserVO(p.getTeacherId()))
                    .collect(Collectors.toList());
            vo.setParticipants(participantVOs);
        }

        return vo;
    }

    @Override
    public PageResult<ActivityVO> queryActivities(ActivityQueryDTO query, Long userId) {
        PageRequest pageRequest = PageRequest.of(query.getPage() - 1, query.getSize(),
                Sort.by(Sort.Order.desc("isPinned"), Sort.Order.asc("activityTime")));

        Page<TeachingActivity> page;
        Long schoolId = schoolScopeId(userId);
        if (userId != null && schoolId == null && !isPlatformUser(userId)) {
            return PageResult.of(new ArrayList<>(), 0L, query.getPage(), query.getSize());
        }

        boolean activityHall = userId != null
                && !Boolean.TRUE.equals(query.getMyCreated())
                && !Boolean.TRUE.equals(query.getMyParticipation())
                && !StringUtils.hasText(query.getStatus())
                && userRepository.findById(userId)
                .map(user -> user.getRole() == User.UserRole.TEACHER
                        || user.getRole() == User.UserRole.STUDENT)
                .orElse(false);

        if (query.getMyParticipation() != null && query.getMyParticipation() && userId != null) {
            List<Long> activityIds = participantRepository.findActivityIdsByTeacherId(userId);
            if (activityIds.isEmpty()) {
                List<ActivityVO> emptyList = new ArrayList<>();
                return PageResult.of(emptyList, 0L, query.getPage(), query.getSize());
            }
            page = query.getCourseId() == null
                    ? (schoolId == null
                    ? activityRepository.findByIdInAndIsDeletedFalse(activityIds, pageRequest)
                    : activityRepository.findByIdInAndSchoolIdAndIsDeletedFalse(activityIds, schoolId, pageRequest))
                    : (schoolId == null
                    ? activityRepository.findByIdInAndCourseIdAndIsDeletedFalse(activityIds, query.getCourseId(), pageRequest)
                    : activityRepository.findByIdInAndCourseIdAndSchoolIdAndIsDeletedFalse(activityIds, query.getCourseId(), schoolId, pageRequest));
        } else if (activityHall) {
            page = activityRepository.findForActivityHall(
                    query.getCourseId(), query.getClassroomId(), query.getType(),
                    StringUtils.hasText(query.getKeyword()) ? query.getKeyword() : null,
                    schoolId, pageRequest);
        } else {
            Long organizerId = query.getMyCreated() != null && query.getMyCreated() ? userId : null;
            page = activityRepository.findByFilters(organizerId, query.getCourseId(), query.getClassroomId(),
                    query.getStatus(), query.getType(), StringUtils.hasText(query.getKeyword()) ? query.getKeyword() : null,
                    Boolean.TRUE.equals(query.getSchoolLevelOnly()), schoolId, pageRequest);
        }

        List<ActivityVO> voList = page.getContent().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        for (ActivityVO vo : voList) {
            long count = participantRepository.countByActivityIdAndStatusConfirmed(vo.getId());
            vo.setParticipantCount((int) count);

            int maxParticipants = vo.getMaxParticipants() != null ? vo.getMaxParticipants() : 50;
            vo.setIsFull(count >= maxParticipants);

            if (userId != null) {
                vo.setIsOrganizer(vo.getOrganizerId().equals(userId));
                vo.setIsJoined(participantRepository.existsConfirmedByActivityIdAndTeacherId(vo.getId(), userId));
                vo.setHasCheckedIn(participantRepository.hasCheckedIn(vo.getId(), userId));
            } else {
                vo.setIsOrganizer(false);
                vo.setIsJoined(false);
                vo.setHasCheckedIn(false);
            }
        }

        return PageResult.of(voList, page.getTotalElements(), query.getPage(), query.getSize());
    }

    @Override
    @Transactional
    public void reviewActivity(Long id, ActivityReviewDTO dto, Long adminId) {
        TeachingActivity activity = getEntityById(id);
        adminScopeService.requireSchool(activity.getSchoolId());

        if (activity.getCourseId() != null || activity.getClassroomId() != null) {
            throw new BusinessException("课程或班级活动不走校级发布审核，请在风险与举报队列处置异常内容");
        }

        if (!TeachingActivity.ActivityStatus.PENDING.name().equals(activity.getStatus())
                && !TeachingActivity.ActivityStatus.REJECTED.name().equals(activity.getStatus())) {
            throw new BusinessException("只有待审核或已拒绝的活动可以重新审核");
        }

        if ("approve".equals(dto.getAction())) {
            activity.setStatus(TeachingActivity.ActivityStatus.APPROVED.name());
            log.info("教研活动审核通过: id={}, adminId={}", id, adminId);
        } else if ("reject".equals(dto.getAction())) {
            if (!StringUtils.hasText(dto.getRejectReason())) {
                throw new BusinessException("拒绝活动时必须填写拒绝原因");
            }
            activity.setStatus(TeachingActivity.ActivityStatus.REJECTED.name());
            activity.setRejectReason(dto.getRejectReason().trim());
            log.info("教研活动审核拒绝: id={}, adminId={}, reason={}", id, adminId, dto.getRejectReason());
        } else {
            throw new BusinessException("无效的审核操作");
        }

        activityRepository.save(activity);
        boolean approved = "approve".equals(dto.getAction());
        String reviewReason = !approved && StringUtils.hasText(dto.getRejectReason())
                ? "驳回原因：" + dto.getRejectReason().trim() : "";
        notificationService.sendNotification(
                activity.getOrganizerId(),
                approved ? "ACTIVITY_APPROVED" : "ACTIVITY_REJECTED",
                "教研活动审核" + (approved ? "通过" : "未通过"),
                "您创建的教研活动“" + activity.getTitle() + "”" + (approved ? "已通过审核。" : "未通过审核。") + reviewReason,
                activity.getId());
    }

    @Override
    @Transactional
    public void cancelActivity(Long id, Long userId, boolean isAdmin, String cancelReason) {
        TeachingActivity activity = getEntityById(id);
        requireActivitySchool(activity, userId);

        if (!isAdmin && !activity.getOrganizerId().equals(userId)) {
            throw new BusinessException("只有活动组织者或管理员可以取消");
        }

        if (activity.getActivityTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException("活动已经开始或已结束，无法取消");
        }

        activity.setStatus(TeachingActivity.ActivityStatus.CANCELLED.name());
        activity.setCancelReason(StringUtils.hasText(cancelReason) ? cancelReason.trim() : null);
        if (activity.getCancelReason() != null && activity.getCancelReason().length() > 500) {
            throw new BusinessException("取消原因不能超过500字");
        }
        activityRepository.save(activity);

        List<Long> participantIds = participantRepository.findByActivityIdAndStatusConfirmed(id).stream()
                .map(ActivityParticipant::getTeacherId)
                .collect(Collectors.toList());
        participantRepository.cancelByActivityId(id);
        activity.setCurrentParticipants(0);
        activityRepository.save(activity);

        String reasonText = StringUtils.hasText(activity.getCancelReason())
                ? "取消原因：" + activity.getCancelReason()
                : "请留意后续活动安排。";
        participantIds.stream()
                .filter(participantId -> !participantId.equals(userId))
                .forEach(participantId -> notificationService.sendNotification(
                        participantId,
                        "ACTIVITY_CANCELLED",
                        "教研活动已取消",
                        "您报名的“" + activity.getTitle() + "”已取消。" + reasonText,
                        activity.getId()
                ));
        log.info("取消教研活动: id={}, userId={}, isAdmin={}", id, userId, isAdmin);
    }

    // ========== 活动参与 ==========

    @Override
    @Transactional
    public void joinActivity(Long activityId, Long teacherId) {
        // 1. 检查活动是否存在
        TeachingActivity activity = getEntityById(activityId);
        requireActivitySchool(activity, teacherId);

        log.info("尝试参与教研活动: activityId={}, teacherId={}, activityStatus={}, currentParticipants={}, maxParticipants={}",
                activityId, teacherId, activity.getStatus(), activity.getCurrentParticipants(), activity.getMaxParticipants());

        // 2. 检查活动状态
        if (!TeachingActivity.ActivityStatus.APPROVED.name().equals(activity.getStatus())) {
            throw new BusinessException("活动未通过审核，无法参与");
        }

        // 3. Registration closes when the activity starts.
        LocalDateTime now = LocalDateTime.now();
        if (activity.getRegistrationDeadline() != null && now.isAfter(activity.getRegistrationDeadline())) {
            throw new BusinessException("报名截止时间已过");
        }
        if (!activity.getActivityTime().isAfter(now)) {
            throw new BusinessException("活动已开始，报名已关闭");
        }
        LocalDateTime activityEndTime = activity.getActivityTime().plusMinutes(activity.getDuration());
        if (!activityEndTime.isAfter(now)) {
            throw new BusinessException("活动已结束，无法参与");
        }

        // 4. 检查是否已满员
        int currentParticipants = activity.getCurrentParticipants() != null ? activity.getCurrentParticipants() : 0;
        int maxParticipants = activity.getMaxParticipants() != null ? activity.getMaxParticipants() : Integer.MAX_VALUE;
        if (currentParticipants >= maxParticipants) {
            throw new BusinessException("活动已满员，无法参与");
        }

        // 5. 检查是否有已确认参与的记录（CONFIRMED 状态）
        if (participantRepository.existsConfirmedByActivityIdAndTeacherId(activityId, teacherId)) {
            log.warn("用户已确认参与此活动: activityId={}, teacherId={}", activityId, teacherId);
            throw new BusinessException("您已参与此活动，请勿重复加入");
        }

        // 6. 检查是否有已取消的参与记录（CANCELLED 状态），允许重新激活
        Optional<ActivityParticipant> existingParticipant = participantRepository
                .findByActivityIdAndTeacherId(activityId, teacherId);

        if (existingParticipant.isPresent()) {
            ActivityParticipant participant = existingParticipant.get();

            // 如果是已取消状态，允许重新激活
            if ("CANCELLED".equals(participant.getStatus())) {
                participant.setStatus("CONFIRMED");
                participant.setJoinTime(LocalDateTime.now());
                participant.setCheckInTime(null);
                participant.setCreatedAt(LocalDateTime.now());
                participantRepository.save(participant);

                if (activityRepository.incrementParticipants(activityId) == 0) {
                    throw new BusinessException("活动已满员，无法重新报名");
                }

                log.info("教师重新参与教研活动（从取消状态恢复）: activityId={}, teacherId={}", activityId, teacherId);
                return;
            } else {
                log.warn("用户已有其他状态的参与记录: activityId={}, teacherId={}, status={}",
                        activityId, teacherId, participant.getStatus());
                throw new BusinessException("您已参与此活动，请勿重复加入");
            }
        }

        // 7. 添加新的参与记录
        ActivityParticipant participant = ActivityParticipant.builder()
                .activityId(activityId)
                .teacherId(teacherId)
                .status("CONFIRMED")
                .joinTime(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        try {
            participantRepository.save(participant);
            log.info("参与记录已保存: activityId={}, teacherId={}", activityId, teacherId);
        } catch (Exception e) {
            log.error("保存参与记录失败: activityId={}, teacherId={}, error={}", activityId, teacherId, e.getMessage());
            if (participantRepository.existsByActivityIdAndTeacherId(activityId, teacherId)) {
                throw new BusinessException("您已参与此活动");
            }
            throw new BusinessException("参与活动失败，请稍后重试");
        }

        // 8. 更新参与人数
        int updated = activityRepository.incrementParticipants(activityId);
        if (updated == 0) {
            log.warn("更新参与人数失败，可能活动已满员: activityId={}", activityId);
            participantRepository.delete(participant);
            throw new BusinessException("活动已满员，无法参与");
        }

        log.info("教师成功参与教研活动: activityId={}, teacherId={}", activityId, teacherId);
    }

    @Override
    @Transactional
    public void cancelJoinActivity(Long activityId, Long teacherId) {
        TeachingActivity activity = getEntityById(activityId);

        if (activity.getActivityTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException("活动已开始，无法取消参与");
        }

        if (!participantRepository.existsConfirmedByActivityIdAndTeacherId(activityId, teacherId)) {
            throw new BusinessException("您未参与此活动");
        }

        int updated = participantRepository.cancelParticipation(activityId, teacherId);
        if (updated == 0) {
            throw new BusinessException("取消参与失败");
        }

        activityRepository.decrementParticipants(activityId);
        log.info("教师取消参与教研活动: activityId={}, teacherId={}", activityId, teacherId);
    }

    @Override
    @Transactional
    public void checkIn(Long activityId, Long teacherId) {
        TeachingActivity activity = getEntityById(activityId);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endTime = activity.getActivityTime().plusMinutes(activity.getDuration());
        if (!(TeachingActivity.ActivityStatus.APPROVED.name().equals(activity.getStatus())
                || TeachingActivity.ActivityStatus.ONGOING.name().equals(activity.getStatus()))
                || now.isBefore(activity.getActivityTime()) || !now.isBefore(endTime)) {
            throw new BusinessException("仅可在活动进行期间签到");
        }

        ActivityParticipant participant = participantRepository
                .findByActivityIdAndTeacherId(activityId, teacherId)
                .orElseThrow(() -> new BusinessException("您未参与此活动"));

        if (!"CONFIRMED".equals(participant.getStatus())) {
            throw new BusinessException("您的参与状态异常，无法签到");
        }

        if (participant.getCheckInTime() != null) {
            throw new BusinessException("您已签到");
        }

        participant.setCheckInTime(LocalDateTime.now());
        participantRepository.save(participant);
        log.info("教师签到: activityId={}, teacherId={}", activityId, teacherId);
    }

    @Override
    @Transactional
    public void submitFeedback(Long activityId, Long teacherId, String feedback, Integer rating) {
        TeachingActivity activity = getEntityById(activityId);
        LocalDateTime endTime = activity.getActivityTime().plusMinutes(activity.getDuration());
        if (!LocalDateTime.now().isAfter(endTime) && !LocalDateTime.now().isEqual(endTime)) {
            throw new BusinessException("活动结束后才可提交反馈");
        }
        if (!StringUtils.hasText(feedback) || feedback.trim().length() > 1000) {
            throw new BusinessException("反馈内容不能为空且不能超过1000字");
        }
        if (rating == null || rating < 1 || rating > 5) {
            throw new BusinessException("评分必须为1到5分");
        }

        ActivityParticipant participant = participantRepository
                .findByActivityIdAndTeacherId(activityId, teacherId)
                .orElseThrow(() -> new BusinessException("您未参与此活动"));

        if (!"CONFIRMED".equals(participant.getStatus()) || participant.getCheckInTime() == null) {
            throw new BusinessException("完成签到后才可提交反馈");
        }

        participant.setFeedback(feedback.trim());
        participant.setRating(rating);
        participantRepository.save(participant);
        log.info("提交活动反馈: activityId={}, teacherId={}", activityId, teacherId);
    }

    @Override
    public boolean isParticipant(Long activityId, Long userId) {
        return participantRepository.existsConfirmedByActivityIdAndTeacherId(activityId, userId);
    }

    @Override
    public boolean hasEverParticipated(Long activityId, Long userId) {
        return participantRepository.existsByActivityIdAndTeacherId(activityId, userId);
    }

    @Override
    public Map<String, Object> getCheckinStats(Long activityId, Long userId, boolean isAdmin) {
        assertVisibleActivity(getEntityById(activityId), userId, isAdmin);
        long checkedInCount = participantRepository.countCheckedInByActivityId(activityId);
        long totalParticipants = participantRepository.countByActivityIdAndStatusConfirmed(activityId);
        double checkinRate = totalParticipants > 0 ? (checkedInCount * 100.0 / totalParticipants) : 0;

        Map<String, Object> stats = new HashMap<>();
        stats.put("checkedInCount", checkedInCount);
        stats.put("totalParticipants", totalParticipants);
        stats.put("checkinRate", Math.round(checkinRate * 10) / 10.0);

        return stats;
    }

    // ========== 活动讨论 ==========

    @Override
    @Transactional
    public Long addDiscussion(DiscussionCreateDTO dto, Long teacherId) {
        TeachingActivity activity = getEntityById(dto.getActivityId());

        if (!List.of(
                TeachingActivity.ActivityStatus.APPROVED.name(),
                TeachingActivity.ActivityStatus.ONGOING.name(),
                TeachingActivity.ActivityStatus.COMPLETED.name(),
                TeachingActivity.ActivityStatus.ENDED_PENDING_ARCHIVE.name()
        ).contains(activity.getStatus())) {
            throw new BusinessException("仅已通过、进行中或已完成的活动可参与讨论");
        }

        ActivityDiscussion discussion = ActivityDiscussion.builder()
                .activityId(dto.getActivityId())
                .teacherId(teacherId)
                .content(dto.getContent())
                .parentId(dto.getParentId() != null ? dto.getParentId() : 0L)
                .build();

        if (dto.getParentId() != null && dto.getParentId() != 0L) {
            ActivityDiscussion parent = discussionRepository.findByIdAndIsDeletedFalse(dto.getParentId())
                    .orElseThrow(() -> new BusinessException("回复的评论不存在或已删除"));
            if (!dto.getActivityId().equals(parent.getActivityId())) {
                throw new BusinessException("回复评论必须属于当前活动");
            }
        }

        discussionRepository.save(discussion);
        log.info("发表评论: activityId={}, teacherId={}", dto.getActivityId(), teacherId);

        return discussion.getId();
    }

    @Override
    @Transactional
    public void deleteDiscussion(Long id, Long userId) {
        ActivityDiscussion discussion = discussionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("评论", id));

        if (!discussion.getTeacherId().equals(userId)) {
            User user = userRepository.findById(userId).orElse(null);
            // 修复：使用 User 实体的 role 字段进行比较
            if (user == null) {
                throw new BusinessException("无权删除此评论");
            }
            String role = user.getRole() != null ? user.getRole().name() : "";
            if (!"ADMIN".equals(role) && !"SUPER_ADMIN".equals(role)) {
                throw new BusinessException("无权删除此评论");
            }
        }

        discussionRepository.softDelete(id);
        log.info("删除评论: id={}, userId={}", id, userId);
    }

    @Override
    @Transactional
    public void likeDiscussion(Long id, Long userId) {
        ActivityDiscussion discussion = discussionRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("评论", id));
        if (discussionLikeRepository.existsByDiscussionIdAndTeacherId(id, userId)) {
            throw new BusinessException("您已经点赞过该评论");
        }
        discussionLikeRepository.save(ActivityDiscussionLike.builder()
                .discussionId(id)
                .teacherId(userId)
                .build());
        if (discussionRepository.incrementLikeCount(id) == 0) {
            throw new NotFoundException("评论", id);
        }
    }

    @Override
    public PageResult<DiscussionVO> getDiscussions(Long activityId, Integer page, Integer size, Long userId, boolean isAdmin) {
        assertVisibleActivity(getEntityById(activityId), userId, isAdmin);
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by("createdAt").descending());

        Page<ActivityDiscussion> pageResult = discussionRepository
                .findByActivityIdAndParentIdAndIsDeletedFalse(activityId, 0L, pageRequest);

        List<DiscussionVO> voList = pageResult.getContent().stream()
                .map(this::convertToDiscussionVO)
                .collect(Collectors.toList());

        for (DiscussionVO vo : voList) {
            List<ActivityDiscussion> replies = discussionRepository
                    .findByParentIdAndIsDeletedFalseOrderByCreatedAtAsc(vo.getId());
            List<DiscussionVO> replyVOs = replies.stream()
                    .map(this::convertToDiscussionVO)
                    .collect(Collectors.toList());
            vo.setReplies(replyVOs);
        }

        return PageResult.of(voList, pageResult.getTotalElements(), page, size);
    }

    // ========== 辅助方法 ==========

    @Override
    @Transactional
    public void incrementViewCount(Long id) {
        activityRepository.incrementViewCount(id);
    }

    private TeachingActivity getEntityById(Long id) {
        return activityRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("教研活动", id));
    }

    private void assertVisibleActivity(TeachingActivity activity, Long userId, boolean isAdmin) {
        boolean privateStatus = TeachingActivity.ActivityStatus.PENDING.name().equals(activity.getStatus())
                || TeachingActivity.ActivityStatus.REJECTED.name().equals(activity.getStatus());
        if (privateStatus && !isAdmin && (userId == null || !activity.getOrganizerId().equals(userId))) {
            throw new NotFoundException("教研活动", activity.getId());
        }
    }

    @Override
    @Transactional
    public void archiveActivity(Long id, Long userId, boolean isAdmin) {
        TeachingActivity activity = getEntityById(id);
        requireActivitySchool(activity, userId);
        if (!isAdmin && !activity.getOrganizerId().equals(userId)) throw new BusinessException("只有组织者或管理员可以归档");
        if (!TeachingActivity.ActivityStatus.ENDED_PENDING_ARCHIVE.name().equals(activity.getStatus())) {
            throw new BusinessException("活动必须处于待归档状态");
        }
        activity.setStatus(TeachingActivity.ActivityStatus.COMPLETED.name());
        activity.setUpdatedBy(userId);
        activityRepository.save(activity);
    }

    private void notifyAdminsAboutPendingActivity(TeachingActivity activity, Long organizerId) {
        List<User> admins = new ArrayList<>(userRepository.findAllByRoleAndStatus(
                User.UserRole.ADMIN, User.UserStatus.ACTIVE));
        admins.addAll(userRepository.findAllByRoleAndStatus(
                User.UserRole.SUPER_ADMIN, User.UserStatus.ACTIVE));
        admins.stream()
                .filter(admin -> !admin.getId().equals(organizerId))
                .filter(admin -> admin.getRole() == User.UserRole.SUPER_ADMIN
                        || (activity.getSchoolId() != null && activity.getSchoolId().equals(admin.getSchoolId())))
                .forEach(admin -> notificationService.sendNotification(
                        admin.getId(),
                        "ACTIVITY_PENDING",
                        "有新的教研活动待审核",
                        "教师提交了教研活动“" + activity.getTitle() + "”，请前往管理中心的审核中心处理。",
                        activity.getId()));
    }

    private ContentRiskService.RiskAssessment assessActivityRisk(TeachingActivity activity) {
        return contentRiskService.assess(activity.getTitle(), activity.getOrganizerUnit(), activity.getSponsor(),
                activity.getContent(), activity.getLocation());
    }

    private void queueActivityRisk(TeachingActivity activity, User organizer,
                                   ContentRiskService.RiskAssessment risk) {
        ContentAudit audit = contentAuditService.createOrEscalate(
                ContentAudit.ContentType.ACTIVITY, activity.getId(), activity.getTitle(), activity.getContent(),
                organizer.getId(), displayName(organizer), activity.getSchoolId(), risk.level(), risk.reason());
        List<User> admins = new ArrayList<>(userRepository.findAllByRoleAndStatus(
                User.UserRole.ADMIN, User.UserStatus.ACTIVE));
        admins.addAll(userRepository.findAllByRoleAndStatus(
                User.UserRole.SUPER_ADMIN, User.UserStatus.ACTIVE));
        admins.stream()
                .filter(admin -> admin.getRole() == User.UserRole.SUPER_ADMIN
                        || (activity.getSchoolId() != null && activity.getSchoolId().equals(admin.getSchoolId())))
                .forEach(admin -> notificationService.sendNotification(
                        admin.getId(), "MODERATION_RISK", "课程活动触发风险复核",
                        "课程或班级活动“" + activity.getTitle() + "”触发风险规则，请前往管理中心的风险与举报队列处理。",
                        audit.getId()));
    }

    private String displayName(User user) {
        return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
    }

    private void normalizeRegistrationDeadline(TeachingActivity activity) {
        if (activity.getActivityTime() == null) return;
        if (activity.getRegistrationDeadline() == null || activity.getRegistrationDeadline().isAfter(activity.getActivityTime())) {
            activity.setRegistrationDeadline(activity.getActivityTime());
        }
    }

    private void validateScope(Long courseId, Long classroomId, Long userId, boolean isAdmin) {
        if (courseId == null && classroomId == null) return;
        if (courseId != null) {
            Course course = courseRepository.findById(courseId).orElseThrow(() -> new NotFoundException("课程", courseId));
            User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("用户", userId));
            if (user.getRole() != User.UserRole.SUPER_ADMIN
                    && (user.getSchoolId() == null || course.getSchoolId() == null || !user.getSchoolId().equals(course.getSchoolId()))) {
                throw new BusinessException("课程与当前学校不一致");
            }
            if (!isAdmin && course.getCreatedBy() != null && !course.getCreatedBy().equals(userId)) {
                throw new BusinessException("无权使用该课程范围");
            }
        }
        if (classroomId != null) {
            Classroom classroom = classroomRepository.findById(classroomId).orElseThrow(() -> new NotFoundException("课堂", classroomId));
            if (courseId != null && !courseId.equals(classroom.getCourseId())) throw new BusinessException("课堂必须属于所选课程");
            if (!isAdmin && classroom.getTeacherId() != null && !classroom.getTeacherId().equals(userId)) throw new BusinessException("无权使用该课堂范围");
        }
    }

    private void requireActivitySchool(TeachingActivity activity, Long userId) {
        if (userId == null) return;
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("用户", userId));
        if (user.getRole() == User.UserRole.SUPER_ADMIN) return;
        if (user.getSchoolId() == null || activity.getSchoolId() == null
                || !user.getSchoolId().equals(activity.getSchoolId())) {
            throw new NotFoundException("教研活动", activity.getId());
        }
    }

    private Long schoolScopeId(Long userId) {
        if (userId == null) return null;
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getRole() == User.UserRole.SUPER_ADMIN) return null;
        return user.getSchoolId() == null ? -1L : user.getSchoolId();
    }

    private boolean isPlatformUser(Long userId) {
        return userRepository.findById(userId)
                .map(user -> user.getRole() == User.UserRole.SUPER_ADMIN)
                .orElse(false);
    }

    private UserVO getUserVO(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return null;
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

    private ActivityOrganizerVO getOrganizerVO(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return null;
        ActivityOrganizerVO vo = new ActivityOrganizerVO();
        vo.setId(user.getId());
        vo.setNickname(StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername());
        vo.setAvatar(user.getAvatar());
        return vo;
    }

    private ActivityVO convertToVO(TeachingActivity activity) {
        ActivityVO vo = new ActivityVO();
        BeanUtils.copyProperties(activity, vo);

        // 安全获取枚举值
        try {
            vo.setStatusName(TeachingActivity.ActivityStatus.valueOf(activity.getStatus()).getDescription());
        } catch (IllegalArgumentException e) {
            vo.setStatusName(activity.getStatus());
        }

        try {
            vo.setTypeName(TeachingActivity.ActivityType.valueOf(activity.getType()).getDescription());
        } catch (IllegalArgumentException e) {
            vo.setTypeName(activity.getType());
        }

        ActivityOrganizerVO organizer = getOrganizerVO(activity.getOrganizerId());
        vo.setOrganizer(organizer);

        return vo;
    }

    private DiscussionVO convertToDiscussionVO(ActivityDiscussion discussion) {
        DiscussionVO vo = new DiscussionVO();
        BeanUtils.copyProperties(discussion, vo);

        UserVO teacher = getUserVO(discussion.getTeacherId());
        vo.setTeacher(teacher);

        return vo;
    }
}
