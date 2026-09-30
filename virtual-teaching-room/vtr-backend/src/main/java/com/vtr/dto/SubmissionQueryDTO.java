package com.vtr.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class SubmissionQueryDTO extends PageQueryDTO {

    private Long assignmentId;

    private Long studentId;

    private String status;

    private String language;

    private Boolean needReview; // 需要人工评审的
}