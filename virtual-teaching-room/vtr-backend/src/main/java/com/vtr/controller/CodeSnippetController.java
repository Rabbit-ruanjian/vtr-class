package com.vtr.controller;

import com.vtr.common.PageResult;
import com.vtr.common.Result;
import com.vtr.dto.SnippetCreateDTO;
import com.vtr.dto.SnippetReviewDTO;
import com.vtr.dto.SnippetSearchDTO;
import com.vtr.dto.SnippetUpdateDTO;
import com.vtr.service.CodeSnippetService;
import com.vtr.vo.CodeSnippetVO;
import com.vtr.vo.SnippetStatisticsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/snippets")
@RequiredArgsConstructor
public class CodeSnippetController {

    private final CodeSnippetService snippetService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public Result<Long> create(@RequestBody @Validated SnippetCreateDTO dto) {
        Long authorId = getCurrentUserId();
        return Result.success(snippetService.create(dto, authorId));
    }

    @GetMapping("/{id}")
    public Result<CodeSnippetVO> getById(@PathVariable Long id) {
        return Result.success(snippetService.getById(id));
    }

    @GetMapping
    public Result<PageResult<CodeSnippetVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String language) {

        log.info("查询代码片段列表: page={}, size={}, keyword={}, language={}", page, size, keyword, language);

        SnippetSearchDTO query = new SnippetSearchDTO();
        query.setPage(page);
        query.setSize(size);
        query.setKeyword(keyword);
        query.setLanguage(language);
        query.setStatus("APPROVED");
        query.setIsPublic(true);
        query.setSortBy("createdAt");
        query.setSortOrder("desc");

        return Result.success(snippetService.search(query));
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public Result<PageResult<CodeSnippetVO>> getMySnippets(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = getCurrentUserId();
        return Result.success(snippetService.getUserSnippets(userId, PageRequest.of(page - 1, size)));
    }

    @GetMapping("/tags")
    public Result<List<String>> getHotTags(@RequestParam(defaultValue = "20") int limit) {
        return Result.success(snippetService.getHotTags(limit));
    }

    @GetMapping("/statistics")
    public Result<SnippetStatisticsVO> getStatistics() {
        return Result.success(snippetService.getStatistics());
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Validated SnippetUpdateDTO dto) {
        Long userId = getCurrentUserId();
        snippetService.update(id, dto, userId);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        snippetService.delete(id, userId);
        return Result.success();
    }

    @PostMapping("/{id}/like")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> like(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        snippetService.like(id, userId);
        return Result.success();
    }

    @DeleteMapping("/{id}/like")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> unlike(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        snippetService.unlike(id, userId);
        return Result.success();
    }

    @PostMapping("/{id}/download")
    public Result<Void> download(@PathVariable Long id) {
        snippetService.incrementDownloadCount(id);
        return Result.success();
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> review(@PathVariable Long id, @RequestBody @Validated SnippetReviewDTO dto) {
        Long reviewerId = getCurrentUserId();
        snippetService.review(id, dto, reviewerId);
        return Result.success();
    }

    private Long getCurrentUserId() {
        try {
            var authentication = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()
                    && !"anonymousUser".equals(authentication.getPrincipal())) {
                return 1L;
            }
        } catch (Exception e) {
            log.debug("获取当前用户ID失败: {}", e.getMessage());
        }
        return null;
    }
}