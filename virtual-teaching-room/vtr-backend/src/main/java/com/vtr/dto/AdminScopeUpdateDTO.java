package com.vtr.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AdminScopeUpdateDTO {
    private boolean allSchools;
    private List<Long> schoolIds = new ArrayList<>();
}
