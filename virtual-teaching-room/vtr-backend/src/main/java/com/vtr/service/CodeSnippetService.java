package com.vtr.service;

import com.vtr.common.PageResult;
import com.vtr.dto.SnippetCreateDTO;
import com.vtr.dto.SnippetReviewDTO;
import com.vtr.dto.SnippetSearchDTO;
import com.vtr.dto.SnippetUpdateDTO;
import com.vtr.entity.CodeSnippet;
import com.vtr.vo.CodeSnippetVO;
import com.vtr.vo.SnippetStatisticsVO;
import org.springframework.data.domain.PageRequest;

import java.util.List;

public interface CodeSnippetService {

    // CRUD
    Long create(SnippetCreateDTO dto, Long authorId);
    void update(Long id, SnippetUpdateDTO dto, Long userId);
    void delete(Long id, Long userId);
    CodeSnippetVO getById(Long id);

    // 查询
    PageResult<CodeSnippetVO> search(SnippetSearchDTO query);
    PageResult<CodeSnippetVO> getUserSnippets(Long userId, PageRequest page);
    PageResult<CodeSnippetVO> getPendingSnippets(Integer page, Integer size);

    // ========== 新增：按状态获取代码片段 ==========
    PageResult<CodeSnippetVO> getApprovedList(PageRequest pageRequest);
    PageResult<CodeSnippetVO> getRejectedList(PageRequest pageRequest);

    // 审核
    void review(Long id, SnippetReviewDTO dto, Long reviewerId);
    void reviewSnippet(Long id, String action, String remark, Long reviewerId, String reviewerName);

    // 互动
    void like(Long id, Long userId);
    void unlike(Long id, Long userId);
    void incrementViewCount(Long id);
    void incrementDownloadCount(Long id);

    // 统计
    List<String> getHotTags(Integer limit);
    SnippetStatisticsVO getStatistics();

    // 工具
    String highlightCode(String code, String language);

    PageResult<CodeSnippetVO> getPendingList(PageRequest of);
    List<CodeSnippetVO> getAllApproved();
    List<CodeSnippet> getAllRaw();
}