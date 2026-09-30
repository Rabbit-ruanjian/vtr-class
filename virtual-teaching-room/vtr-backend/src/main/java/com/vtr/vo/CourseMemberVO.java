package com.vtr.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CourseMemberVO {
    private Long userId;
    private String username;
    private String nickname;
    private String email;
    private String avatar;
    private String role;
    private LocalDateTime joinedAt;
}
