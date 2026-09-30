package com.vtr.event;

/**
 * 课件记录或审核状态发生变化后发布的事件。
 * AI 索引监听器只接收课件 ID，避免把 JPA 实体带到事务提交后的异步线程。
 */
public record CoursewareChangedEvent(Long coursewareId, boolean reindexContent) {

    public static CoursewareChangedEvent reindex(Long coursewareId) {
        return new CoursewareChangedEvent(coursewareId, true);
    }

    public static CoursewareChangedEvent syncStatus(Long coursewareId) {
        return new CoursewareChangedEvent(coursewareId, false);
    }
}
