package com.vtr.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
public class VideoProgressDTO {
    @NotNull
    @Min(0)
    private Integer watchedSeconds;

    @NotNull
    @Min(1)
    private Integer durationSeconds;
}
