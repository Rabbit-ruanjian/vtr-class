package com.vtr.controller;

import com.vtr.common.PageResult;
import com.vtr.common.Result;
import com.vtr.dto.ManualReviewDTO;
import com.vtr.dto.SubmissionDTO;
import com.vtr.dto.SubmissionQueryDTO;
import com.vtr.service.SubmissionService;
import com.vtr.vo.SubmissionProgressVO;
import com.vtr.vo.SubmissionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    @PostMapping("/assignments/{assignmentId}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<Long> submit(@PathVariable Long assignmentId,
                               @RequestBody @Validated SubmissionDTO dto) {
        dto.setAssignmentId(assignmentId);
        Long studentId = getCurrentUserId();
        log.info("学生提交作业: assignmentId={}, studentId={}", assignmentId, studentId);
        return Result.success(submissionService.submit(dto, studentId));
    }

    @GetMapping("/submissions/{id}")
    public Result<SubmissionVO> getById(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        return Result.success(submissionService.getById(id, userId));
    }

    @GetMapping("/submissions")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<PageResult<SubmissionVO>> list(SubmissionQueryDTO query) {
        Long userId = getCurrentUserId();
        return Result.success(submissionService.query(query, userId));
    }

    @GetMapping("/assignments/{assignmentId}/submissions/my")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<List<SubmissionVO>> getMySubmissions(@PathVariable Long assignmentId) {
        Long studentId = getCurrentUserId();
        return Result.success(submissionService.getMySubmissions(assignmentId, studentId));
    }

    @GetMapping("/assignments/{assignmentId}/submissions/final")
    public Result<SubmissionVO> getFinalSubmission(@PathVariable Long assignmentId) {
        Long studentId = getCurrentUserId();
        return Result.success(submissionService.getFinalSubmission(assignmentId, studentId));
    }

    // ========== 新增接口：教师端获取所有提交记录 ==========
    @GetMapping("/assignments/{assignmentId}/submissions/all")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<PageResult<SubmissionVO>> getAllSubmissions(
            @PathVariable Long assignmentId,
            @Validated SubmissionQueryDTO queryDTO) {
        Long userId = getCurrentUserId();
        log.info("获取作业所有提交记录: assignmentId={}, userId={}", assignmentId, userId);
        return Result.success(submissionService.getAllSubmissionsByAssignment(assignmentId, queryDTO, userId));
    }

    @PostMapping("/submissions/{id}/review")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')")
    public Result<Void> manualReview(@PathVariable Long id,
                                     @RequestBody @Validated ManualReviewDTO dto) {
        Long reviewerId = getCurrentUserId();
        submissionService.manualReview(id, dto, reviewerId);
        return Result.success();
    }

    @GetMapping("/submissions/{id}/progress")
    public Result<SubmissionProgressVO> getProgress(@PathVariable Long id) {
        return Result.success(submissionService.getProgress(id));
    }

    // ========== 私有方法：获取当前登录用户ID ==========
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() ||
                "anonymousUser".equals(authentication.getPrincipal())) {
            throw new RuntimeException("未找到已认证的用户信息");
        }
        Object principal = authentication.getPrincipal();

        // 处理 CustomUserDetails 类型
        if (principal instanceof com.vtr.security.CustomUserDetails) {
            return ((com.vtr.security.CustomUserDetails) principal).getId();
        }
        if (principal instanceof Long) {
            return (Long) principal;
        }
        if (principal instanceof Integer) {
            return ((Integer) principal).longValue();
        }
        if (principal instanceof String) {
            try {
                return Long.parseLong((String) principal);
            } catch (NumberFormatException ignored) {
            }
        }
        throw new RuntimeException("无法从 SecurityContext 中获取用户ID，principal 类型: " + principal.getClass());
    }
}