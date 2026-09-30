package com.vtr.vo;

import lombok.Data;

import java.util.Map;

@Data
public class UserStatisticsVO {

    private Long totalUsers;

    private Long activeUsers;

    private Long pendingUsers;

    private Long pendingIdentityBindings;

    private Long todayNewUsers;

    private Map<String, Long> roleDistribution;

    private Map<String, Long> statusDistribution;
}
