package com.vtr.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "course")
@EntityListeners(AuditingEntityListener.class)
public class Course {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 100) private String courseName;
    @Column(unique = true, length = 50) private String courseCode;
    @Column(length = 500) private String description;
    @Column(length = 500) private String coverImage;
    @Column(length = 50) private String semester;
    @Column(precision = 4, scale = 1) private BigDecimal credits;
    @Column(length = 50) private String courseCategory;
    @Column(length = 100) private String teachingDepartment;
    @Column(length = 50) private String assessmentMethod;
    @Column(name = "created_by", nullable = false) private Long createdBy;
    @Column(name = "school_id") private Long schoolId;
    @Column(nullable = false, length = 20) private String status = "ACTIVE";
    @CreatedDate @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @LastModifiedDate @Column(name = "updated_at") private LocalDateTime updatedAt;
}
