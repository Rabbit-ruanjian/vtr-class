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

/** 学校预置的教师工号名单，教师注册时必须使用其中的有效工号。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "school_teacher_roster",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_teacher_roster_school_number",
                columnNames = {"school_id", "employee_number"}))
public class SchoolTeacherRoster {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "employee_number", nullable = false, length = 50)
    private String employeeNumber;

    @Column(length = 100)
    private String name;

    @Column(length = 200)
    private String department;

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
