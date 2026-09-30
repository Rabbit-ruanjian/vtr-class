package com.vtr.vo;

import com.vtr.entity.User;
import com.vtr.entity.AcademicClassStudentRoster;
import lombok.Data;

@Data
public class AcademicClassStudentVO {
    private Long id;
    private String studentNumber;
    private String name;
    private String status;
    private Long userId;
    private String registrationStatus;

    public static AcademicClassStudentVO from(User user) {
        AcademicClassStudentVO result = new AcademicClassStudentVO();
        result.setId(user.getId());
        result.setStudentNumber(user.getIdentityNumber() == null ? user.getUsername() : user.getIdentityNumber());
        result.setName(user.getNickname());
        result.setStatus(user.getStatus() == null ? null : user.getStatus().name());
        return result;
    }

    public static AcademicClassStudentVO from(AcademicClassStudentRoster roster) {
        AcademicClassStudentVO result = new AcademicClassStudentVO();
        result.setId(roster.getId());
        result.setUserId(roster.getUserId());
        result.setStudentNumber(roster.getStudentNumber());
        result.setName(roster.getName());
        result.setStatus(roster.getStatus());
        result.setRegistrationStatus(roster.getUserId() == null ? "未注册" : "已注册");
        return result;
    }
}
