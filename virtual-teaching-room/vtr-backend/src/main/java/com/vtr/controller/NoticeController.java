package com.vtr.controller;

import com.vtr.common.PageResult;
import com.vtr.common.Result;
import com.vtr.dto.NoticeCreateDTO;
import com.vtr.dto.NoticeUpdateDTO;
import com.vtr.security.SecurityUtils;
import com.vtr.service.NoticeService;
import com.vtr.vo.NoticeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Long> create(@RequestBody @Validated NoticeCreateDTO dto) {
        Long authorId = getCurrentUserId();
        return Result.success(noticeService.create(dto, authorId));
    }

    @GetMapping("/{id}")
    public Result<NoticeVO> getById(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        return Result.success(noticeService.getById(id, userId));
    }

    @GetMapping
    public Result<PageResult<NoticeVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(noticeService.query(keyword, type, status, page, size));
    }

    @GetMapping("/active")
    public Result<List<NoticeVO>> getActiveNotices() {
        Long userId = getCurrentUserId();
        return Result.success(noticeService.getActiveNotices(userId));
    }

    @GetMapping("/top")
    public Result<List<NoticeVO>> getTopNotices() {
        return Result.success(noticeService.getTopNotices());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> update(@PathVariable Long id,
                               @RequestBody @Validated NoticeUpdateDTO dto) {
        noticeService.update(id, dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        noticeService.delete(id);
        return Result.success();
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> publish(@PathVariable Long id) {
        noticeService.publish(id);
        return Result.success();
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> withdraw(@PathVariable Long id) {
        noticeService.withdraw(id);
        return Result.success();
    }

    @PostMapping("/{id}/pin")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> pin(@PathVariable Long id, @RequestParam Boolean isTop) {
        noticeService.pin(id, isTop);
        return Result.success();
    }

    private Long getCurrentUserId() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            // 未登录用户返回 null，而不是抛出异常
            return null;
        }
        return userId;
    }
}
