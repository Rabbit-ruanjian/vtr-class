package com.vtr.scheduler;

import com.vtr.repository.ForumPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class PinExpiryScheduler {

    private final ForumPostRepository forumPostRepository;

    /**
     * 每小时执行一次，清除过期的置顶
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void clearExpiredPins() {
        try {
            int count = forumPostRepository.clearExpiredPins(LocalDateTime.now());
            if (count > 0) {
                log.info("已清除 {} 个过期的置顶帖子", count);
            }
        } catch (Exception e) {
            log.error("清除过期置顶失败: {}", e.getMessage());
        }
    }
}