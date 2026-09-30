package com.vtr.service;

import com.vtr.common.PageResult;
import com.vtr.dto.ContentAuditDTO;
import com.vtr.dto.PageQueryDTO;
import com.vtr.entity.ContentAudit;

import java.util.Map;

public interface ContentAuditService {

    PageResult<ContentAudit> getPendingList(PageQueryDTO query);

    PageResult<ContentAudit> getPendingList(PageQueryDTO query, java.util.Set<Long> schoolIds);

    PageResult<ContentAudit> getList(PageQueryDTO query, String status, java.util.Set<Long> schoolIds);

    void review(Long id, ContentAuditDTO dto, Long reviewerId,String reviewName);

    Map<String, Object> getDashboardStatistics();

    void createAudit(ContentAudit.ContentType type, Long contentId, String title,
                     String preview, Long authorId, String authorName);

    ContentAudit createOrEscalate(ContentAudit.ContentType type, Long contentId, String title,
                                  String preview, Long authorId, String authorName, Long schoolId,
                                  String riskLevel, String details);

    void resolveAutomatedRisk(ContentAudit.ContentType type, Long contentId, Long actorId,
                              String actorName, String remark);

    void report(String targetType, Long targetId, String reason, Long reporterId, String reporterName);

    void appeal(String targetType, Long targetId, String reason, Long appellantId, String appellantName);

    ContentAudit getByContentTypeAndContentId(ContentAudit.ContentType type, Long contentId);

    ContentAudit getById(Long id);

}
