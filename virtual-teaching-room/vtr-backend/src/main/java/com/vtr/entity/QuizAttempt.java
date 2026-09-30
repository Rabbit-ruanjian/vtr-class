package com.vtr.entity;

import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "quiz_attempt", indexes = {
        @Index(name = "idx_quiz_attempt_quiz_student", columnList = "quiz_id,student_id"),
        @Index(name = "idx_quiz_attempt_status", columnList = "status")
})
public class QuizAttempt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "quiz_id", nullable = false) private Long quizId;
    @Column(name = "student_id", nullable = false) private Long studentId;
    @Column(name = "attempt_number", nullable = false) private Integer attemptNumber;
    @Column(name = "started_at", nullable = false) private LocalDateTime startedAt;
    @Column(name = "expires_at") private LocalDateTime expiresAt;
    @Column(name = "submitted_at") private LocalDateTime submittedAt;
    @Column(nullable = false, length = 20) @Builder.Default private String status = "IN_PROGRESS";
    @Column private Integer score;
}
