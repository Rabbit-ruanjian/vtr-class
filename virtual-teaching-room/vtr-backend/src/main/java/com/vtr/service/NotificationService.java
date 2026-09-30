package com.vtr.service;

import com.vtr.common.PageResult;
import com.vtr.vo.NotificationVO;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationService {

    PageResult<NotificationVO> getMyNotifications(Long userId, Integer page, Integer size,
                                                   String category, Boolean unreadOnly);

    long getUnreadCount(Long userId);

    void markAsRead(Long id, Long userId);

    void markAllAsRead(Long userId);

    void deleteNotification(Long id, Long userId);

    void deleteNotifications(List<Long> ids, Long userId);

    void sendNotification(Long userId, String type, String title, String content, Long relatedId);

    void sendGradeNotification(Long studentId, String assignmentTitle, Integer score, Long submissionId);

    void sendAssignmentPublishedNotification(Long studentId, String assignmentTitle, Long assignmentId);

    void sendAssignmentPublishedNotification(Long studentId, String assignmentTitle, Long assignmentId, LocalDateTime deadline);

    void sendAssignmentDeadlineReminder(Long studentId, String assignmentTitle, Long assignmentId, LocalDateTime deadline);

    void sendAssignmentSubmissionNotification(Long teacherId, String studentName,
                                              String assignmentTitle, Long submissionId);

    void sendExamPublishedNotification(Long studentId, String examTitle, Long examId,
                                       java.time.LocalDateTime startAt, java.time.LocalDateTime endAt);
}
