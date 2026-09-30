// entity/ClassroomStudentRelation.java
package com.vtr.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "classroom_student_relation",
        uniqueConstraints = @UniqueConstraint(columnNames = {"classroom_id", "student_id"}))
@EntityListeners(AuditingEntityListener.class)
public class ClassroomStudentRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "classroom_id", nullable = false)
    private Long classroomId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "student_number", length = 50)
    private String studentNumber;  // 冗余学号，方便查询

    @Column(nullable = false)
    private String status = "ACTIVE";  // ACTIVE, REMOVED

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime removedAt;
}