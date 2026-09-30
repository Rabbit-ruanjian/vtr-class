package com.vtr.controller;

import com.vtr.common.Result;
import com.vtr.common.exception.BusinessException;
import com.vtr.common.exception.NotFoundException;
import com.vtr.entity.ActivityOutcome;
import com.vtr.entity.ActivityAttachment;
import com.vtr.entity.Courseware;
import com.vtr.entity.ResearchTask;
import com.vtr.entity.TeachingActivity;
import com.vtr.repository.ActivityOutcomeRepository;
import com.vtr.repository.ActivityAttachmentRepository;
import com.vtr.repository.CoursewareRepository;
import com.vtr.repository.ActivityParticipantRepository;
import com.vtr.repository.ResearchTaskRepository;
import com.vtr.repository.TeachingActivityRepository;
import com.vtr.security.CustomUserDetails;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/teaching-activities")
@RequiredArgsConstructor
public class ActivityOutcomeController {
    private final TeachingActivityRepository activityRepository;
    private final ActivityOutcomeRepository outcomeRepository;
    private final ActivityAttachmentRepository attachmentRepository;
    private final ResearchTaskRepository taskRepository;
    private final CoursewareRepository coursewareRepository;
    private final ActivityParticipantRepository participantRepository;

    @GetMapping("/{activityId}/outcomes")
    @PreAuthorize("isAuthenticated()")
    public Result<List<ActivityOutcome>> list(@PathVariable Long activityId) {
        requireViewer(activityId);
        return Result.success(outcomeRepository.findByActivityIdOrderByCreatedAtDesc(activityId));
    }

    @GetMapping("/{activityId}/attachments")
    @PreAuthorize("isAuthenticated()")
    public Result<List<ActivityAttachment>> attachments(@PathVariable Long activityId) {
        requireViewer(activityId);
        return Result.success(attachmentRepository.findByActivityId(activityId));
    }

    @PostMapping("/{activityId}/attachments")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    @Transactional
    public Result<Long> addAttachment(@PathVariable Long activityId, @RequestBody @Valid AttachmentRequest request) {
        TeachingActivity activity = requireOwner(activityId);
        requireOutcomeWindow(activity);
        ActivityAttachment attachment = ActivityAttachment.builder()
                .activityId(activityId)
                .fileName(request.getFileName().trim())
                .fileUrl(request.getFileUrl().trim())
                .fileSize(request.getFileSize())
                .fileType(request.getFileType())
                .uploadBy(currentUser().getId())
                .build();
        return Result.success(attachmentRepository.save(attachment).getId());
    }

    @DeleteMapping("/{activityId}/attachments/{attachmentId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    @Transactional
    public Result<Void> deleteAttachment(@PathVariable Long activityId, @PathVariable Long attachmentId) {
        requireOwner(activityId);
        ActivityAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new NotFoundException("活动附件", attachmentId));
        if (!activityId.equals(attachment.getActivityId())) throw new BusinessException(400, "附件不属于该活动");
        attachmentRepository.delete(attachment);
        return Result.success();
    }

    @PostMapping("/{activityId}/outcomes")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    @Transactional
    public Result<Long> submit(@PathVariable Long activityId, @RequestBody @Valid OutcomeRequest request) {
        TeachingActivity activity = requireOwner(activityId);
        requireOutcomeWindow(activity);
        ActivityOutcome outcome = new ActivityOutcome();
        outcome.setActivity(activity);
        outcome.setTitle(request.getTitle().trim());
        outcome.setSummary(request.getSummary().trim());
        outcome.setType(request.getType().trim().toUpperCase());
        outcome.setCreatedBy(currentUser().getId());
        if (request.getTaskId() != null) {
            ResearchTask task = taskRepository.findById(request.getTaskId()).orElseThrow(() -> new NotFoundException("教研任务", request.getTaskId()));
            requireSameCourse(activity, task.getCourse().getId());
            outcome.setTask(task);
        }
        if (request.getResourceId() != null) {
            Courseware resource = coursewareRepository.findById(request.getResourceId()).orElseThrow(() -> new NotFoundException("课程资源", request.getResourceId()));
            requireSameCourse(activity, resource.getCourseId());
            outcome.setResource(resource);
        }
        return Result.success(outcomeRepository.save(outcome).getId());
    }

