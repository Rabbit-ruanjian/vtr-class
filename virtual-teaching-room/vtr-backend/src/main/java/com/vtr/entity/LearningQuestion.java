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
@Table(name = "learning_question")
public class LearningQuestion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 200) private String title;
    @Lob @Column(nullable = false) private String stem;
    @Column(name = "question_type", nullable = false, length = 20) private String questionType;
    @Lob private String options;
    @Lob @Column(name = "reference_answer", nullable = false) private String referenceAnswer;
    @Lob private String analysis;
    @Column(nullable = false) private Integer difficulty;
    @Column(name = "knowledge_point", length = 200) private String knowledgePoint;
    @Column(name = "course_id", nullable = false) private Long courseId;
    @Column(length = 100) private String chapter;
    @Column(name = "section_id") private Long sectionId;
    @Column(name = "teacher_id", nullable = false) private Long teacherId;
    @Column(nullable = false, length = 20) @Builder.Default private String status = "PENDING";
    @Column(name = "audit_remark", length = 1000) private String auditRemark;
    @CreatedDate @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @LastModifiedDate @Column(name = "updated_at") private LocalDateTime updatedAt;
}
