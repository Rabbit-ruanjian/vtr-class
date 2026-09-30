package com.vtr.entity;

import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "quiz_answer", indexes = {
        @Index(name = "idx_quiz_answer_attempt", columnList = "attempt_id"),
        @Index(name = "idx_quiz_answer_question", columnList = "question_id")
})
public class QuizAnswer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "attempt_id", nullable = false) private Long attemptId;
    @Column(name = "question_id", nullable = false) private Long questionId;
    @Lob @Column(nullable = false) private String answer;
    @Column private Boolean correct;
    @Column private Integer score;
    @Column(name = "review_status", nullable = false, length = 20) @Builder.Default private String reviewStatus = "AUTO";
    @Column(name = "teacher_comment", length = 1000) private String teacherComment;
    @Column(name = "answered_at", nullable = false) private LocalDateTime answeredAt;
}
