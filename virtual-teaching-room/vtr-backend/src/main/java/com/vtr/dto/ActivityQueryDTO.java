package com.vtr.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ActivityQueryDTO extends PageQueryDTO {

    private String status;

    private String type;

    private String keyword;

    private Long organizerId;

    private Long courseId;

    private Long classroomId;

    private Boolean myParticipation; // 我参与的

    private Boolean myCreated; // 我创建的

    private Boolean schoolLevelOnly; // 仅校级公开活动
}
