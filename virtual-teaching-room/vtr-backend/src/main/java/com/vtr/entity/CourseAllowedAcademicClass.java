package com.vtr.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

/** 课程允许进入的行政班。空关系表示兼容旧课程，不限制邀请码入课。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "course_allowed_academic_class",
        uniqueConstraints = @UniqueConstraint(name = "uk_course_allowed_academic_class", columnNames = {"course_id", "academic_class_id"}))
public class CourseAllowedAcademicClass {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "academic_class_id", nullable = false)
    private Long academicClassId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
