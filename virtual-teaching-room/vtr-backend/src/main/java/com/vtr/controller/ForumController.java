package com.vtr.controller;

import com.vtr.common.Result;
import com.vtr.dto.AuditCommentDTO;
import com.vtr.dto.AuditPostDTO;
import com.vtr.dto.ForumCommentCreateDTO;
import com.vtr.dto.ForumPostCreateDTO;
import com.vtr.service.ForumService;
import com.vtr.vo.ForumCommentVO;
import com.vtr.vo.ForumPostDetailVO;
import com.vtr.vo.ForumPostListVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/api/forum")
@RequiredArgsConstructor
@Validated
public class ForumController {

    private final ForumService forumService;

    // ========== 前台接口 ==========

    @GetMapping("/posts")
    public Result<Page<ForumPostListVO>> getPostList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String postType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long courseId,
            @RequestParam(defaultValue = "false") boolean unresolved) {
        return Result.success(forumService.getPostList(page, size, postType, keyword, courseId, unresolved));
    }

    @GetMapping("/post/{id}")
    public Result<ForumPostDetailVO> getPostDetail(@PathVariable Long id) {
        return Result.success(forumService.getPostDetail(id));
    }

    @PostMapping("/post")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> createPost(@Valid @RequestBody ForumPostCreateDTO dto) {
        forumService.createPost(dto);
        return Result.success();
    }

    @PostMapping("/comment")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> addComment(@Valid @RequestBody ForumCommentCreateDTO dto) {
        forumService.addComment(dto);
        return Result.success();
    }

    @DeleteMapping("/comment/{id}")
    public Result<Void> deleteComment(@PathVariable Long id) {
        forumService.deleteComment(id);
        return Result.success();
    }

    @DeleteMapping("/post/{id}")
    public Result<Void> deletePost(@PathVariable Long id) {
        forumService.deletePost(id);
        return Result.success();
    }

    @PostMapping("/post/{id}/like")
    @PreAuthorize("isAuthenticated()")
    public Result<java.util.Map<String, Object>> toggleLike(@PathVariable Long id) {
        boolean liked = forumService.toggleLike(id);
        return Result.success(java.util.Map.of("liked", liked));
    }

    // ========== 管理员帖子审核接口 ==========

    @GetMapping("/admin/posts/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Page<ForumPostListVO>> getAllPosts(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(forumService.getAllPosts(page, size));
    }

    @GetMapping("/admin/posts/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Page<ForumPostListVO>> getPendingPosts(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        log.info("获取待审核帖子列表: page={}, size={}", page, size);
        return Result.success(forumService.getPendingPosts(page, size));
    }

    @GetMapping("/admin/posts/approved")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Page<ForumPostListVO>> getApprovedPosts(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        log.info("获取已通过帖子列表: page={}, size={}", page, size);
        return Result.success(forumService.getApprovedPosts(page, size));
    }

    @GetMapping("/admin/posts/rejected")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Page<ForumPostListVO>> getRejectedPosts(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        log.info("获取已拒绝帖子列表: page={}, size={}", page, size);
        return Result.success(forumService.getRejectedPosts(page, size));
    }

    @PostMapping("/admin/post/{id}/audit")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> auditPost(@PathVariable Long id,
                                  @RequestBody @Valid AuditPostDTO dto) {
        Long reviewerId = getCurrentUserId();
        String reviewerName = getCurrentUserName();
        log.info("审核帖子: id={}, status={}, reviewerId={}, reviewerName={}",
                id, dto.getStatus(), reviewerId, reviewerName);
        forumService.auditPost(id, dto, reviewerId, reviewerName);
        return Result.success();
    }

    // ========== 管理员评论审核接口 ==========

    @GetMapping("/admin/comments/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Page<ForumCommentVO>> getAllComments(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(forumService.getAllComments(page, size));
    }

    @GetMapping("/admin/comments/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Page<ForumCommentVO>> getPendingComments(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        log.info("获取待审核评论列表: page={}, size={}", page, size);
        return Result.success(forumService.getPendingComments(page, size));
    }

    @GetMapping("/admin/comments/approved")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Page<ForumCommentVO>> getApprovedComments(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        log.info("获取已通过评论列表: page={}, size={}", page, size);
        return Result.success(forumService.getApprovedComments(page, size));
    }

    @GetMapping("/admin/comments/rejected")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Page<ForumCommentVO>> getRejectedComments(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        log.info("获取已拒绝评论列表: page={}, size={}", page, size);
        return Result.success(forumService.getRejectedComments(page, size));
    }

    @PostMapping("/admin/comment/{id}/audit")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> auditComment(@PathVariable Long id,
                                     @RequestBody @Valid AuditCommentDTO dto) {
        Long reviewerId = getCurrentUserId();
        String reviewerName = getCurrentUserName();
        log.info("审核评论: id={}, status={}, reviewerId={}, reviewerName={}",
                id, dto.getStatus(), reviewerId, reviewerName);
        forumService.auditComment(id, dto, reviewerId, reviewerName);
        return Result.success();
    }

    // 在 ForumController.java 中添加

    /**
     * 取消帖子置顶（管理员）
     */
    @PostMapping("/admin/post/{id}/cancel-pin")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> cancelPin(@PathVariable Long id) {
        Long reviewerId = getCurrentUserId();
        String reviewerName = getCurrentUserName();
        log.info("取消帖子置顶: id={}, reviewerId={}, reviewerName={}", id, reviewerId, reviewerName);
        forumService.cancelPin(id, reviewerId, reviewerName);
        return Result.success();
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
