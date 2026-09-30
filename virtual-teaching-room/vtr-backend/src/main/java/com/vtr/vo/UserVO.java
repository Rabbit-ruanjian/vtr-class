package com.vtr.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserVO {
    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String avatar;
    private String phone;
    private String unitName;
    private Long schoolId;
    private String schoolName;
    private String schoolCode;
    private String identityType;
    private String identityNumber;
    private String identityStatus;
    private String bio;
    private String role;
    private String status;
    private LocalDateTime createdAt;
    /** 学生加入当前教学班的时间；不同于账号创建时间。 */
    private LocalDateTime joinedAt;
    private LocalDateTime lastLoginTime;
    private Long classroomId;
    private String classroomName;
    private Long academicClassId;
    private String academicClassName;
    private Boolean adminScopeConfigured;
    private Boolean adminScopeAll;
    private Boolean adminScopeUnbound;
    // 获取头像，如果为空返回默认头像
    public String getAvatar() {
        if (avatar == null || avatar.isEmpty()) {
            return "https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png";
        }
        return avatar;
    }
}
