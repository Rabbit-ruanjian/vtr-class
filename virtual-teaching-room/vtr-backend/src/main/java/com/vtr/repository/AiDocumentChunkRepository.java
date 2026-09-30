package com.vtr.repository;

import com.vtr.entity.AiDocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AiDocumentChunkRepository extends JpaRepository<AiDocumentChunk, Long> {

    @Query("SELECT c FROM AiDocumentChunk c "
            + "WHERE c.document.id = :documentId "
            + "AND c.document.isDeleted = false "
            + "ORDER BY c.chunkIndex ASC")
    List<AiDocumentChunk> findByDocumentId(@Param("documentId") Long documentId);

    @Query("SELECT c FROM AiDocumentChunk c "
            + "WHERE c.document.id = :documentId "
            + "AND c.document.ownerId = :ownerId "
            + "AND c.document.isDeleted = false "
            + "ORDER BY c.chunkIndex ASC")
    List<AiDocumentChunk> findByDocumentIdAndOwnerId(@Param("documentId") Long documentId,
                                                      @Param("ownerId") Long ownerId);

    @Query("SELECT c FROM AiDocumentChunk c "
            + "WHERE c.document.courseId = :courseId "
            + "AND c.document.isDeleted = false "
            + "AND c.document.status = 'READY' "
            + "AND (c.document.indexMode IS NULL OR c.document.indexMode <> 'FAILED') "
            + "AND (:chapter IS NULL OR c.document.chapter = :chapter) "
            + "ORDER BY c.document.id ASC, c.chunkIndex ASC")
    List<AiDocumentChunk> findByCourseIdAndChapter(@Param("courseId") Long courseId,
                                                    @Param("chapter") String chapter);
}
