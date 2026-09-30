package com.vtr.service.impl;

import com.vtr.common.PageResult;
import com.vtr.common.exception.BusinessException;
import com.vtr.common.exception.NotFoundException;
import com.vtr.dto.SnippetCreateDTO;
import com.vtr.dto.SnippetReviewDTO;
import com.vtr.dto.SnippetSearchDTO;
import com.vtr.dto.SnippetUpdateDTO;
import com.vtr.entity.CodeSnippet;
import com.vtr.entity.User;
import com.vtr.repository.CodeSnippetRepository;
import com.vtr.repository.UserRepository;
import com.vtr.service.CodeSnippetService;
import com.vtr.vo.CodeSnippetVO;
import com.vtr.vo.SnippetStatisticsVO;
import com.vtr.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodeSnippetServiceImpl implements CodeSnippetService {

    private final CodeSnippetRepository snippetRepository;
    private final UserRepository userRepository;

    // 简单的点赞记录（生产环境应使用Redis）
    private final Map<String, Boolean> likeCache = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public Long create(SnippetCreateDTO dto, Long authorId) {
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new NotFoundException("用户", authorId));

        CodeSnippet snippet = new CodeSnippet();
        BeanUtils.copyProperties(dto, snippet);
        snippet.setAuthor(author);
        snippet.setTags(dto.getTags() != null ? dto.getTags() : List.of());

        snippetRepository.save(snippet);

        log.info("创建代码片段: id={}, author={}", snippet.getId(), author.getUsername());

        return snippet.getId();
    }

    @Override
    @Transactional
    public void update(Long id, SnippetUpdateDTO dto, Long userId) {
        CodeSnippet snippet = snippetRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("代码片段", id));

        // 检查权限
        if (!snippet.getAuthor().getId().equals(userId) && !isAdmin(userId)) {
            throw new BusinessException("无权修改此代码片段");
        }

        // 已审核通过的不能修改代码
        if (snippet.getStatus() == CodeSnippet.SnippetStatus.APPROVED &&
                !dto.getCode().equals(snippet.getCode())) {
            // 重新提交审核
            snippet.setStatus(CodeSnippet.SnippetStatus.PENDING);
        }

        BeanUtils.copyProperties(dto, snippet, "id", "author", "status", "viewCount", "likeCount");
        snippet.setTags(dto.getTags() != null ? dto.getTags() : List.of());

        snippetRepository.save(snippet);

        log.info("更新代码片段: id={}", id);
    }

    @Override
    @Transactional
    public void delete(Long id, Long userId) {
        CodeSnippet snippet = snippetRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("代码片段", id));

        if (!snippet.getAuthor().getId().equals(userId) && !isAdmin(userId)) {
            throw new BusinessException("无权删除此代码片段");
        }

        snippetRepository.softDelete(id);

        log.info("删除代码片段: id={}", id);
    }

    @Override
    @Transactional
    public CodeSnippetVO getById(Long id) {
        CodeSnippet snippet = snippetRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("代码片段", id));

        // 增加浏览量
        snippetRepository.incrementViewCount(id);
        snippet.setViewCount(snippet.getViewCount() + 1);

        Long currentUserId = getCurrentUserId();

        return convertToVO(snippet, currentUserId);
    }

    @Override
    public PageResult<CodeSnippetVO> search(SnippetSearchDTO query) {
        log.info("搜索参数: keyword={}, language={}, page={}, size={}",
                query.getKeyword(), query.getLanguage(), query.getPage(), query.getSize());

        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        if ("viewCount".equals(query.getSortBy())) {
            sort = "asc".equals(query.getSortOrder()) ?
                    Sort.by("viewCount").ascending() : Sort.by("viewCount").descending();
        } else if ("likeCount".equals(query.getSortBy())) {
            sort = "asc".equals(query.getSortOrder()) ?
                    Sort.by("likeCount").ascending() : Sort.by("likeCount").descending();
        }

        PageRequest pageRequest = PageRequest.of(query.getPage() - 1, query.getSize(), sort);

        Page<CodeSnippet> page;
        String keyword = query.getKeyword();
        String language = query.getLanguage();

        boolean hasKeyword = keyword != null && !keyword.trim().isEmpty();
        boolean hasLanguage = language != null && !language.trim().isEmpty();

        if (hasKeyword && hasLanguage) {
            page = snippetRepository.searchApprovedSnippets(
                    CodeSnippet.SnippetStatus.APPROVED, keyword, language, pageRequest);
        } else if (hasKeyword) {
            page = snippetRepository.searchApprovedSnippetsByKeyword(
                    CodeSnippet.SnippetStatus.APPROVED, keyword, pageRequest);
        } else if (hasLanguage) {
            page = snippetRepository.findApprovedSnippetsByLanguage(
                    CodeSnippet.SnippetStatus.APPROVED, language, pageRequest);
        } else {
            page = snippetRepository.findApprovedSnippets(
                    CodeSnippet.SnippetStatus.APPROVED, pageRequest);
        }

        log.info("查询结果: 总条数={}, 当前页数据量={}", page.getTotalElements(), page.getNumberOfElements());

        Long currentUserId = getCurrentUserId();

        List<CodeSnippetVO> list = page.getContent().stream()
                .map(s -> convertToVO(s, currentUserId))
                .collect(Collectors.toList());

        return PageResult.of(list, page.getTotalElements(), query.getPage(), query.getSize());
    }

    @Override
    public PageResult<CodeSnippetVO> getUserSnippets(Long userId, PageRequest pageRequest) {
        Page<CodeSnippet> snippets = snippetRepository.findByAuthorIdAndIsDeletedFalse(userId, pageRequest);

        Long currentUserId = getCurrentUserId();

        List<CodeSnippetVO> list = snippets.getContent().stream()
                .map(s -> convertToVO(s, currentUserId))
                .collect(Collectors.toList());

        return PageResult.of(list, snippets.getTotalElements(),
                pageRequest.getPageNumber() + 1, pageRequest.getPageSize());
    }

    @Override
    public PageResult<CodeSnippetVO> getPendingSnippets(Integer page, Integer size) {
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by("createdAt").ascending());

        Page<CodeSnippet> snippets = snippetRepository.findPendingSnippets(pageRequest);

        Long currentUserId = getCurrentUserId();

        List<CodeSnippetVO> list = snippets.getContent().stream()
                .map(s -> convertToVO(s, currentUserId))
                .collect(Collectors.toList());

        return PageResult.of(list, snippets.getTotalElements(), page, size);
    }

    // ========== 新增：获取已通过的代码片段 ==========
    @Override
    public PageResult<CodeSnippetVO> getApprovedList(PageRequest pageRequest) {
        Page<CodeSnippet> page = snippetRepository.findByStatusAndIsDeletedFalse(
                CodeSnippet.SnippetStatus.APPROVED, pageRequest);
        List<CodeSnippetVO> list = page.getContent().stream()
                .map(s -> convertToVO(s, null))
                .collect(Collectors.toList());
        return PageResult.of(list, page.getTotalElements(),
                pageRequest.getPageNumber() + 1, pageRequest.getPageSize());
    }

    // ========== 新增：获取已拒绝的代码片段 ==========
    @Override
    public PageResult<CodeSnippetVO> getRejectedList(PageRequest pageRequest) {
        Page<CodeSnippet> page = snippetRepository.findByStatusAndIsDeletedFalse(
                CodeSnippet.SnippetStatus.REJECTED, pageRequest);
        List<CodeSnippetVO> list = page.getContent().stream()
                .map(s -> convertToVO(s, null))
                .collect(Collectors.toList());
        return PageResult.of(list, page.getTotalElements(),
                pageRequest.getPageNumber() + 1, pageRequest.getPageSize());
    }

    @Override
    @Transactional
    public void review(Long id, SnippetReviewDTO dto, Long reviewerId) {
        CodeSnippet snippet = snippetRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("代码片段", id));

        if (snippet.getStatus() != CodeSnippet.SnippetStatus.PENDING) {
            throw new BusinessException("该片段已审核");
        }

        if ("APPROVE".equals(dto.getAction())) {
            snippet.approve(reviewerId, dto.getRemark());
        } else {
            snippet.reject(reviewerId, dto.getRemark());
        }

        snippetRepository.save(snippet);

        log.info("审核代码片段: id={}, 结果={}, 审核人={}", id, dto.getAction(), reviewerId);
    }

    @Override
    @Transactional
    public void like(Long id, Long userId) {
        if (userId == null) {
            throw new BusinessException("请先登录");
        }

        String key = userId + ":" + id;

        if (likeCache.containsKey(key)) {
            throw new BusinessException("已经点赞过了");
        }

        snippetRepository.incrementLikeCount(id);
        likeCache.put(key, true);

        log.info("点赞代码片段: snippetId={}, userId={}", id, userId);
    }

    @Override
    @Transactional
    public void unlike(Long id, Long userId) {
        if (userId == null) {
            throw new BusinessException("请先登录");
        }

        String key = userId + ":" + id;

        if (!likeCache.containsKey(key)) {
            throw new BusinessException("还没有点赞");
        }

        snippetRepository.decrementLikeCount(id);
        likeCache.remove(key);

        log.info("取消点赞: snippetId={}, userId={}", id, userId);
    }

    @Override
    @Transactional
    public void incrementViewCount(Long id) {
        snippetRepository.incrementViewCount(id);
    }

    @Override
    @Transactional
    public void incrementDownloadCount(Long id) {
        snippetRepository.incrementDownloadCount(id);
    }

    @Override
    public List<String> getHotTags(Integer limit) {
        return snippetRepository.findHotTags(PageRequest.of(0, limit));
    }

    @Override
    public SnippetStatisticsVO getStatistics() {
        SnippetStatisticsVO vo = new SnippetStatisticsVO();

        vo.setTotalSnippets(snippetRepository.count());
        vo.setApprovedSnippets((long) snippetRepository.findByStatusAndIsDeletedFalse(
                CodeSnippet.SnippetStatus.APPROVED, PageRequest.of(0, 1)).getTotalElements());
        vo.setPendingSnippets((long) snippetRepository.findByStatusAndIsDeletedFalse(
                CodeSnippet.SnippetStatus.PENDING, PageRequest.of(0, 1)).getTotalElements());

        Map<String, Long> langDist = snippetRepository.countByLanguage().stream()
                .collect(Collectors.toMap(
                        arr -> (String) arr[0],
                        arr -> (Long) arr[1]
                ));
        vo.setLanguageDistribution(langDist);

        vo.setHotTags(getHotTags(20));
        vo.setPopularSnippets(snippetRepository.findPopularSnippets(PageRequest.of(0, 5))
                .stream().map(s -> convertToVO(s, null)).collect(Collectors.toList()));

        return vo;
    }

    @Override
    public String highlightCode(String code, String language) {
        return code;
    }

    @Override
    public PageResult<CodeSnippetVO> getPendingList(PageRequest pageRequest) {
        return getPendingSnippets(pageRequest.getPageNumber() + 1, pageRequest.getPageSize());
    }

    @Override
    public List<CodeSnippetVO> getAllApproved() {
        List<CodeSnippet> snippets = snippetRepository.findAllApproved(CodeSnippet.SnippetStatus.APPROVED);
        return snippets.stream()
                .map(s -> convertToVO(s, null))
                .collect(Collectors.toList());
    }

    @Override
    public List<CodeSnippet> getAllRaw() {
        return snippetRepository.findAll();
    }

    // ========== 审核方法 ==========
    @Override
    @Transactional
    public void reviewSnippet(Long id, String action, String remark, Long reviewerId, String reviewerName) {
        CodeSnippet snippet = snippetRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("代码片段", id));

        if (snippet.getStatus() != CodeSnippet.SnippetStatus.PENDING) {
            throw new BusinessException("该片段已审核");
        }

        if ("APPROVE".equals(action)) {
            snippet.approve(reviewerId, remark);
            log.info("代码片段审核通过: id={}, reviewer={}", id, reviewerName);
        } else if ("REJECT".equals(action)) {
            snippet.reject(reviewerId, remark);
            log.info("代码片段审核拒绝: id={}, reviewer={}, reason={}", id, reviewerName, remark);
        } else {
            throw new BusinessException("无效的审核操作: " + action);
        }

        snippetRepository.save(snippet);
    }

    // ========== 私有方法 ==========

    private CodeSnippetVO convertToVO(CodeSnippet snippet, Long viewerId) {
        CodeSnippetVO vo = new CodeSnippetVO();
        BeanUtils.copyProperties(snippet, vo);

        vo.setCreatedAt(snippet.getCreatedAt());
        vo.setUpdatedAt(snippet.getUpdatedAt());

        if (snippet.getAuthor() != null) {
            UserVO authorVO = new UserVO();
            BeanUtils.copyProperties(snippet.getAuthor(), authorVO);

            vo.setAuthor(authorVO);
            vo.setAuthorName(authorVO.getNickname() != null ? authorVO.getNickname() : authorVO.getUsername());
            vo.setAuthorAvatar(authorVO.getAvatar());
        }

        vo.setStatus(snippet.getStatus().name());

        if (viewerId != null) {
            vo.setIsLiked(likeCache.containsKey(viewerId + ":" + snippet.getId()));
            vo.setIsAuthor(viewerId.equals(snippet.getAuthor().getId()));
        }

        return vo;
    }

    private boolean isAdmin(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        return user != null && user.isAdmin();
    }

    private Long getCurrentUserId() {
        try {
            org.springframework.security.core.Authentication authentication =
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null && authentication.isAuthenticated()
                    && !"anonymousUser".equals(authentication.getPrincipal())) {

                Object principal = authentication.getPrincipal();

                if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
                    String username = ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
                    return userRepository.findByUsername(username)
                            .map(User::getId)
                            .orElse(null);
                }
            }
        } catch (Exception e) {
            log.debug("获取当前用户ID失败: {}", e.getMessage());
        }
        return null;
    }
}