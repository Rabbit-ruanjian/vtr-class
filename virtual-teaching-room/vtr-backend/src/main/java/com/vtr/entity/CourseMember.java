package com.vtr.entity;

import lombok.*;
import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "course_member", uniqueConstraints = @UniqueConstraint(columnNames = {"course_id", "user_id"}))
public class CourseMember {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "course_id", nullable = false) private Long courseId;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(nullable = false, length = 30) private String role;
    @Column(nullable = false, length = 20) @Builder.Default private String status = "ACTIVE";
    @Column(name = "joined_at", nullable = false) @Builder.Default private LocalDateTime joinedAt = LocalDateTime.now();
}
