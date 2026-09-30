package com.vtr.service;

import com.vtr.common.PageResult;
import com.vtr.dto.NoticeCreateDTO;
import com.vtr.dto.NoticeUpdateDTO;
import com.vtr.vo.NoticeVO;

import java.util.List;

public interface NoticeService {

    Long create(NoticeCreateDTO dto, Long authorId);

    void update(Long id, NoticeUpdateDTO dto);

    void delete(Long id);

    NoticeVO getById(Long id, Long userId);

    PageResult<NoticeVO> getNotices(String type, String status, String keyword, Integer page, Integer size);

    List<NoticeVO> getActiveNotices(Long userId);

    List<NoticeVO> getTopNotices();

    void publish(Long id);

    void withdraw(Long id);

    void incrementViewCount(Long id);

    void markAsRead(Long noticeId, Long userId);

    List<Long> getUnreadNoticeIds(Long userId);

    PageResult<NoticeVO> query(String keyword, String type, String status, int page, int size);

    // 新增：置顶/取消置顶
    void pin(Long id, Boolean isTop);
}
