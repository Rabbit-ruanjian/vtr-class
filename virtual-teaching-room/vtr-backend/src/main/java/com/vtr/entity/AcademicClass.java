package com.vtr.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import javax.persistence.*;
import java.time.LocalDateTime;
import javax.persistence.Transient;

@Data @Entity @Builder @NoArgsConstructor @AllArgsConstructor
@Table(name = "academic_class", uniqueConstraints = @UniqueConstraint(name = "uk_academic_class_school_code", columnNames = {"school_id", "class_code"}))
@EntityListeners(AuditingEntityListener.class)
public class AcademicClass {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "school_id") private Long schoolId;
    @Column(nullable = false, length = 100) private String name;
    @Column(length = 100) private String college;
    @Column(length = 100) private String campus;
    @Column(nullable = false, length = 100) private String major;
    @Column(nullable = false, length = 30) private String grade;
    @Column(name = "class_code", nullable = false, length = 50) private String classCode;
    @Column(name = "head_teacher_name", length = 50) private String headTeacherName;
    @Column(name = "counselor_name", length = 50) private String counselorName;
    @Column(nullable = false, length = 20) private String status = "ACTIVE";
    @CreatedDate @Column(updatable = false) private LocalDateTime createdAt;
    @LastModifiedDate private LocalDateTime updatedAt;
}
