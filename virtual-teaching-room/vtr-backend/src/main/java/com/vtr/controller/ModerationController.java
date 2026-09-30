package com.vtr.controller;

import com.vtr.common.Result;
import com.vtr.dto.ModerationReportDTO;
import com.vtr.security.SecurityUtils;
import com.vtr.service.ContentAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/moderation")
@RequiredArgsConstructor
public class ModerationController {

    private final ContentAuditService contentAuditService;

    @PostMapping("/reports")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> report(@RequestBody @Valid ModerationReportDTO dto) {
        contentAuditService.report(dto.getTargetType(), dto.getTargetId(), dto.getReason(),
                SecurityUtils.getCurrentUserId(), SecurityUtils.getCurrentUserName());
        return Result.success();
    }

    @PostMapping("/appeals")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> appeal(@RequestBody @Valid ModerationReportDTO dto) {
        contentAuditService.appeal(dto.getTargetType(), dto.getTargetId(), dto.getReason(),
                SecurityUtils.getCurrentUserId(), SecurityUtils.getCurrentUserName());
        return Result.success();
    }
}
