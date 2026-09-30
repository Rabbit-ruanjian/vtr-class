package com.vtr.controller;

import com.vtr.common.Result;
import com.vtr.common.exception.NotFoundException;
import com.vtr.entity.NoticeAttachment;
import com.vtr.repository.NoticeAttachmentRepository;
import com.vtr.repository.NoticeRepository;
import com.vtr.security.SecurityUtils;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.List;

@RestController
@RequestMapping("/api/notices/{noticeId}/attachments")
@RequiredArgsConstructor
public class NoticeAttachmentController {

    private final NoticeRepository noticeRepository;
    private final NoticeAttachmentRepository attachmentRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @GetMapping
    public Result<List<NoticeAttachment>> list(@PathVariable Long noticeId) {
        requireNotice(noticeId);
        return Result.success(attachmentRepository.findByNoticeIdOrderByCreatedAtAsc(noticeId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Transactional
    public Result<Long> add(@PathVariable Long noticeId, @RequestBody @Valid AttachmentRequest request) {
        requireNotice(noticeId);
        NoticeAttachment attachment = NoticeAttachment.builder()
                .noticeId(noticeId)
                .fileName(request.getFileName().trim())
                .fileUrl(request.getFileUrl().trim())
                .fileSize(request.getFileSize())
                .fileType(request.getFileType())
                .uploadBy(SecurityUtils.getCurrentUserId())
                .build();
        Long id = attachmentRepository.save(attachment).getId();
        evictNoticeCache(noticeId);
        return Result.success(id);
    }

    @DeleteMapping("/{attachmentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Transactional
    public Result<Void> delete(@PathVariable Long noticeId, @PathVariable Long attachmentId) {
        requireNotice(noticeId);
        if (attachmentRepository.deleteByIdAndNoticeId(attachmentId, noticeId) == 0) {
            throw new NotFoundException("公告附件", attachmentId);
        }
        evictNoticeCache(noticeId);
        return Result.success();
    }

    private void requireNotice(Long noticeId) {
        noticeRepository.findById(noticeId)
                .orElseThrow(() -> new NotFoundException("公告", noticeId));
    }

    private void evictNoticeCache(Long noticeId) {
        try {
            redisTemplate.delete("notice:" + noticeId);
        } catch (Exception ignored) {
            // Redis 只负责缓存，不能影响公告附件保存。
        }
    }

    @Data
    public static class AttachmentRequest {
        @NotBlank(message = "附件名称不能为空")
        private String fileName;

        @NotBlank(message = "附件地址不能为空")
        private String fileUrl;

        private Long fileSize;
        private String fileType;
    }
}
