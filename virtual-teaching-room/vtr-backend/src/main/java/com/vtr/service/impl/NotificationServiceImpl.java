package com.vtr.service.impl;

import com.vtr.common.PageResult;
import com.vtr.entity.Assignment;
import com.vtr.entity.Notification;
import com.vtr.repository.AssignmentRepository;
import com.vtr.repository.ClassroomStudentRelationRepository;
import com.vtr.repository.NotificationRepository;
import com.vtr.service.NotificationService;
import com.vtr.vo.NotificationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final AssignmentRepository assignmentRepository;
    private final ClassroomStudentRelationRepository classroomStudentRelationRepository;
    private static final DateTimeFormatter NOTIFICATION_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    public PageResult<NotificationVO> getMyNotifications(Long userId, Integer page, Integer size,
                                                         String category, Boolean unreadOnly) {
        List<String> types = categoryTypes(category);
        boolean onlyUnread = Boolean.TRUE.equals(unreadOnly);
        if (types != null && types.isEmpty()) {
            return PageResult.of(Collections.emptyList(), 0L, page, size);
        }

        List<Notification> filtered = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(notification -> isVisibleToStudent(notification, userId))
                .filter(notification -> types == null || types.contains(notification.getType()))
                .filter(notification -> !onlyUnread || !Boolean.TRUE.equals(notification.getIsRead()))
                .sorted(Comparator.comparing(Notification::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, size);
        int fromIndex = Math.min((safePage - 1) * safeSize, filtered.size());
        int toIndex = Math.min(fromIndex + safeSize, filtered.size());
        List<NotificationVO> voList = filtered.subList(fromIndex, toIndex).stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return PageResult.of(voList, (long) filtered.size(), safePage, safeSize);
    }

    private List<String> categoryTypes(String category) {
        if (category == null || category.isBlank() || "all".equalsIgnoreCase(category)) return null;
        switch (category.trim().toLowerCase()) {
            case "assignment":
                return Arrays.asList("ASSIGNMENT_PUBLISHED", "ASSIGNMENT_DEADLINE_REMINDER",
                        "ASSIGNMENT_SUBMITTED", "SUBMISSION_GRADED");
            case "exam":
                return Collections.singletonList("EXAM_PUBLISHED");
            case "resource":
                return Arrays.asList("RESOURCE_PENDING", "RESOURCE_APPROVED", "RESOURCE_REJECTED",
                        "RESOURCE_PUBLISHED", "OUTLINE_ARCHIVED");
            case "forum":
                return Arrays.asList("FORUM_POST_PENDING", "FORUM_COMMENT_PENDING", "FORUM_REPLY",
                        "FORUM_POST_APPROVED", "FORUM_POST_REJECTED", "FORUM_COMMENT_APPROVED",
                        "FORUM_COMMENT_REJECTED");
            case "activity":
                return Arrays.asList("ACTIVITY_PENDING", "ACTIVITY_APPROVED", "ACTIVITY_REJECTED",
                        "ACTIVITY_CANCELLED");
            case "system":
                return Arrays.asList("SYSTEM", "COURSE_MEMBER_ADDED", "COURSE_MEMBER_ROLE_UPDATED",
                        "COURSE_MEMBER_REMOVED");
            default:
                return Collections.emptyList();
        }
    }

    @Override
    public long getUnreadCount(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(notification -> !Boolean.TRUE.equals(notification.getIsRead()))
                .filter(notification -> isVisibleToStudent(notification, userId))
                .count();
    }

    /** 过滤历史上因教师跨课程取学生而误发的作业通知。归档课程只要学生曾加入过仍可见。 */
    private boolean isVisibleToStudent(Notification notification, Long userId) {
        if (!Arrays.asList("ASSIGNMENT_PUBLISHED", "ASSIGNMENT_DEADLINE_REMINDER")
                .contains(notification.getType())) {
            return true;
        }

        if (notification.getRelatedId() == null) return false;
        Assignment assignment = assignmentRepository.findById(notification.getRelatedId()).orElse(null);
        if (assignment == null || assignment.getCourseId() == null) return assignment != null;
        return classroomStudentRelationRepository.existsByStudentIdAndCourseId(userId, assignment.getCourseId());
    }

    @Override
    @Transactional
    public void markAsRead(Long id, Long userId) {
        notificationRepository.markAsRead(id, userId);
    }

    @Override
    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsRead(userId);
    }

    @Override
    @Transactional
    public void deleteNotification(Long id, Long userId) {
        notificationRepository.deleteByIdAndUserId(id, userId);
    }

    @Override
    @Transactional
    public void deleteNotifications(List<Long> ids, Long userId) {
        if (ids == null || ids.isEmpty()) return;
        notificationRepository.deleteByIdsAndUserId(ids, userId);
    }

    @Override
    @Transactional
    public void sendNotification(Long userId, String type, String title, String content, Long relatedId) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .content(content)
                .relatedId(relatedId)
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();
        notificationRepository.save(notification);
        log.info("发送通知: userId={}, type={}, title={}", userId, type, title);
    }

    @Override
    public void sendGradeNotification(Long studentId, String assignmentTitle, Integer score, Long submissionId) {
        String title = "作业评分通知";
        String content = String.format("您提交的作业《%s》已被评分，得分：%d分", assignmentTitle, score);
        sendNotification(studentId, "SUBMISSION_GRADED", title, content, submissionId);
    }

    @Override
    public void sendAssignmentPublishedNotification(Long studentId, String assignmentTitle, Long assignmentId) {
        if (notificationRepository.existsByUserIdAndTypeAndRelatedId(studentId, "ASSIGNMENT_PUBLISHED", assignmentId)) {
            return;
        }
        String title = "作业发布通知";
        String content = String.format("老师发布了作业《%s》，请及时查看并提交", assignmentTitle);
        sendNotification(studentId, "ASSIGNMENT_PUBLISHED", title, content, assignmentId);
    }

    @Override
    public void sendAssignmentPublishedNotification(Long studentId, String assignmentTitle,
                                                    Long assignmentId, LocalDateTime deadline) {
        if (notificationRepository.existsByUserIdAndTypeAndRelatedId(studentId, "ASSIGNMENT_PUBLISHED", assignmentId)) {
            return;
        }
        String title = "作业发布通知";
        String content = String.format("老师发布了作业《%s》，请于 %s 前完成并提交",
                assignmentTitle, formatNotificationTime(deadline));
        sendNotification(studentId, "ASSIGNMENT_PUBLISHED", title, content, assignmentId);
    }

    @Override
    public void sendAssignmentDeadlineReminder(Long studentId, String assignmentTitle,
                                               Long assignmentId, LocalDateTime deadline) {
        if (notificationRepository.existsByUserIdAndTypeAndRelatedId(studentId,
                "ASSIGNMENT_DEADLINE_REMINDER", assignmentId)) {
            return;
        }
        String title = "作业即将截止";
        String content = String.format("作业《%s》将于 %s 截止，请及时完成并提交",
                assignmentTitle, formatNotificationTime(deadline));
        sendNotification(studentId, "ASSIGNMENT_DEADLINE_REMINDER", title, content, assignmentId);
    }

    @Override
    public void sendAssignmentSubmissionNotification(Long teacherId, String studentName,
                                                     String assignmentTitle, Long assignmentId) {
        String title = "收到新的作业提交";
        Notification unread = notificationRepository
                .findFirstByUserIdAndTypeAndRelatedIdAndIsReadFalseOrderByCreatedAtDesc(
                        teacherId, "ASSIGNMENT_SUBMITTED", assignmentId);
        if (unread != null) {
            unread.setContent(String.format("作业《%s》有新的学生提交待批改（已合并通知，最近提交：%s）。",
                    assignmentTitle, studentName));
            unread.setCreatedAt(LocalDateTime.now());
            notificationRepository.save(unread);
            return;
        }
        String content = String.format("学生“%s”提交了作业《%s》，请及时查看提交记录。",
                studentName, assignmentTitle);
        sendNotification(teacherId, "ASSIGNMENT_SUBMITTED", title, content, assignmentId);
    }

    @Override
    public void sendExamPublishedNotification(Long studentId, String examTitle, Long examId,
                                               LocalDateTime startAt, LocalDateTime endAt) {
        if (notificationRepository.existsByUserIdAndTypeAndRelatedId(studentId, "EXAM_PUBLISHED", examId)) return;
        String title = "考试发布通知";
        String content = String.format("老师发布了考试《%s》，开放时间：%s 至 %s，请及时查看。",
                examTitle, formatNotificationTime(startAt), endAt == null ? "不限" : formatNotificationTime(endAt));
        sendNotification(studentId, "EXAM_PUBLISHED", title, content, examId);
    }

    private String formatNotificationTime(LocalDateTime value) {
        return value == null ? "规定时间" : value.format(NOTIFICATION_TIME_FORMAT);
    }


    private NotificationVO convertToVO(Notification notification) {
        NotificationVO vo = new NotificationVO();
        BeanUtils.copyProperties(notification, vo);
        return vo;
    }
}
