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
@Table(name = "course_section", indexes = {
        @Index(name = "idx_course_section_chapter", columnList = "chapter_id"),
        @Index(name = "idx_course_section_course", columnList = "course_id")
})
public class CourseSection {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "course_id", nullable = false) private Long courseId;
    @Column(name = "chapter_id", nullable = false) private Long chapterId;
    @Column(nullable = false, length = 100) private String title;
    @Column(length = 200) private String subtitle;
    @Column(length = 500) private String description;
    @Column(name = "sort_order", nullable = false) private Integer sortOrder = 0;
    @Column(nullable = false, length = 20) private String status = "ACTIVE";
    @CreatedDate @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @LastModifiedDate @Column(name = "updated_at") private LocalDateTime updatedAt;
}
