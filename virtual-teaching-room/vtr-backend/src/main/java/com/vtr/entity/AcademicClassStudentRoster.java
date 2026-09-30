package com.vtr.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/**
 * 学校维护的行政班学生名单。名单可以先于用户账号存在，学生注册时再完成账号绑定。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "academic_class_student",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_academic_class_student_school_number",
                columnNames = {"school_id", "student_number"}))
public class AcademicClassStudentRoster {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "academic_class_id", nullable = false)
    private Long academicClassId;

    @Column(name = "student_number", nullable = false, length = 50)
    private String studentNumber;

    @Column(length = 100)
    private String name;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
