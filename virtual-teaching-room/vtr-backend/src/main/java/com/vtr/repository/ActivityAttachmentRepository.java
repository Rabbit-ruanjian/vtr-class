package com.vtr.repository;

import com.vtr.entity.ActivityAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ActivityAttachmentRepository extends JpaRepository<ActivityAttachment, Long> {

    // 获取活动的所有附件
    List<ActivityAttachment> findByActivityId(Long activityId);

    // 删除活动的所有附件
    @Modifying
    @Transactional
    void deleteByActivityId(Long activityId);
}