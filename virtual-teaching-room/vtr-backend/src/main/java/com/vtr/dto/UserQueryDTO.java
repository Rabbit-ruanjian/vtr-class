package com.vtr.dto;

import lombok.Data;

@Data
public class UserQueryDTO extends PageQueryDTO {

    private String keyword;

    private String role;

    private String status;

    private String identityStatus;

    private String startTime;

    private String endTime;

    private Long schoolId;
}
