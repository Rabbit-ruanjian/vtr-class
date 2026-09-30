package com.vtr.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Table(name = "question_favorite",
        uniqueConstraints = @UniqueConstraint(name = "uk_question_favorite_student_question", columnNames = {"question_id", "student_id"}),
        indexes = {
                @Index(name = "idx_question_favorite_student", columnList = "student_id"),
                @Index(name = "idx_question_favorite_question", columnList = "question_id")
        })
public class QuestionFavorite {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "question_id", nullable = false) private Long questionId;
    @Column(name = "student_id", nullable = false) private Long studentId;
    @CreatedDate @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
}
