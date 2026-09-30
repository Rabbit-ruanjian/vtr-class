package com.vtr.controller;

import com.vtr.common.Result;
import com.vtr.entity.User;
import com.vtr.repository.CourseRepository;
import com.vtr.repository.SchoolRepository;
import com.vtr.repository.TeachingActivityRepository;
import com.vtr.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicOverviewController {
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final SchoolRepository schoolRepository;
    private final TeachingActivityRepository activityRepository;

    @GetMapping("/overview")
    public Result<Map<String, Long>> overview() {
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("teacherCount", userRepository.countByStatusAndRole(User.UserStatus.ACTIVE, User.UserRole.TEACHER));
        result.put("courseCount", courseRepository.countByStatus("ACTIVE"));
        result.put("schoolCount", schoolRepository.countByStatus("ACTIVE"));
        result.put("activityHours", activityRepository.countByStatusAndIsDeletedFalse("APPROVED"));
        return Result.success(result);
    }
}
