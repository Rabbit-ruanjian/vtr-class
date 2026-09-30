package com.vtr.repository;

import com.vtr.entity.AiDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiDocumentRepository extends JpaRepository<AiDocument, Long> {

    List<AiDocument> findByOwnerIdAndIsDeletedFalseOrderByCreatedAtDesc(Long ownerId);

    Optional<AiDocument> findByIdAndOwnerIdAndIsDeletedFalse(Long id, Long ownerId);

    List<AiDocument> findByCourseIdAndIsDeletedFalseAndStatusOrderByCreatedAtDesc(Long courseId, String status);

    List<AiDocument> findByCourseIdAndIsDeletedFalseOrderByCreatedAtDesc(Long courseId);

    Optional<AiDocument> findByIdAndCourseIdAndIsDeletedFalse(Long id, Long courseId);

    Optional<AiDocument> findByIdAndIsDeletedFalse(Long id);

    Optional<AiDocument> findFirstByCoursewareIdAndIsDeletedFalseOrderByIdDesc(Long coursewareId);

    List<AiDocument> findByCoursewareIdAndIsDeletedFalse(Long coursewareId);
}
