package com.vtr.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** 按学院聚合的行政班统计，用于行政班管理页的学院导航与概览。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AcademicClassCollegeSummaryVO {
    private String college;
    private long classCount;
    private long studentCount;
    private List<GradeSummary> grades = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GradeSummary {
        private String grade;
        private long classCount;
        private long studentCount;
    }
}
