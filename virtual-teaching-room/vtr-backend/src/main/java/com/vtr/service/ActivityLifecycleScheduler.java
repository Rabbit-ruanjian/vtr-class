package com.vtr.service;

import com.vtr.repository.TeachingActivityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityLifecycleScheduler {

    private final TeachingActivityRepository activityRepository;

    @Scheduled(initialDelay = 10000, fixedDelay = 60000)
    @Transactional
    public void refreshActivityStatuses() {
        int ongoing = activityRepository.markStartedActivitiesOngoing();
        int completed = activityRepository.markFinishedActivitiesCompleted();
        if (ongoing > 0 || completed > 0) {
            log.info("Updated activity lifecycle statuses: ongoing={}, completed={}", ongoing, completed);
        }
    }
}
