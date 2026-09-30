package com.vtr.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class AssignmentQueryDTO extends PageQueryDTO {

    private String keyword;

    private String status;

    private Long teacherId;

    private Boolean isExpired;

    private Boolean hasSubmitted;
    private Long courseId;
}
