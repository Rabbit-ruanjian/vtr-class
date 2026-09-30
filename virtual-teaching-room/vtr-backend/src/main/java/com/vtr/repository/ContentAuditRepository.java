package com.vtr.repository;

import com.vtr.entity.ContentAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ContentAuditRepository extends JpaRepository<ContentAudit, Long> {

    Optional<ContentAudit> findByContentTypeAndContentId(ContentAudit.ContentType contentType,
                                                         Long contentId);

    Page<ContentAudit> findByStatusOrderByCreatedAtAsc(ContentAudit.AuditStatus status, Pageable pageable);

    Page<ContentAudit> findByStatusAndSchoolIdInOrderByCreatedAtAsc(ContentAudit.AuditStatus status,
                                                                      java.util.Collection<Long> schoolIds,
                                                                      Pageable pageable);

    @Query("SELECT ca FROM ContentAudit ca WHERE " +
            "(:status IS NULL OR ca.status = :status) AND " +
            "(:schoolId IS NULL OR ca.schoolId = :schoolId)")
    Page<ContentAudit> findByStatusAndSchoolId(@Param("status") ContentAudit.AuditStatus status,
                                               @Param("schoolId") Long schoolId,
                                               Pageable pageable);

    @Query("SELECT ca FROM ContentAudit ca WHERE ca.status IN :statuses AND ca.schoolId = :schoolId")
    Page<ContentAudit> findByStatusInAndSchoolId(@Param("statuses") java.util.Collection<ContentAudit.AuditStatus> statuses,
                                                 @Param("schoolId") Long schoolId,
                                                 Pageable pageable);

    @Query("SELECT ca FROM ContentAudit ca WHERE ca.status IN :statuses")
    Page<ContentAudit> findByStatusIn(@Param("statuses") java.util.Collection<ContentAudit.AuditStatus> statuses,
                                      Pageable pageable);

    @Query("SELECT ca FROM ContentAudit ca WHERE ca.status IN :statuses AND ca.schoolId IN :schoolIds")
    Page<ContentAudit> findByStatusInAndSchoolIdIn(@Param("statuses") java.util.Collection<ContentAudit.AuditStatus> statuses,
                                                  @Param("schoolIds") java.util.Collection<Long> schoolIds,
                                                  Pageable pageable);

    @Query("SELECT ca FROM ContentAudit ca WHERE ca.status IN :statuses AND ca.riskLevel IN :riskLevels")
    Page<ContentAudit> findModerationByStatusesAndRiskLevels(
            @Param("statuses") java.util.Collection<ContentAudit.AuditStatus> statuses,
            @Param("riskLevels") java.util.Collection<String> riskLevels,
            Pageable pageable);

    @Query("SELECT ca FROM ContentAudit ca WHERE ca.status IN :statuses AND ca.riskLevel IN :riskLevels " +
            "AND ca.schoolId IN :schoolIds")
    Page<ContentAudit> findModerationByStatusesAndRiskLevelsAndSchoolIdIn(
            @Param("statuses") java.util.Collection<ContentAudit.AuditStatus> statuses,
            @Param("riskLevels") java.util.Collection<String> riskLevels,
            @Param("schoolIds") java.util.Collection<Long> schoolIds,
            Pageable pageable);

    @Query("SELECT ca FROM ContentAudit ca WHERE " +
            "(:status IS NULL OR ca.status = :status) AND " +
            "(:contentType IS NULL OR ca.contentType = :contentType) AND " +
            "(:riskLevel IS NULL OR ca.riskLevel = :riskLevel)")
    Page<ContentAudit> findByConditions(@Param("status") ContentAudit.AuditStatus status,
                                        @Param("contentType") ContentAudit.ContentType contentType,
                                        @Param("riskLevel") String riskLevel,
                                        Pageable pageable);

    long countByStatus(ContentAudit.AuditStatus status);

    long countByStatusAndRiskLevelIn(ContentAudit.AuditStatus status, java.util.Collection<String> riskLevels);

    @Modifying
    @Query("UPDATE ContentAudit ca SET ca.submitCount = ca.submitCount + 1 WHERE ca.id = :id")
    void incrementSubmitCount(@Param("id") Long id);

    @Query("SELECT ca.contentType, COUNT(ca) FROM ContentAudit ca GROUP BY ca.contentType")
    java.util.List<Object[]> countByContentType();
}
