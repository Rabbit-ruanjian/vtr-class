package com.vtr.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Table(name = "chapter_quiz", indexes = {
        @Index(name = "idx_chapter_quiz_course_section", columnList = "course_id,section_id"),
        @Index(name = "idx_chapter_quiz_status", columnList = "status")
})
public class ChapterQuiz {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "course_id", nullable = false) private Long courseId;
    @Column(length = 100) private String chapter;
    @Column(name = "section_id") private Long sectionId;
    @Column(name = "assessment_type", nullable = false, length = 20) @Builder.Default private String assessmentType = "QUIZ";
    @Column(nullable = false, length = 200) private String title;
    @Column(length = 1000) private String description;
    @Column(name = "total_score", nullable = false) @Builder.Default private Integer totalScore = 100;
    @Column(name = "duration_minutes", nullable = false) @Builder.Default private Integer durationMinutes = 30;
    @Column(name = "attempt_limit", nullable = false) @Builder.Default private Integer attemptLimit = 1;
    @Column(name = "start_at") private LocalDateTime startAt;
    @Column(name = "end_at") private LocalDateTime endAt;
    @Column(name = "shuffle_questions", nullable = false) @Builder.Default private Boolean shuffleQuestions = false;
    @Column(name = "shuffle_options", nullable = false) @Builder.Default private Boolean shuffleOptions = false;
    @Column(name = "show_answer_after_submit", nullable = false) @Builder.Default private Boolean showAnswerAfterSubmit = true;
    @Column(name = "allow_review_after_submit", nullable = false) @Builder.Default private Boolean allowReviewAfterSubmit = true;
    @Column(nullable = false, length = 20) @Builder.Default private String status = "DRAFT";
    @Column(name = "created_by", nullable = false) private Long createdBy;
    @CreatedDate @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @LastModifiedDate @Column(name = "updated_at") private LocalDateTime updatedAt;
}
