package com.vtr.service.impl;

import com.vtr.common.PageResult;
import com.vtr.common.exception.BusinessException;
import com.vtr.common.exception.NotFoundException;
import com.vtr.dto.NoticeCreateDTO;
import com.vtr.dto.NoticeUpdateDTO;
import com.vtr.entity.Notice;
import com.vtr.entity.NoticeAttachment;
import com.vtr.entity.User;
import com.vtr.repository.NoticeAttachmentRepository;
import com.vtr.repository.NoticeRepository;
import com.vtr.repository.UserRepository;
import com.vtr.service.NoticeService;
import com.vtr.service.AdminScopeService;
import com.vtr.vo.NoticeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeServiceImpl implements NoticeService {  // 移除 abstract

    private final NoticeRepository noticeRepository;
    private final NoticeAttachmentRepository noticeAttachmentRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AdminScopeService adminScopeService;

    private static final String CACHE_PREFIX = "notice:";
    private static final String ACTIVE_CACHE = "notices:active";

    @Override
    @Transactional
    public Long create(NoticeCreateDTO dto, Long authorId) {
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new NotFoundException("用户", authorId));

        Notice notice = new Notice();
        BeanUtils.copyProperties(dto, notice);
        notice.setAuthorId(authorId);
        notice.setSchoolId(author.getRole() == User.UserRole.SUPER_ADMIN ? null : author.getSchoolId());
        if (author.getRole() != User.UserRole.SUPER_ADMIN && author.getSchoolId() == null) {
            throw new BusinessException("当前账号未绑定学校，不能发布公告");
        }

        // 设置类型
        if (StringUtils.hasText(dto.getType())) {
            notice.setType(Notice.NoticeType.valueOf(dto.getType()));
        }

        // 设置目标用户
        if (StringUtils.hasText(dto.getTargetUser())) {
            notice.setTargetUser(Notice.TargetUser.valueOf(dto.getTargetUser()));
        }

        // 如果设置了发布时间且是未来，保持草稿状态
        if (notice.getPublishTime() != null &&
                notice.getPublishTime().isAfter(LocalDateTime.now())) {
            notice.setStatus(Notice.NoticeStatus.DRAFT);
        }

        noticeRepository.save(notice);

        // 清除缓存
        evictCache(ACTIVE_CACHE);

        log.info("公告创建成功: {} by {}", notice.getTitle(), author.getUsername());

        return notice.getId();
    }

    @Override
    @Transactional
    public void update(Long id, NoticeUpdateDTO dto) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("公告", id));
        requireNoticeAdminAccess(notice);

        BeanUtils.copyProperties(dto, notice);

        if (StringUtils.hasText(dto.getType())) {
            notice.setType(Notice.NoticeType.valueOf(dto.getType()));
        }
        if (StringUtils.hasText(dto.getTargetUser())) {
            notice.setTargetUser(Notice.TargetUser.valueOf(dto.getTargetUser()));
        }

        noticeRepository.save(notice);

        evictCache(CACHE_PREFIX + id, ACTIVE_CACHE);

        log.info("公告更新成功: {}", id);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("公告", id));
        requireNoticeAdminAccess(notice);

        noticeRepository.softDelete(id);
        noticeAttachmentRepository.deleteByNoticeId(id);

        evictCache(CACHE_PREFIX + id, ACTIVE_CACHE);

        log.info("公告删除成功: {}", id);
    }

    @Override
    @Transactional
    public void pin(Long id, Boolean isTop) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("公告", id));
        requireNoticeAdminAccess(notice);

        notice.setIsTop(isTop);
        noticeRepository.save(notice);

        evictCache(CACHE_PREFIX + id, ACTIVE_CACHE);

        log.info("公告{}置顶: id={}", isTop ? "设置" : "取消", id);
    }

    @Override
    @Transactional
    public NoticeVO getById(Long id, Long userId) {
        User current = userId != null ? userRepository.findById(userId).orElse(null) : null;
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("公告", id));
        if (Boolean.TRUE.equals(notice.getIsDeleted()) || !canViewNotice(notice, current)) {
            // 不向无权用户暴露公告是否存在，统一返回未找到。
            throw new NotFoundException("公告", id);
        }

        String cacheKey = CACHE_PREFIX + id + ":" + noticeScopeKey(current);
        NoticeVO cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        NoticeVO vo = convertToVO(notice);

        // 增加浏览量
        try {
            noticeRepository.incrementViewCount(id);
        } catch (Exception e) {
            log.warn("增加浏览量失败: {}", e.getMessage());
        }

        // 缓存
        writeCache(cacheKey, vo, 2, TimeUnit.HOURS);

        return vo;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<NoticeVO> getNotices(String type, String status, String keyword, Integer page, Integer size) {
        Notice.NoticeType noticeType = null;
        Notice.NoticeStatus noticeStatus = null;

        if (StringUtils.hasText(type)) {
            try {
                noticeType = Notice.NoticeType.valueOf(type);
            } catch (IllegalArgumentException e) {
                // 忽略无效类型
            }
        }
        if (StringUtils.hasText(status)) {
            try {
                noticeStatus = Notice.NoticeStatus.valueOf(status);
            } catch (IllegalArgumentException e) {
                // 忽略无效状态
            }
        }

        // 修改：先按置顶降序，再按创建时间降序
        Sort sort = Sort.by(Sort.Direction.DESC, "isTop")
                .and(Sort.by(Sort.Direction.DESC, "createdAt"));
        PageRequest pageRequest = PageRequest.of(page - 1, size, sort);

        Page<Notice> noticePage = noticeRepository.findByConditions(
                noticeType, noticeStatus, keyword, currentSchoolId(), pageRequest
        );

        List<NoticeVO> list = noticePage.getContent().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return PageResult.of(list, noticePage.getTotalElements(), page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NoticeVO> getActiveNotices(Long userId) {
        User user = userId != null ? userRepository.findById(userId).orElse(null) : null;
        Long schoolId = schoolScopeId(user);
        String activeCacheKey = ACTIVE_CACHE + ":" + (schoolId == null ? "GLOBAL" : schoolId);
        // 尝试缓存
        List<NoticeVO> cached = readCache(activeCacheKey);
        if (cached != null) {
            return filterByUserType(cached, userId);
        }

        Notice.TargetUser userType = user != null
                ? (user.isAdmin() ? Notice.TargetUser.ADMINS : user.isTeacher() ? Notice.TargetUser.TEACHERS : Notice.TargetUser.STUDENTS)
                : Notice.TargetUser.ALL;

        List<Notice> notices = noticeRepository.findActiveNotices(LocalDateTime.now(), userType, schoolId);

        List<NoticeVO> list = notices.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        // 缓存
        writeCache(activeCacheKey, list, 10, TimeUnit.MINUTES);

        return filterByUserType(list, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NoticeVO> getTopNotices() {
        List<Notice> notices = noticeRepository.findTopNotices(LocalDateTime.now(), currentSchoolId());

        return notices.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void publish(Long id) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("公告", id));
        requireNoticeAdminAccess(notice);

        if (notice.getStatus() == Notice.NoticeStatus.PUBLISHED) {
            throw new BusinessException("公告已是发布状态");
        }

        notice.publish();
        noticeRepository.save(notice);

        evictCache(CACHE_PREFIX + id, ACTIVE_CACHE);

        log.info("公告发布成功: {}", id);
    }

    @Override
    @Transactional
    public void withdraw(Long id) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("公告", id));
        requireNoticeAdminAccess(notice);

        if (notice.getStatus() != Notice.NoticeStatus.PUBLISHED) {
            throw new BusinessException("只有已发布的公告才能撤回");
        }

        notice.withdraw();
        noticeRepository.save(notice);

        evictCache(CACHE_PREFIX + id, ACTIVE_CACHE);

        log.info("公告撤回成功: {}", id);
    }

    @Override
    @Transactional
    public void incrementViewCount(Long id) {
        noticeRepository.incrementViewCount(id);
        evictCache(CACHE_PREFIX + id);
    }

    @Override
    @Transactional
    public void markAsRead(Long noticeId, Long userId) {
        // 实现标记已读逻辑（需要 NoticeReadRecord 表）
        log.debug("标记公告已读: noticeId={}, userId={}", noticeId, userId);
    }

    @Override
    public List<Long> getUnreadNoticeIds(Long userId) {
        // 实现获取未读公告ID逻辑
        return List.of();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<NoticeVO> query(String keyword, String type, String status, int page, int size) {
        return getNotices(type, status, keyword, page, size);
    }

    // ========== 私有方法 ==========

    private NoticeVO convertToVO(Notice notice) {
        NoticeVO vo = new NoticeVO();
        BeanUtils.copyProperties(notice, vo);

        if (notice.getType() != null) {
            vo.setType(notice.getType().name());
            vo.setTypeDescription(notice.getType().getDescription());
        }

        if (notice.getStatus() != null) {
            vo.setStatus(notice.getStatus().name());
        }

        if (notice.getTargetUser() != null) {
            vo.setTargetUser(notice.getTargetUser().name());
            vo.setTargetUserDescription(notice.getTargetUser().getDescription());
        }

        // 作者信息 - 返回 nickname
        if (notice.getAuthorId() != null) {
            userRepository.findById(notice.getAuthorId()).ifPresent(author -> {
                vo.setAuthorName(author.getUsername());      // 保留 username
                vo.setAuthorNickname(author.getNickname());  // 新增 nickname
            });
        }

        List<com.vtr.vo.NoticeAttachmentVO> attachments = noticeAttachmentRepository
                .findByNoticeIdOrderByCreatedAtAsc(notice.getId())
                .stream()
                .map(this::convertAttachmentToVO)
                .collect(Collectors.toList());
        vo.setAttachments(attachments);

        return vo;
    }

    private com.vtr.vo.NoticeAttachmentVO convertAttachmentToVO(NoticeAttachment attachment) {
        com.vtr.vo.NoticeAttachmentVO vo = new com.vtr.vo.NoticeAttachmentVO();
        BeanUtils.copyProperties(attachment, vo);
        return vo;
    }

    private List<NoticeVO> filterByUserType(List<NoticeVO> list, Long userId) {
        if (userId == null) {
            return list.stream()
                    .filter(n -> "ALL".equals(n.getTargetUser()))
                    .collect(Collectors.toList());
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return list;
        }

        String userType = user.isAdmin() ? "ADMINS" : user.isTeacher() ? "TEACHERS" : "STUDENTS";

        return list.stream()
                .filter(n -> "ALL".equals(n.getTargetUser()) || n.getTargetUser().equals(userType))
                .collect(Collectors.toList());
    }

    /**
     * Redis 只是公告缓存，缓存服务不可用时不能阻断公告主流程。
     */
    @SuppressWarnings("unchecked")
    private <T> T readCache(String key) {
        try {
            return (T) redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("读取公告缓存失败，回源数据库: key={}, reason={}", key, e.getMessage());
            return null;
        }
    }

    private void writeCache(String key, Object value, long timeout, TimeUnit unit) {
        try {
            redisTemplate.opsForValue().set(key, value, timeout, unit);
        } catch (Exception e) {
            log.warn("写入公告缓存失败，不影响主流程: key={}, reason={}", key, e.getMessage());
        }
    }

    private void evictCache(String... keys) {
        for (String key : keys) {
            try {
                if (ACTIVE_CACHE.equals(key)) {
                    java.util.Set<String> matchingKeys = redisTemplate.keys(key + ":*");
                    if (matchingKeys != null && !matchingKeys.isEmpty()) redisTemplate.delete(matchingKeys);
                } else {
                    redisTemplate.delete(key);
                }
            } catch (Exception e) {
                log.warn("清理公告缓存失败，不影响主流程: key={}, reason={}", key, e.getMessage());
            }
        }
    }

    private Long currentSchoolId() {
        return schoolScopeId(adminScopeService.currentUser());
    }

    /**
     * null 表示平台管理员可查看全部学校；-1 表示未绑定学校或未登录，只能查看全平台公告。
     */
    private Long schoolScopeId(User user) {
        if (user != null && user.getRole() == User.UserRole.SUPER_ADMIN) return null;
        return user != null && user.getSchoolId() != null ? user.getSchoolId() : -1L;
    }

    private boolean canViewNotice(Notice notice, User user) {
        if (user != null && user.getRole() == User.UserRole.SUPER_ADMIN) return true;
        if (notice.getSchoolId() == null) return true;
        return user != null && notice.getSchoolId().equals(user.getSchoolId());
    }

    private String noticeScopeKey(User user) {
        if (user == null) return "PUBLIC";
        if (user.getRole() == User.UserRole.SUPER_ADMIN) return "GLOBAL";
        return user.getSchoolId() == null ? "UNBOUND" : String.valueOf(user.getSchoolId());
    }

    private void requireNoticeAdminAccess(Notice notice) {
        User current = adminScopeService.currentUser();
        if (current == null || current.getRole() == User.UserRole.SUPER_ADMIN) return;
        adminScopeService.requireSchool(notice.getSchoolId());
    }
}
