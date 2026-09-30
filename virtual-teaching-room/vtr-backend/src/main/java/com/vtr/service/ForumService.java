package com.vtr.service;

import com.vtr.dto.AuditCommentDTO;
import com.vtr.dto.AuditPostDTO;
import com.vtr.dto.ForumCommentCreateDTO;
import com.vtr.dto.ForumPostCreateDTO;
import com.vtr.vo.ForumCommentVO;
import com.vtr.vo.ForumPostDetailVO;
import com.vtr.vo.ForumPostListVO;
import org.springframework.data.domain.Page;

public interface ForumService {

    // 帖子相关
    Page<ForumPostListVO> getPostList(Integer page, Integer size, String postType, String keyword, Long courseId, boolean unresolved);
    ForumPostDetailVO getPostDetail(Long id);
    void createPost(ForumPostCreateDTO dto);
    void deletePost(Long id);
    void deleteComment(Long id);
    boolean toggleLike(Long id);

    // 帖子审核
    void auditPost(Long id, AuditPostDTO dto, Long reviewerId, String reviewerName);
    Page<ForumPostListVO> getPendingPosts(Integer page, Integer size);
    Page<ForumPostListVO> getApprovedPosts(Integer page, Integer size);
    Page<ForumPostListVO> getRejectedPosts(Integer page, Integer size);
    Page<ForumPostListVO> getAllPosts(Integer page, Integer size);
    // 在 ForumService.java 中添加

    /**
     * 取消帖子置顶
     */
    void cancelPin(Long id, Long reviewerId, String reviewerName);
    // 评论相关
    void addComment(ForumCommentCreateDTO dto);

    // 评论审核
    Page<ForumCommentVO> getPendingComments(Integer page, Integer size);
    Page<ForumCommentVO> getApprovedComments(Integer page, Integer size);
    Page<ForumCommentVO> getRejectedComments(Integer page, Integer size);
    Page<ForumCommentVO> getAllComments(Integer page, Integer size);
    void auditComment(Long id, AuditCommentDTO dto, Long reviewerId, String reviewerName);
}
