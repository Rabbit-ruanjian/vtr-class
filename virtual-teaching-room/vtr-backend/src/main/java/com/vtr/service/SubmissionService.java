package com.vtr.service;

import com.vtr.common.PageResult;
import com.vtr.dto.ManualReviewDTO;
import com.vtr.dto.SubmissionDTO;
import com.vtr.dto.SubmissionQueryDTO;
import com.vtr.vo.SubmissionProgressVO;
import com.vtr.vo.SubmissionVO;

import java.util.List;

public interface SubmissionService {

    // 提交
    Long submit(SubmissionDTO dto, Long studentId);

    // 查询
    SubmissionVO getById(Long id, Long userId);

    PageResult<SubmissionVO> querySubmissions(SubmissionQueryDTO query, Long userId);

    List<SubmissionVO> getMySubmissions(Long assignmentId, Long studentId);

    SubmissionVO getMyLastSubmission(Long assignmentId, Long studentId);

    PageResult<SubmissionVO> query(SubmissionQueryDTO queryDTO, Long userId);

    // 评审
    void manualReview(Long id, ManualReviewDTO dto, Long reviewerId);

    void batchReview(List<Long> ids, Integer score, String comment, Long reviewerId);

    // 统计
    void calculatePlagiarism(Long assignmentId);

    // 执行
    void executeAutoTest(Long submissionId);

    // 进度
    SubmissionProgressVO getProgress(Long submissionId);

    // 工具
    void markAsFinal(Long submissionId, Long studentId);

    SubmissionVO getFinalSubmission(Long assignmentId, Long studentId);

    // ========== 新增方法 ==========

    /**
     * 获取作业的所有提交记录（教师/管理员使用）
     */
    PageResult<SubmissionVO> getAllSubmissionsByAssignment(Long assignmentId, SubmissionQueryDTO queryDTO, Long userId);
}