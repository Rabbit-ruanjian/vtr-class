package com.vtr.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class CourseMemberUpsertDTO {
    @NotNull
    private Long userId;

    @NotBlank
    private String role;
}
