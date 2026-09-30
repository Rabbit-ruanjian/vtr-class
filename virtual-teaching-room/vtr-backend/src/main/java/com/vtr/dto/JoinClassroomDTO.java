package com.vtr.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class JoinClassroomDTO {

    @NotBlank(message = "课程代码或教学班邀请码不能为空")
    @Size(min = 4, max = 50, message = "课程代码或教学班邀请码长度应为4到50位")
    private String inviteCode;
}
