package com.vtr.dto;

import lombok.Data;

import java.time.LocalDateTime;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;
import javax.validation.constraints.Future;

@Data
public class ActivityUpdateDTO {

    @Size(max = 200, message = "活动标题不能超过200字") private String title;
    @Size(max = 500, message = "封面地址不能超过500字") private String coverUrl;
    @Size(max = 200, message = "组织单位不能超过200字") private String organizerUnit;
    @Size(max = 200, message = "主办方不能超过200字") private String sponsor;

    @Size(max = 20000, message = "活动内容不能超过20000字") private String content;

    private String type;

    @Future(message = "活动时间必须晚于当前时间")
    private LocalDateTime activityTime;

    @Size(max = 200, message = "活动地点不能超过200字") private String location;

    @Min(value = 10, message = "活动时长不能少于10分钟")
    @Max(value = 1440, message = "活动时长不能超过1440分钟")
    private Integer duration;

    @Min(value = 1, message = "最大参与人数至少为1")
    @Max(value = 1000, message = "最大参与人数不能超过1000")
    private Integer maxParticipants;
    private Long courseId;
    private Long classroomId;
    @Future(message = "报名截止时间必须晚于当前时间")
    private LocalDateTime registrationDeadline;
}