    @PostMapping("/{activityId}/outcomes/{outcomeId}/review")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    @Transactional
    public Result<Void> review(@PathVariable Long activityId, @PathVariable Long outcomeId, @RequestBody ReviewRequest request) {
        requireOwner(activityId);
        ActivityOutcome outcome = outcomeRepository.findById(outcomeId).orElseThrow(() -> new NotFoundException("活动成果", outcomeId));
        if (!activityId.equals(outcome.getActivity().getId())) throw new BusinessException(400, "成果不属于该活动");
        outcome.setStatus(request.isApproved() ? "APPROVED" : "RETURNED");
        outcome.setReviewComment(request.getComment());
        outcome.setReviewedBy(currentUser().getId());
        outcome.setReviewedAt(LocalDateTime.now());
        return Result.success();
    }

    @PostMapping("/{activityId}/complete")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    @Transactional
    public Result<Void> complete(@PathVariable Long activityId) {
        TeachingActivity activity = requireOwner(activityId);
        if (!TeachingActivity.ActivityStatus.ENDED_PENDING_ARCHIVE.name().equals(activity.getStatus())
                && !TeachingActivity.ActivityStatus.ONGOING.name().equals(activity.getStatus())) {
            throw new BusinessException(400, "只有进行中或已结束待归档的活动可以归档");
        }
        LocalDateTime endTime = activity.getActivityTime().plusMinutes(activity.getDuration());
        if (LocalDateTime.now().isBefore(endTime) && !currentUser().isAdmin()) throw new BusinessException(400, "活动结束后才能归档完成");
        if (outcomeRepository.countByActivityIdAndStatus(activityId, "APPROVED") == 0) {
            throw new BusinessException(400, "至少需要一份审核通过的活动成果才能完成归档");
        }
        activity.setStatus(TeachingActivity.ActivityStatus.COMPLETED.name());
        return Result.success();
    }

    private TeachingActivity requireActivity(Long id) {
        return activityRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("教研活动", id));
    }

    private TeachingActivity requireOwner(Long id) {
        TeachingActivity activity = requireActivity(id);
        if (!currentUser().isAdmin() && !currentUser().getId().equals(activity.getOrganizerId())) {
            throw new BusinessException(403, "仅活动组织者可执行此操作");
        }
        return activity;
    }

    private TeachingActivity requireViewer(Long id) {
        TeachingActivity activity = requireActivity(id);
        CustomUserDetails user = currentUser();
        boolean viewer = user.isAdmin()
                || user.getId().equals(activity.getOrganizerId())
                || participantRepository.existsConfirmedByActivityIdAndTeacherId(id, user.getId());
        if (!viewer) throw new BusinessException(403, "仅活动组织者、管理员或参与者可以查看活动成果");
        return activity;
    }

    private void requireSameCourse(TeachingActivity activity, Long courseId) {
        if (activity.getCourseId() == null || courseId == null || !activity.getCourseId().equals(courseId)) {
            throw new BusinessException(400, "活动成果、任务和资源必须属于同一课程组");
        }
    }

    private void requireOutcomeWindow(TeachingActivity activity) {
        if (!TeachingActivity.ActivityStatus.APPROVED.name().equals(activity.getStatus())
                && !TeachingActivity.ActivityStatus.ONGOING.name().equals(activity.getStatus())
                && !TeachingActivity.ActivityStatus.ENDED_PENDING_ARCHIVE.name().equals(activity.getStatus())) {
            throw new BusinessException(400, "当前活动状态不允许提交成果或附件");
        }
    }

    private CustomUserDetails currentUser() {
        return (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    @Data
    public static class OutcomeRequest {
        @NotBlank private String title;
        @NotBlank private String summary;
        @NotBlank private String type;
        private Long taskId;
        private Long resourceId;
    }

    @Data
    public static class ReviewRequest { private boolean approved; private String comment; }

    @Data
    public static class AttachmentRequest {
        @NotBlank private String fileName;
        @NotBlank private String fileUrl;
        private Long fileSize;
        private String fileType;
    }
}
