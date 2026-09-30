package com.vtr.repository;

import com.vtr.entity.NoticeAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface NoticeAttachmentRepository extends JpaRepository<NoticeAttachment, Long> {

    List<NoticeAttachment> findByNoticeIdOrderByCreatedAtAsc(Long noticeId);

    @Modifying
    @Transactional
    void deleteByNoticeId(Long noticeId);

    @Modifying
    @Transactional
    @Query("DELETE FROM NoticeAttachment a WHERE a.id = :id AND a.noticeId = :noticeId")
    int deleteByIdAndNoticeId(@Param("id") Long id, @Param("noticeId") Long noticeId);
}
