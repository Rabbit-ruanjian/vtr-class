package com.vtr.service.impl;

import com.vtr.dto.AuditCommentDTO;
import com.vtr.dto.AuditPostDTO;
import com.vtr.dto.ForumCommentCreateDTO;
import com.vtr.dto.ForumPostCreateDTO;
import com.vtr.entity.ForumComment;
import com.vtr.entity.ForumPost;
import com.vtr.entity.ForumPostLike;
import com.vtr.entity.ContentAudit;
import com.vtr.entity.User;
import com.vtr.repository.ForumCommentRepository;
import com.vtr.repository.ForumPostRepository;
import com.vtr.repository.ForumPostLikeRepository;
import com.vtr.repository.UserRepository;
import com.vtr.security.SecurityUtils;
import com.vtr.service.ForumService;
import com.vtr.service.AdminScopeService;
import com.vtr.service.ContentAuditService;
import com.vtr.service.ContentRiskService;
import com.vtr.service.NotificationService;
import com.vtr.vo.ForumCommentVO;
import com.vtr.vo.ForumPostDetailVO;
import com.vtr.vo.ForumPostListVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ForumServiceImpl implements ForumService {

    private final ForumPostRepository postRepository;
    private final ForumCommentRepository commentRepository;
    private final ForumPostLikeRepository postLikeRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AdminScopeService adminScopeService;
    private final ContentRiskService contentRiskService;
    private final ContentAuditService contentAuditService;
    // ========== 前台展示：只显示已审核通过的帖子 ==========
    @Override
    public Page<ForumPostListVO> getPostList(Integer page, Integer size, String postType, String keyword, Long courseId, boolean unresolved) {
        Pageable pageable = PageRequest.of(page - 1, size);
        User current = currentUser();
        Long schoolId = current == null ? null : current.getSchoolId();
        boolean allSchools = current != null && adminScopeService.isGlobalAdmin(current);
        Page<ForumPost> postPage = postRepository.findApprovedPosts(currentAudience(), schoolId, allSchools, courseId, postType,
                keyword == null ? "" : keyword.trim(), unresolved, pageable);

        return postPage.map(this::toListVO);
    }

    private ForumPostListVO toListVO(ForumPost post) {
        ForumPostListVO vo = new ForumPostListVO();
        vo.setId(post.getId()); vo.setAuthorId(post.getAuthorId()); vo.setTitle(post.getTitle()); vo.setContent(post.getContent());
        vo.setPostType(post.getPostType()); vo.setAudience(post.getAudience());
        vo.setCourseId(post.getCourseId()); vo.setCourseName(post.getCourseName()); vo.setActivityId(post.getActivityId());
        vo.setImageUrls(post.getImageUrls()); vo.setAuthorName(post.getAuthorName()); vo.setAuthorAvatar(post.getAuthorAvatar());
        vo.setPinned(post.getPinned() && !post.isPinnedExpired()); vo.setPinnedExpiry(post.getPinnedExpiry());
        vo.setReplyCount(post.getReplyCount()); vo.setViews(post.getViews()); vo.setLikeCount(post.getLikeCount());
        vo.setSolved(post.getSolved()); vo.setCreateTime(post.getCreateTime()); vo.setAuditStatus(post.getAuditStatus());
        Long userId = SecurityUtils.getCurrentUserId();
        vo.setLikedByMe(userId != null && postLikeRepository.findByPostIdAndUserId(post.getId(), userId).isPresent());
        return vo;
    }

    // ========== 管理员：获取待审核帖子 ==========
    @Override
    public Page<ForumPostListVO> getPendingPosts(Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page - 1, size);
        Page<ForumPost> postPage = adminPostPage("PENDING", pageable);

        return postPage.map(post -> {
            ForumPostListVO vo = new ForumPostListVO();
            vo.setId(post.getId());
            vo.setTitle(post.getTitle());
            vo.setAuthorName(post.getAuthorName());
            vo.setAuthorAvatar(post.getAuthorAvatar());
            vo.setPinned(post.getPinned());
            vo.setReplyCount(post.getReplyCount());
            vo.setViews(post.getViews());
            vo.setCreateTime(post.getCreateTime());
            vo.setAuditStatus(post.getAuditStatus());
            // 管理员审核页面显示申请置顶信息
            vo.setRequestPin(post.getRequestPin());
            vo.setRequestPinDays(post.getRequestPinDays());
            return vo;
        });
    }

    // ========== 管理员：获取已通过帖子 ==========
    @Override
    public Page<ForumPostListVO> getApprovedPosts(Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page - 1, size);
        Page<ForumPost> postPage = adminPostPage("APPROVED", pageable);

        return postPage.map(post -> {
            ForumPostListVO vo = new ForumPostListVO();
            vo.setId(post.getId());
            vo.setTitle(post.getTitle());
            vo.setAuthorName(post.getAuthorName());
            vo.setAuthorAvatar(post.getAuthorAvatar());
            vo.setPinned(post.getPinned());
            vo.setPinnedExpiry(post.getPinnedExpiry());
            vo.setReplyCount(post.getReplyCount());
            vo.setViews(post.getViews());
            vo.setCreateTime(post.getCreateTime());
            vo.setAuditStatus(post.getAuditStatus());
            return vo;
        });
    }

    // ========== 管理员：获取已拒绝帖子 ==========
    @Override
    public Page<ForumPostListVO> getRejectedPosts(Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page - 1, size);
        Page<ForumPost> postPage = adminPostPage("REJECTED", pageable);

        return postPage.map(post -> {
            ForumPostListVO vo = new ForumPostListVO();
            vo.setId(post.getId());
            vo.setTitle(post.getTitle());
            vo.setAuthorName(post.getAuthorName());
            vo.setAuthorAvatar(post.getAuthorAvatar());
            vo.setPinned(post.getPinned());
            vo.setReplyCount(post.getReplyCount());
            vo.setViews(post.getViews());
            vo.setCreateTime(post.getCreateTime());
            vo.setAuditStatus(post.getAuditStatus());
            return vo;
        });
    }

    @Override
    public Page<ForumPostListVO> getAllPosts(Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page - 1, size);
        return adminPostAllPage(pageable).map(this::toAdminPostVO);
    }

    private ForumPostListVO toAdminPostVO(ForumPost post) {
        ForumPostListVO vo = new ForumPostListVO();
        vo.setId(post.getId());
        vo.setAuthorId(post.getAuthorId());
        vo.setTitle(post.getTitle());
        vo.setContent(post.getContent());
        vo.setPostType(post.getPostType());
        vo.setAuthorName(post.getAuthorName());
        vo.setAuthorAvatar(post.getAuthorAvatar());
        vo.setPinned(post.getPinned() && !post.isPinnedExpired());
        vo.setPinnedExpiry(post.getPinnedExpiry());
        vo.setReplyCount(post.getReplyCount());
        vo.setViews(post.getViews());
        vo.setCreateTime(post.getCreateTime());
        vo.setAuditStatus(post.getAuditStatus());
        vo.setRequestPin(post.getRequestPin());
        vo.setRequestPinDays(post.getRequestPinDays());
        return vo;
    }

    @Override
    @Transactional
    public ForumPostDetailVO getPostDetail(Long id) {
        // 增加阅读数
        postRepository.incrementViews(id);

        // 查询帖子
        ForumPost post = postRepository.findById(id).orElseThrow(() -> new RuntimeException("帖子不存在"));

        // 获取当前用户信息
        Long currentUserId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = false;

        // 如果用户已登录，检查是否为管理员
        if (currentUserId != null) {
            try {
                User currentUser = userRepository.findById(currentUserId).orElse(null);
                if (currentUser != null) {
                    isAdmin = currentUser.isAdmin();
                }
            } catch (Exception e) {
                log.debug("获取当前用户信息失败: {}", e.getMessage());
            }
        }

        User currentUser = currentUser();
        if (!isAdmin && !java.util.Objects.equals(currentUser == null ? null : currentUser.getSchoolId(), post.getSchoolId())) {
            throw new RuntimeException("只能访问本校教研社区内容");
        }

        if (isAdmin) {
            requireForumSchool(post);
        }

        if (!isAdmin && !"APPROVED".equals(post.getAuditStatus())) {
            throw new RuntimeException("该帖子正在审核中或已被移除");
        }
        if (!isAdmin && !isAudienceVisible(post.getAudience(), currentAudience())) {
            throw new RuntimeException("该内容暂不面向当前账号开放");
        }

        // 查询评论：管理员可以查看所有评论，普通用户查看所有未被明确拒绝的评论
        List<ForumComment> comments;
        if (isAdmin) {
            comments = commentRepository.findByPostIdOrderByCreateTimeAsc(id);
        } else {
            comments = commentRepository.findByPostIdAndAuditStatusOrderByCreateTimeAsc(id, "APPROVED");
        }

        List<ForumCommentVO> commentVOs = comments.stream().map(comment -> {
            ForumCommentVO vo = new ForumCommentVO();
            vo.setId(comment.getId());
            vo.setContent(comment.getContent());
            vo.setAuthorId(comment.getAuthorId());
            vo.setAuthorName(comment.getAuthorName());
            vo.setCreateTime(comment.getCreateTime());
            vo.setAuditStatus(comment.getAuditStatus());
            return vo;
        }).collect(Collectors.toList());

        // 组装返回数据
        ForumPostDetailVO vo = new ForumPostDetailVO();
        vo.setId(post.getId());
        vo.setTitle(post.getTitle());
        vo.setContent(post.getContent());
        vo.setPostType(post.getPostType());
        vo.setAudience(post.getAudience());
        vo.setCourseId(post.getCourseId());
        vo.setCourseName(post.getCourseName());
        vo.setActivityId(post.getActivityId());
        vo.setImageUrls(post.getImageUrls());
        vo.setAuthorId(post.getAuthorId());
        vo.setAuthorName(post.getAuthorName());
        vo.setAuthorAvatar(post.getAuthorAvatar());
        vo.setPinned(post.getPinned() && !post.isPinnedExpired());
        vo.setPinnedExpiry(post.getPinnedExpiry());
        vo.setReplyCount(post.getReplyCount());
        vo.setViews(post.getViews());
        vo.setLikeCount(post.getLikeCount());
        vo.setLikedByMe(currentUserId != null && postLikeRepository.findByPostIdAndUserId(post.getId(), currentUserId).isPresent());
        vo.setSolved(post.getSolved());
        vo.setCreateTime(post.getCreateTime());
        vo.setComments(commentVOs);
        vo.setAuditStatus(post.getAuditStatus());
        vo.setAuditRemark(post.getAuditRemark());

        return vo;
    }

    @Override
    @Transactional
    public void createPost(ForumPostCreateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        String userName = SecurityUtils.getCurrentUserName();
        String userAvatar = SecurityUtils.getCurrentUserAvatar();

        ForumPost post = new ForumPost();
        post.setTitle(dto.getTitle());
        post.setContent(dto.getContent());
        post.setPostType(normalizePostType(dto.getPostType()));
        post.setAudience(normalizeAudience(dto.getAudience()));
        post.setCourseId(dto.getCourseId());
        post.setCourseName(blankToNull(dto.getCourseName()));
        post.setActivityId(dto.getActivityId());
        post.setImageUrls(dto.getImageUrls());
        post.setAuthorId(userId);
        post.setAuthorName(userName);
        post.setAuthorAvatar(userAvatar);
        User author = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("用户不存在"));
        post.setSchoolId(author.getSchoolId());
        post.setPinned(false);
        post.setReplyCount(0);
        post.setViews(0);
        ContentRiskService.RiskAssessment risk = contentRiskService.assess(dto.getTitle(), dto.getContent());
        post.setAuditStatus(risk.requiresManualReview() ? "PENDING" : "APPROVED");

        // 处理申请置顶
        if (dto.getRequestPin() != null && dto.getRequestPin()) {
            post.setRequestPin(true);
            int pinDays = dto.getPinDays() != null ? Math.min(dto.getPinDays(), 3) : 1;
            post.setRequestPinDays(pinDays);
            log.info("帖子申请置顶: {}天", pinDays);
        }

        postRepository.save(post);
        if (risk.requiresManualReview()) {
            contentAuditService.createOrEscalate(ContentAudit.ContentType.FORUM_POST, post.getId(),
                    post.getTitle(), post.getContent(), userId, userName, post.getSchoolId(),
                    risk.level(), risk.reason());
            notifyAdminsAboutPendingPost(post);
            log.info("高风险帖子进入人工复核: title={}, author={}, reason={}", dto.getTitle(), userName, risk.reason());
        } else {
            log.info("普通帖子直接公开: title={}, author={}", dto.getTitle(), userName);
        }
    }

    @Override
    @Transactional
    public boolean toggleLike(Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) throw new RuntimeException("请先登录");
        ForumPost post = postRepository.findById(id).orElseThrow(() -> new RuntimeException("帖子不存在"));
        User actor = currentUser();
        if (actor == null || (!actor.isAdmin() && !java.util.Objects.equals(actor.getSchoolId(), post.getSchoolId()))) {
            throw new RuntimeException("只能访问本校教研社区内容");
        }
        java.util.Optional<ForumPostLike> existing = postLikeRepository.findByPostIdAndUserId(id, userId);
        boolean liked;
        if (existing.isPresent()) {
            postLikeRepository.delete(existing.get());
            post.setLikeCount(Math.max(0, post.getLikeCount() == null ? 0 : post.getLikeCount() - 1));
            liked = false;
        } else {
            postLikeRepository.save(ForumPostLike.builder().postId(id).userId(userId).build());
            post.setLikeCount((post.getLikeCount() == null ? 0 : post.getLikeCount()) + 1);
            liked = true;
        }
        postRepository.save(post);
        return liked;
    }

    private String normalizePostType(String value) {
        String type = value == null ? "QUESTION" : value.trim().toUpperCase();
        return List.of("QUESTION", "NOTE", "RESOURCE", "TEACHING", "CASE", "RESULT").contains(type) ? type : "QUESTION";
    }

    private String normalizeAudience(String value) {
        String audience = value == null ? "ALL" : value.trim().toUpperCase();
        return List.of("ALL", "STUDENTS", "TEACHERS").contains(audience) ? audience : "ALL";
    }

    private User currentUser() {
        Long userId = SecurityUtils.getCurrentUserId();
        return userId == null ? null : userRepository.findById(userId).orElse(null);
    }

    private String currentAudience() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return "PUBLIC";
        return userRepository.findById(userId)
                .map(user -> user.isAdmin() ? "ADMIN" : user.isStudent() ? "STUDENTS" : user.isTeacher() ? "TEACHERS" : "ALL")
                .orElse("ALL");
    }

    private boolean isAudienceVisible(String postAudience, String audience) {
        return postAudience == null || postAudience.isBlank() || "ALL".equals(postAudience) || postAudience.equals(audience);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Page<ForumPost> adminPostPage(String status, Pageable pageable) {
        User current = adminScopeService.currentUser();
        if (adminScopeService.isGlobalAdmin(current)) {
            return postRepository.findByAuditStatusOrderByCreateTimeDesc(status, pageable);
        }
        if (adminScopeService.isUnboundUserManager(current)) {
            return postRepository.findByAuditStatusAndSchoolIdIsNullOrderByCreateTimeDesc(status, pageable);
        }
        if (current != null && current.getSchoolId() != null) {
            return postRepository.findByAuditStatusAndSchoolIdOrderByCreateTimeDesc(status, current.getSchoolId(), pageable);
        }
        return Page.empty(pageable);
    }

    private Page<ForumComment> adminCommentPage(String status, Pageable pageable) {
        User current = adminScopeService.currentUser();
        if (adminScopeService.isGlobalAdmin(current)) {
            return commentRepository.findByAuditStatusOrderByCreateTimeAsc(status, pageable);
        }
        if (adminScopeService.isUnboundUserManager(current)) {
            return commentRepository.findByAuditStatusAndSchoolIdIsNullOrderByCreateTimeAsc(status, pageable);
        }
        if (current != null && current.getSchoolId() != null) {
            return commentRepository.findByAuditStatusAndSchoolIdOrderByCreateTimeAsc(status, current.getSchoolId(), pageable);
        }
        return Page.empty(pageable);
    }

    private void notifyAdminsAboutPendingComment(ForumComment comment, ForumPost post) {
        List<User> admins = new java.util.ArrayList<>(userRepository.findAllByRoleAndStatus(
                User.UserRole.ADMIN, User.UserStatus.ACTIVE));
        admins.addAll(userRepository.findAllByRoleAndStatus(User.UserRole.SUPER_ADMIN, User.UserStatus.ACTIVE));
        admins.stream()
                .filter(admin -> admin.getRole() == User.UserRole.SUPER_ADMIN
                        || (post.getSchoolId() == null && Boolean.TRUE.equals(admin.getAdminScopeUnbound()))
                        || (post.getSchoolId() != null && post.getSchoolId().equals(admin.getSchoolId())))
                .forEach(admin -> notificationService.sendNotification(
                        admin.getId(), "FORUM_COMMENT_PENDING", "社区留言触发风险复核",
                        "帖子《" + post.getTitle() + "》下的留言触发风险规则，请前往管理中心的风险与举报队列处理。",
                        comment.getId()));
    }

    private void notifyAdminsAboutPendingPost(ForumPost post) {
        List<User> admins = new java.util.ArrayList<>(userRepository.findAllByRoleAndStatus(
                User.UserRole.ADMIN, User.UserStatus.ACTIVE));
        admins.addAll(userRepository.findAllByRoleAndStatus(User.UserRole.SUPER_ADMIN, User.UserStatus.ACTIVE));
        admins.stream()
                .filter(admin -> admin.getRole() == User.UserRole.SUPER_ADMIN
                        || (post.getSchoolId() == null && Boolean.TRUE.equals(admin.getAdminScopeUnbound()))
                        || (post.getSchoolId() != null && post.getSchoolId().equals(admin.getSchoolId())))
                .forEach(admin -> notificationService.sendNotification(
                        admin.getId(), "FORUM_POST_PENDING", "社区内容触发风险复核",
                        "社区内容《" + post.getTitle() + "》触发风险规则，请前往管理中心的风险与举报队列处理。",
                        post.getId()));
    }

    @Override
    @Transactional
    public void addComment(ForumCommentCreateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        String userName = SecurityUtils.getCurrentUserName();

        // 检查帖子是否存在且未被明确拒绝
        ForumPost post = postRepository.findById(dto.getPostId())
                .orElseThrow(() -> new RuntimeException("帖子不存在"));
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        if (!java.util.Objects.equals(author.getSchoolId(), post.getSchoolId())) {
            throw new RuntimeException("只能在本校教研社区内容下留言");
        }
        if ("REJECTED".equals(post.getAuditStatus())) {
            throw new RuntimeException("该帖子已被移除，暂不可评论");
        }

        ForumComment comment = new ForumComment();
        comment.setPostId(dto.getPostId());
        comment.setContent(dto.getContent());
        comment.setAuthorId(userId);
        comment.setAuthorName(userName);
        comment.setSchoolId(post.getSchoolId());
        ContentRiskService.RiskAssessment risk = contentRiskService.assess(dto.getContent());
        comment.setAuditStatus(risk.requiresManualReview() ? "PENDING" : "APPROVED");

        commentRepository.save(comment);
        if (risk.requiresManualReview()) {
            contentAuditService.createOrEscalate(ContentAudit.ContentType.FORUM_COMMENT, comment.getId(),
                    "社区评论 #" + comment.getId(), comment.getContent(), userId, userName, comment.getSchoolId(),
                    risk.level(), risk.reason());
            notifyAdminsAboutPendingComment(comment, post);
            log.info("高风险评论进入人工复核: postId={}, author={}, reason={}", dto.getPostId(), userName, risk.reason());
        } else {
            postRepository.incrementReplyCount(comment.getPostId());
            log.info("普通评论直接公开: postId={}, author={}", dto.getPostId(), userName);
        }
    }

    @Override
    @Transactional
    public void deletePost(Long id) {
        ForumPost post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("帖子不存在"));
        if (post.getSchoolId() == null) {
            if (!adminScopeService.isGlobalAdmin(adminScopeService.currentUser())) {
                throw new RuntimeException("该帖子没有明确学校归属");
            }
        } else {
            adminScopeService.requireSchool(post.getSchoolId());
        }
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!SecurityUtils.isAdmin() && (currentUserId == null || !currentUserId.equals(post.getAuthorId()))) {
            throw new RuntimeException("无权删除该帖子");
        }
        // 先删除关联的评论
        commentRepository.deleteByPostId(id);
        // 删除帖子
        postRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void deleteComment(Long id) {
        ForumComment comment = commentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("评论不存在"));
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!SecurityUtils.isAdmin() && (currentUserId == null || !currentUserId.equals(comment.getAuthorId()))) {
            throw new RuntimeException("无权删除该评论");
        }
        boolean wasApproved = comment.isApproved();
        commentRepository.deleteById(id);
        if (wasApproved) {
            postRepository.decrementReplyCount(comment.getPostId());
        }
    }

    // ========== 审核帖子 ==========
    @Override
    @Transactional
    public void auditPost(Long id, AuditPostDTO dto, Long reviewerId, String reviewerName) {
        ForumPost post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("帖子不存在"));
        if (post.getSchoolId() == null) {
            if (!adminScopeService.isGlobalAdmin(adminScopeService.currentUser())) {
                throw new RuntimeException("该帖子没有明确学校归属");
            }
        } else {
            adminScopeService.requireSchool(post.getSchoolId());
        }

        if ("APPROVED".equals(dto.getStatus())) {
            post.approve(reviewerId, dto.getRemark());

            // 处理置顶
            if (dto.getPin() != null && dto.getPin()) {
                int pinDays = dto.getPinDays() != null ? dto.getPinDays() :
                        (post.getRequestPinDays() != null ? post.getRequestPinDays() : 1);
                LocalDateTime pinnedExpiry = LocalDateTime.now().plusDays(pinDays);
                post.setPinned(true);
                post.setPinnedExpiry(pinnedExpiry);
                log.info("帖子置顶: id={}, 置顶天数={}, 过期时间={}", id, pinDays, pinnedExpiry);
            }

            log.info("帖子审核通过: id={}, title={}, reviewer={}", id, post.getTitle(), reviewerName);
        } else if ("REJECTED".equals(dto.getStatus())) {
            post.reject(reviewerId, dto.getRemark());
            log.info("帖子审核拒绝: id={}, title={}, reviewer={}, reason={}",
                    id, post.getTitle(), reviewerName, dto.getRemark());
        } else {
            throw new RuntimeException("无效的审核状态");
        }

        postRepository.save(post);
        notificationService.sendNotification(
                post.getAuthorId(),
                "APPROVED".equals(dto.getStatus()) ? "FORUM_POST_APPROVED" : "FORUM_POST_REJECTED",
                "APPROVED".equals(dto.getStatus()) ? "论坛帖子审核通过" : "论坛帖子审核未通过",
                String.format("您的帖子《%s》%s。%s", post.getTitle(),
                        "APPROVED".equals(dto.getStatus()) ? "已通过审核" : "未通过审核",
                        dto.getRemark() == null ? "" : "审核意见：" + dto.getRemark()),
                post.getId());
    }

    // ========== 取消置顶 ==========
    @Override
    @Transactional
    public void cancelPin(Long id, Long reviewerId, String reviewerName) {
        ForumPost post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("帖子不存在"));

        post.setPinned(false);
        post.setPinnedExpiry(null);
        postRepository.save(post);

        log.info("取消帖子置顶: id={}, title={}, reviewer={}", id, post.getTitle(), reviewerName);
    }

    // ========== 获取待审核评论 ==========
    @Override
    public Page<ForumCommentVO> getPendingComments(Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page - 1, size);
        Page<ForumComment> commentPage = adminCommentPage("PENDING", pageable);

        return commentPage.map(comment -> {
            ForumCommentVO vo = new ForumCommentVO();
            vo.setId(comment.getId());
            vo.setContent(comment.getContent());
            vo.setPostId(comment.getPostId());
            vo.setAuthorId(comment.getAuthorId());
            vo.setAuthorName(comment.getAuthorName());
            vo.setCreateTime(comment.getCreateTime());
            vo.setAuditStatus(comment.getAuditStatus());
            return vo;
        });
    }

    // ========== 获取已通过评论 ==========
    @Override
    public Page<ForumCommentVO> getApprovedComments(Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page - 1, size);
        Page<ForumComment> commentPage = adminCommentPage("APPROVED", pageable);

        return commentPage.map(comment -> {
            ForumCommentVO vo = new ForumCommentVO();
            vo.setId(comment.getId());
            vo.setContent(comment.getContent());
            vo.setPostId(comment.getPostId());
            vo.setAuthorId(comment.getAuthorId());
            vo.setAuthorName(comment.getAuthorName());
            vo.setCreateTime(comment.getCreateTime());
            vo.setAuditStatus(comment.getAuditStatus());
            return vo;
        });
    }

    // ========== 获取已拒绝评论 ==========
    @Override
    public Page<ForumCommentVO> getRejectedComments(Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page - 1, size);
        Page<ForumComment> commentPage = adminCommentPage("REJECTED", pageable);

        return commentPage.map(comment -> {
            ForumCommentVO vo = new ForumCommentVO();
            vo.setId(comment.getId());
            vo.setContent(comment.getContent());
            vo.setPostId(comment.getPostId());
            vo.setAuthorId(comment.getAuthorId());
            vo.setAuthorName(comment.getAuthorName());
            vo.setCreateTime(comment.getCreateTime());
            vo.setAuditStatus(comment.getAuditStatus());
            return vo;
        });
    }

    @Override
    public Page<ForumCommentVO> getAllComments(Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page - 1, size);
        return adminCommentAllPage(pageable).map(this::toCommentVO);
    }

    private Page<ForumPost> adminPostAllPage(Pageable pageable) {
        User current = adminScopeService.currentUser();
        if (adminScopeService.isGlobalAdmin(current)) return postRepository.findAllByOrderByCreateTimeDesc(pageable);
        if (adminScopeService.isUnboundUserManager(current)) return postRepository.findBySchoolIdIsNullOrderByCreateTimeDesc(pageable);
        if (current != null && current.getSchoolId() != null) {
            return postRepository.findBySchoolIdOrderByCreateTimeDesc(current.getSchoolId(), pageable);
        }
        return Page.empty(pageable);
    }

    private Page<ForumComment> adminCommentAllPage(Pageable pageable) {
        User current = adminScopeService.currentUser();
        if (adminScopeService.isGlobalAdmin(current)) return commentRepository.findAllByOrderByCreateTimeDesc(pageable);
        if (adminScopeService.isUnboundUserManager(current)) return commentRepository.findBySchoolIdIsNullOrderByCreateTimeDesc(pageable);
        if (current != null && current.getSchoolId() != null) {
            return commentRepository.findBySchoolIdOrderByCreateTimeDesc(current.getSchoolId(), pageable);
        }
        return Page.empty(pageable);
    }

    private ForumCommentVO toCommentVO(ForumComment comment) {
        ForumCommentVO vo = new ForumCommentVO();
        vo.setId(comment.getId());
        vo.setContent(comment.getContent());
        vo.setPostId(comment.getPostId());
        vo.setAuthorId(comment.getAuthorId());
        vo.setAuthorName(comment.getAuthorName());
        vo.setCreateTime(comment.getCreateTime());
        vo.setAuditStatus(comment.getAuditStatus());
        return vo;
    }

    // ========== 审核评论 ==========
    @Override
    @Transactional
    public void auditComment(Long id, AuditCommentDTO dto, Long reviewerId, String reviewerName) {
        ForumComment comment = commentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("评论不存在"));
        ForumPost post = postRepository.findById(comment.getPostId())
                .orElseThrow(() -> new RuntimeException("帖子不存在"));
        requireForumSchool(post);

        boolean wasPending = comment.isPending();
        if ("APPROVED".equals(dto.getStatus())) {
            comment.approve(reviewerId, dto.getRemark());
            if (wasPending) {
                postRepository.incrementReplyCount(comment.getPostId());
            }
            log.info("评论审核通过: id={}, content={}, reviewer={}", id, comment.getContent(), reviewerName);
        } else if ("REJECTED".equals(dto.getStatus())) {
            comment.reject(reviewerId, dto.getRemark());
            log.info("评论审核拒绝: id={}, content={}, reviewer={}, reason={}",
                    id, comment.getContent(), reviewerName, dto.getRemark());
        } else {
            throw new RuntimeException("无效的审核状态");
        }

        commentRepository.save(comment);
        notificationService.sendNotification(
                comment.getAuthorId(),
                "APPROVED".equals(dto.getStatus()) ? "FORUM_COMMENT_APPROVED" : "FORUM_COMMENT_REJECTED",
                "APPROVED".equals(dto.getStatus()) ? "论坛留言审核通过" : "论坛留言审核未通过",
                String.format("您提交的论坛留言%s。%s",
                        "APPROVED".equals(dto.getStatus()) ? "已通过审核" : "未通过审核",
                        dto.getRemark() == null ? "" : "审核意见：" + dto.getRemark()),
                comment.getPostId());

        if ("APPROVED".equals(dto.getStatus())) {
            postRepository.findById(comment.getPostId())
                    .filter(parentPost -> !comment.getAuthorId().equals(parentPost.getAuthorId()))
                    .ifPresent(parentPost -> notificationService.sendNotification(
                            parentPost.getAuthorId(),
                            "FORUM_REPLY",
                            "论坛收到新留言",
                            String.format("用户“%s”回复了您的帖子《%s》。", comment.getAuthorName(), parentPost.getTitle()),
                            parentPost.getId()));
        }
    }

    private void requireForumSchool(ForumPost post) {
        if (post.getSchoolId() == null) {
            if (!adminScopeService.isGlobalAdmin(adminScopeService.currentUser())) {
                throw new RuntimeException("该内容没有明确学校归属");
            }
            return;
        }
        adminScopeService.requireSchool(post.getSchoolId());
    }

}
