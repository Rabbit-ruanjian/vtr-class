// entity/Classroom.java
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

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "classroom")
@EntityListeners(AuditingEntityListener.class)
public class Classroom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String className;  // 班级名称，如"高一(1)班"

    @Column(length = 500)
    private String description;  // 班级描述

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;  // 所属教师ID

    @Column(name = "course_id")
    private Long courseId;

    @Column(length = 50)
    private String grade;  // 年级，如"高一"

    @Column(length = 50)
    private String semester;  // 学期，如"2024-2025-1"

    @Column(name = "invite_code", unique = true, length = 16)
    private String inviteCode;

    @Column(nullable = false)
    private Integer studentCount = 0;  // 学生人数（冗余字段，便于展示）

    @Column(nullable = false)
    private String status = "ACTIVE";  // ACTIVE, ARCHIVED

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
