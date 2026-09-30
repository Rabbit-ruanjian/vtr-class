package com.vtr.entity;

import lombok.*;

import javax.persistence.*;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "chapter_quiz_question", indexes = {
        @Index(name = "idx_quiz_question_quiz", columnList = "quiz_id"),
        @Index(name = "idx_quiz_question_question", columnList = "question_id")
})
public class ChapterQuizQuestion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "quiz_id", nullable = false) private Long quizId;
    @Column(name = "question_id", nullable = false) private Long questionId;
    @Column(name = "sort_order", nullable = false) private Integer sortOrder;
    @Column(nullable = false) private Integer score;
}
