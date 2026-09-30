package com.vtr.entity;

import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "question_attempt", indexes = {
        @Index(name = "idx_question_attempt_question", columnList = "question_id"),
        @Index(name = "idx_question_attempt_student", columnList = "student_id")
})
public class QuestionAttempt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "question_id", nullable = false) private Long questionId;
    @Column(name = "student_id", nullable = false) private Long studentId;
    @Lob @Column(nullable = false) private String answer;
    @Column private Boolean correct;
    @Column(name = "review_status", nullable = false, length = 20) @Builder.Default private String reviewStatus = "AUTO";
    @Column(name = "reviewed_by") private Long reviewedBy;
    @Column(name = "reviewed_at") private LocalDateTime reviewedAt;
    @Column private Integer score;
    @Column(name = "elapsed_seconds") private Integer elapsedSeconds;
    @Column(name = "submitted_at", nullable = false) private LocalDateTime submittedAt;
}
