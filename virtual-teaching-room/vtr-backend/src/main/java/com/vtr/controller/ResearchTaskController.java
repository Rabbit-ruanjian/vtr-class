package com.vtr.controller;

import com.vtr.common.Result;
import com.vtr.common.exception.BusinessException;
import com.vtr.common.exception.NotFoundException;
import com.vtr.entity.Course;
import com.vtr.entity.CourseMember;
import com.vtr.entity.Courseware;
import com.vtr.entity.ResearchTask;
import com.vtr.entity.ResearchTaskContribution;
import com.vtr.entity.TeachingActivity;
import com.vtr.repository.CourseMemberRepository;
import com.vtr.repository.CourseRepository;
import com.vtr.repository.CoursewareRepository;
import com.vtr.repository.ResearchTaskContributionRepository;
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
import javax.validation.constraints.NotNull;
import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/research-tasks")
@RequiredArgsConstructor
public class ResearchTaskController {
    private final ResearchTaskRepository taskRepository;
    private final ResearchTaskContributionRepository contributionRepository;
    private final CourseRepository courseRepository;
    private final CourseMemberRepository courseMemberRepository;
    private final TeachingActivityRepository activityRepository;
    private final CoursewareRepository coursewareRepository;

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    @Transactional
    public Result<Long> create(@RequestBody @Valid CreateTaskRequest request) {
        Course course = requireManageableCourse(request.getCourseId());
        ResearchTask task = new ResearchTask();
        task.setCourse(course);
        task.setTitle(request.getTitle().trim());
        task.setDescription(request.getDescription().trim());
        task.setType(request.getType().trim().toUpperCase());
        task.setDueAt(request.getDueAt());
        task.setCreatedBy(currentUser().getId());
        if (request.getActivityId() != null) {
            TeachingActivity activity = activityRepository.findByIdAndIsDeletedFalse(request.getActivityId())
                    .orElseThrow(() -> new NotFoundException("教研活动", request.getActivityId()));
            if (!TeachingActivity.ActivityStatus.APPROVED.name().equals(activity.getStatus())
                    && !TeachingActivity.ActivityStatus.ONGOING.name().equals(activity.getStatus())
                    && !TeachingActivity.ActivityStatus.ENDED_PENDING_ARCHIVE.name().equals(activity.getStatus())
                    && !TeachingActivity.ActivityStatus.COMPLETED.name().equals(activity.getStatus())) {
                throw new BusinessException(400, "只能关联已通过审核的有效活动");
            }
            if (activity.getCourseId() == null || !course.getId().equals(activity.getCourseId())) {
                throw new BusinessException(400, "教研任务只能关联本课程组的活动");
            }
            task.setActivity(activity);
        }
        return Result.success(taskRepository.save(task).getId());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    public Result<List<ResearchTask>> list(@RequestParam Long courseId) {
        requireCourseMember(courseId);
        return Result.success(taskRepository.findByCourseIdOrderByCreatedAtDesc(courseId));
    }

    @PostMapping("/{id}/claim")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    @Transactional
    public Result<Void> claim(@PathVariable Long id) {
        ResearchTask task = requireOpenTask(id);
        requireCourseMember(task.getCourse().getId());
        contributionRepository.findByTaskIdAndContributorId(id, currentUser().getId()).orElseGet(() -> {
            ResearchTaskContribution contribution = new ResearchTaskContribution();
            contribution.setTask(task);
            contribution.setContributorId(currentUser().getId());
            return contributionRepository.save(contribution);
        });
        return Result.success();
    }

    @PostMapping("/{id}/submissions")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    @Transactional
    public Result<Void> submit(@PathVariable Long id, @RequestBody @Valid SubmitTaskRequest request) {
        ResearchTask task = requireOpenTask(id);
        requireCourseMember(task.getCourse().getId());
        ResearchTaskContribution contribution = contributionRepository.findByTaskIdAndContributorId(id, currentUser().getId())
                .orElseThrow(() -> new BusinessException(400, "请先认领任务再提交成果"));
        contribution.setContent(request.getContent().trim());
        contribution.setSubmittedAt(LocalDateTime.now());
        contribution.setStatus("SUBMITTED");
        if (request.getResourceId() != null) {
            Courseware resource = coursewareRepository.findById(request.getResourceId())
                    .orElseThrow(() -> new NotFoundException("课程资源", request.getResourceId()));
            if (!task.getCourse().getId().equals(resource.getCourseId())) {
                throw new BusinessException(400, "提交成果必须属于当前课程组");
            }
            contribution.setResource(resource);
        }
        return Result.success();
    }

    @GetMapping("/{id}/submissions")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    public Result<List<ResearchTaskContribution>> submissions(@PathVariable Long id) {
        ResearchTask task = taskRepository.findById(id).orElseThrow(() -> new NotFoundException("教研任务", id));
        requireCourseMember(task.getCourse().getId());
        return Result.success(contributionRepository.findByTaskIdOrderByClaimedAtAsc(id));
    }

    @PostMapping("/{id}/submissions/{contributionId}/review")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    @Transactional
    public Result<Void> review(@PathVariable Long id, @PathVariable Long contributionId,
                               @RequestBody ReviewTaskRequest request) {
        ResearchTask task = taskRepository.findById(id).orElseThrow(() -> new NotFoundException("教研任务", id));
        requireManageableCourse(task.getCourse().getId());
        ResearchTaskContribution contribution = contributionRepository.findById(contributionId)
                .orElseThrow(() -> new NotFoundException("任务成果", contributionId));
        if (!id.equals(contribution.getTask().getId())) throw new BusinessException(400, "成果不属于该教研任务");
        if (!"SUBMITTED".equals(contribution.getStatus())) throw new BusinessException(400, "仅可评审已提交成果");
        contribution.setStatus(request.isApproved() ? "APPROVED" : "RETURNED");
        contribution.setReviewComment(request.getComment());
        contribution.setReviewedBy(currentUser().getId());
        contribution.setReviewedAt(LocalDateTime.now());
        return Result.success();
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    @Transactional
    public Result<Void> archive(@PathVariable Long id) {
        ResearchTask task = taskRepository.findById(id).orElseThrow(() -> new NotFoundException("教研任务", id));
        requireManageableCourse(task.getCourse().getId());
        task.setStatus("ARCHIVED");
        task.setArchivedAt(LocalDateTime.now());
        return Result.success();
    }

    private ResearchTask requireOpenTask(Long id) {
        ResearchTask task = taskRepository.findById(id).orElseThrow(() -> new NotFoundException("教研任务", id));
        if (!"PUBLISHED".equals(task.getStatus())) throw new BusinessException(400, "当前任务不可认领或提交");
        return task;
    }

    private Course requireManageableCourse(Long courseId) {
        Course course = courseRepository.findById(courseId).orElseThrow(() -> new NotFoundException("课程组", courseId));
        CustomUserDetails user = currentUser();
        if (!user.isAdmin() && !user.getId().equals(course.getCreatedBy())) throw new BusinessException(403, "仅课程组负责人可执行此操作");
        return course;
    }

    private void requireCourseMember(Long courseId) {
        Course course = courseRepository.findById(courseId).orElseThrow(() -> new NotFoundException("课程组", courseId));
        CustomUserDetails user = currentUser();
        boolean member = courseMemberRepository.findByCourseIdAndUserId(courseId, user.getId())
                .map(item -> "ACTIVE".equals(item.getStatus())).orElse(false);
        if (!user.isAdmin() && !user.getId().equals(course.getCreatedBy()) && !member) throw new BusinessException(403, "仅课程组成员可访问");
    }

    private CustomUserDetails currentUser() {
        return (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    @Data
    public static class CreateTaskRequest {
        @NotNull private Long courseId;
        private Long activityId;
        @NotBlank private String title;
        @NotBlank private String description;
        @NotBlank private String type;
        private LocalDateTime dueAt;
    }

    @Data
    public static class SubmitTaskRequest {
        @NotBlank private String content;
        private Long resourceId;
    }

    @Data
    public static class ReviewTaskRequest {
        private boolean approved;
        private String comment;
    }
}
