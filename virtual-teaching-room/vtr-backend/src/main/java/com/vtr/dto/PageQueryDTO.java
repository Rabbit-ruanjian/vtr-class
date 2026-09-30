package com.vtr.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

@Data
public class PageQueryDTO {

    @Min(value = 1, message = "页码最小为1")
    private Integer page = 1;

    @Min(value = 1, message = "每页最少1条")
    @Max(value = 100, message = "每页最多100条")
    private Integer size = 10;

    private String sortField;

    private String sortOrder = "desc";

    public Integer getOffset() {
        return (page - 1) * size;
    }
}