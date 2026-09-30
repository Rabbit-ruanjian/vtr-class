package com.vtr.controller;

import com.vtr.common.PageResult;
import com.vtr.common.Result;
import com.vtr.dto.AuditCommentDTO;
import com.vtr.dto.AuditPostDTO;
import com.vtr.dto.ContentAuditDTO;
import com.vtr.dto.PageQueryDTO;
import com.vtr.entity.ContentAudit;
import com.vtr.service.CodeSnippetService;
import com.vtr.service.ContentAuditService;
import com.vtr.service.AdminScopeService;
import com.vtr.vo.CodeSnippetVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final CodeSnippetService snippetService;
    private final ContentAuditService auditService;
    private final AdminScopeService scopeService;

    // ========== 内容审核 ==========

    @GetMapping("/content/pending")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public Result<PageResult<ContentAudit>> getPendingContent(PageQueryDTO query,
                                                               @RequestParam(defaultValue = "PENDING") String status) {
        Set<Long> allowed = scopeService.allowedSchoolIds(scopeService.currentUser());
        if (allowed != null && allowed.isEmpty()) {
            return Result.success(PageResult.of(List.of(), 0L, query.getPage(), query.getSize()));
        }
        return Result.success(auditService.getList(query, status, allowed));
    }

    @PostMapping("/content/{id}/review")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public Result<Void> reviewContent(@PathVariable Long id,
                                      @RequestBody @Validated ContentAuditDTO dto) {
        Long reviewerId = getCurrentUserId();
        String reviewerName = getCurrentUserName();
        ContentAudit audit = auditService.getById(id);
        if (audit.getSchoolId() == null) {
            if (!scopeService.isGlobalAdmin(scopeService.currentUser())) throw new com.vtr.common.exception.BusinessException(403, "该审核内容没有明确学校归属");
        } else {
            scopeService.requireSchool(audit.getSchoolId());
        }
        auditService.review(id, dto, reviewerId, reviewerName);
        return Result.success();
    }

    // ========== 代码片段审核 ==========

    @GetMapping("/snippets/pending")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public Result<PageResult<CodeSnippetVO>> getPendingSnippets(PageQueryDTO query) {
        return Result.success(snippetService.getPendingList(
                PageRequest.of(query.getPage() - 1, query.getSize())));
    }

    @GetMapping("/snippets/approved")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public Result<PageResult<CodeSnippetVO>> getApprovedSnippets(PageQueryDTO query) {
        return Result.success(snippetService.getApprovedList(
                PageRequest.of(query.getPage() - 1, query.getSize())));
    }

    @GetMapping("/snippets/rejected")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public Result<PageResult<CodeSnippetVO>> getRejectedSnippets(PageQueryDTO query) {
        return Result.success(snippetService.getRejectedList(
                PageRequest.of(query.getPage() - 1, query.getSize())));
    }

    @PostMapping("/snippets/{id}/review")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public Result<Void> reviewSnippet(@PathVariable Long id,
                                      @RequestParam String action,
                                      @RequestParam(required = false) String remark) {
        Long reviewerId = getCurrentUserId();
        String reviewerName = getCurrentUserName();
        snippetService.reviewSnippet(id, action, remark, reviewerId, reviewerName);
        return Result.success();
    }

    // ========== 系统统计 ==========

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public Result<Map<String, Object>> getDashboardData() {
        Map<String, Object> data = new HashMap<>();
        data.put("audit", auditService.getDashboardStatistics());
        data.put("snippet", snippetService.getStatistics());
        return Result.success(data);
    }

    // ========== 辅助方法 ==========

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.vtr.security.CustomUserDetails) {
            return ((com.vtr.security.CustomUserDetails) auth.getPrincipal()).getId();
        }
        return null;
    }

    private String getCurrentUserName() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.vtr.security.CustomUserDetails) {
            return ((com.vtr.security.CustomUserDetails) auth.getPrincipal()).getUsername();
        }
        return "管理员";
    }
}
