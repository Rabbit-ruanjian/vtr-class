package com.vtr.service;

import com.vtr.entity.Assignment;
import com.vtr.entity.Classroom;
import com.vtr.entity.ClassroomStudentRelation;
import com.vtr.repository.AssignmentRepository;
import com.vtr.repository.AssignmentStudentVisibilityRepository;
import com.vtr.repository.ClassroomRepository;
import com.vtr.repository.ClassroomStudentRelationRepository;
import com.vtr.repository.CourseRepository;
import com.vtr.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 为真实已发布作业生成截止提醒。
 * 通知本身由 NotificationService 按 user/type/relatedId 幂等写入，
 * 因此定时任务重复运行不会给同一位学生重复提醒。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssignmentNotificationScheduler {

    private static final long REMINDER_HOURS = 24;

    private final AssignmentRepository assignmentRepository;
    private final AssignmentStudentVisibilityRepository assignmentVisibilityRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomStudentRelationRepository classroomStudentRelationRepository;
    private final CourseRepository courseRepository;
    private final SubmissionRepository submissionRepository;
    private final NotificationService notificationService;

    @Scheduled(initialDelay = 15000, fixedDelay = 60000)
    @Transactional
    public void sendUpcomingDeadlineReminders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime reminderBoundary = now.plusHours(REMINDER_HOURS);

        for (Assignment assignment : assignmentRepository.findActiveAssignments(now)) {
            if (assignment.getDeadline() == null || assignment.getDeadline().isAfter(reminderBoundary)) {
                continue;
            }

            for (Long studentId : visibleStudentIds(assignment)) {
                if (submissionRepository.countByAssignmentAndStudent(assignment.getId(), studentId) > 0) {
                    continue;
                }
                notificationService.sendAssignmentDeadlineReminder(
                        studentId, assignment.getTitle(), assignment.getId(), assignment.getDeadline());
            }
        }
    }

    private List<Long> visibleStudentIds(Assignment assignment) {
        if (assignment.getCourseId() != null && !courseRepository.findById(assignment.getCourseId())
                .map(course -> "ACTIVE".equals(course.getStatus())).orElse(false)) {
            return new ArrayList<>();
        }

        if ("SELECTED".equals(assignment.getPublishType())) {
            List<Long> studentIds = assignmentVisibilityRepository.findByAssignmentId(assignment.getId()).stream()
                    .map(visibility -> visibility.getStudent().getId())
                    .distinct()
                    .collect(Collectors.toList());
            if (assignment.getCourseId() != null) {
                List<Long> classroomIds = classroomRepository.findByCourseIdOrderByCreatedAtDesc(assignment.getCourseId()).stream()
                        .filter(classroom -> "ACTIVE".equals(classroom.getStatus()))
                        .map(Classroom::getId)
                        .collect(Collectors.toList());
                List<Long> enrolledStudentIds = classroomStudentRelationRepository
                        .findByClassroomIdInAndStatus(classroomIds, "ACTIVE").stream()
                        .map(ClassroomStudentRelation::getStudentId)
                        .distinct()
                        .collect(Collectors.toList());
                studentIds.retainAll(enrolledStudentIds);
            }
            return studentIds;
        }

        List<Classroom> classrooms = assignment.getCourseId() == null
                ? classroomRepository.findByTeacherIdAndStatus(assignment.getTeacher().getId(), "ACTIVE")
                : classroomRepository.findByCourseIdOrderByCreatedAtDesc(assignment.getCourseId());
        classrooms = classrooms.stream()
                .filter(classroom -> assignment.getCourseId() == null
                        || assignment.getCourseId().equals(classroom.getCourseId()))
                .collect(Collectors.toList());
        if (classrooms.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> classroomIds = classrooms.stream().map(Classroom::getId).collect(Collectors.toList());
        return classroomStudentRelationRepository.findByClassroomIdInAndStatus(classroomIds, "ACTIVE").stream()
                .map(ClassroomStudentRelation::getStudentId)
                .distinct()
                .collect(Collectors.toList());
    }
}
