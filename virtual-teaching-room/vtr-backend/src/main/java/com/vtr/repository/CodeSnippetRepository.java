package com.vtr.repository;

import com.vtr.entity.CodeSnippet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CodeSnippetRepository extends JpaRepository<CodeSnippet, Long> {

    // ========== 查询已审核通过的公开片段 ==========
    @Query("SELECT s FROM CodeSnippet s WHERE s.status = :status AND s.isDeleted = false AND s.isPublic = true")
    Page<CodeSnippet> findApprovedSnippets(@Param("status") CodeSnippet.SnippetStatus status, Pageable pageable);

    @Query("SELECT s FROM CodeSnippet s WHERE s.status = :status AND s.isDeleted = false AND s.isPublic = true")
    List<CodeSnippet> findAllApproved(@Param("status") CodeSnippet.SnippetStatus status);

    // ========== 按语言筛选 ==========
    @Query("SELECT s FROM CodeSnippet s WHERE s.status = :status AND s.isDeleted = false AND s.isPublic = true AND s.language = :language")
    Page<CodeSnippet> findApprovedSnippetsByLanguage(@Param("status") CodeSnippet.SnippetStatus status,
                                                     @Param("language") String language,
                                                     Pageable pageable);

    // ========== 关键词搜索 ==========
    @Query("SELECT s FROM CodeSnippet s WHERE s.status = :status AND s.isDeleted = false AND s.isPublic = true AND " +
            "(s.title LIKE %:keyword% OR s.description LIKE %:keyword% OR s.code LIKE %:keyword%)")
    Page<CodeSnippet> searchApprovedSnippetsByKeyword(@Param("status") CodeSnippet.SnippetStatus status,
                                                      @Param("keyword") String keyword,
                                                      Pageable pageable);

    // ========== 关键词 + 语言组合搜索 ==========
    @Query("SELECT s FROM CodeSnippet s WHERE s.status = :status AND s.isDeleted = false AND s.isPublic = true AND " +
            "(:language IS NULL OR :language = '' OR s.language = :language) AND " +
            "(s.title LIKE %:keyword% OR s.description LIKE %:keyword% OR s.code LIKE %:keyword%)")
    Page<CodeSnippet> searchApprovedSnippets(@Param("status") CodeSnippet.SnippetStatus status,
                                             @Param("keyword") String keyword,
                                             @Param("language") String language,
                                             Pageable pageable);

    // ========== 通用搜索（带标签） ==========
    @Query("SELECT s FROM CodeSnippet s WHERE s.isDeleted = false AND s.status = 'APPROVED' AND " +
            "(:keyword IS NULL OR :keyword = '' OR s.title LIKE %:keyword% OR s.description LIKE %:keyword% OR s.code LIKE %:keyword%) AND " +
            "(:language IS NULL OR :language = '' OR s.language = :language) AND " +
            "(:tag IS NULL OR :tag = '' OR :tag MEMBER OF s.tags)")
    Page<CodeSnippet> searchSnippets(@Param("keyword") String keyword,
                                     @Param("language") String language,
                                     @Param("tag") String tag,
                                     Pageable pageable);

    // ========== 热门标签 ==========
    @Query("SELECT DISTINCT t FROM CodeSnippet s JOIN s.tags t WHERE s.isDeleted = false AND s.status = 'APPROVED' " +
            "GROUP BY t ORDER BY COUNT(t) DESC")
    List<String> findHotTags(Pageable pageable);

    // ========== 按作者查询 ==========
    Page<CodeSnippet> findByAuthorIdAndIsDeletedFalse(Long authorId, Pageable pageable);

    // ========== 按状态查询 ==========
    Page<CodeSnippet> findByStatusAndIsDeletedFalse(CodeSnippet.SnippetStatus status, Pageable pageable);

    // ========== 查询待审核的片段 ==========
    @Query("SELECT s FROM CodeSnippet s WHERE s.isDeleted = false AND s.status = 'PENDING'")
    Page<CodeSnippet> findPendingSnippets(Pageable pageable);

    // ========== 计数操作 ==========
    @Modifying
    @Query("UPDATE CodeSnippet s SET s.viewCount = s.viewCount + 1 WHERE s.id = :id")
    void incrementViewCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE CodeSnippet s SET s.likeCount = s.likeCount + 1 WHERE s.id = :id")
    void incrementLikeCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE CodeSnippet s SET s.likeCount = s.likeCount - 1 WHERE s.id = :id AND s.likeCount > 0")
    void decrementLikeCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE CodeSnippet s SET s.downloadCount = s.downloadCount + 1 WHERE s.id = :id")
    void incrementDownloadCount(@Param("id") Long id);

    // ========== 热门片段 ==========
    @Query("SELECT s FROM CodeSnippet s WHERE s.isDeleted = false AND s.status = 'APPROVED' AND s.isPublic = true " +
            "ORDER BY s.likeCount DESC, s.viewCount DESC")
    List<CodeSnippet> findPopularSnippets(Pageable pageable);

    // ========== 语言分布统计 ==========
    @Query("SELECT s.language, COUNT(s) FROM CodeSnippet s WHERE s.isDeleted = false AND s.status = 'APPROVED' GROUP BY s.language")
    List<Object[]> countByLanguage();

    // ========== 存在性检查 ==========
    boolean existsByIdAndAuthorId(Long id, Long authorId);

    // ========== 软删除 ==========
    @Modifying
    @Query("UPDATE CodeSnippet s SET s.isDeleted = true WHERE s.id = :id")
    void softDelete(@Param("id") Long id);
}