package com.vtr.config;

import com.vtr.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 对 AI 入口做用户级请求频率和并发保护，避免单个请求拖垮整个平台。 */
@Component
public class AiRequestRateLimitInterceptor implements HandlerInterceptor {
    private static final String STATE_ATTRIBUTE = AiRequestRateLimitInterceptor.class.getName() + ".STATE";
    private final ConcurrentMap<Long, UserState> states = new ConcurrentHashMap<>();

    @Value("${ai.rate-limit.requests-per-minute:30}")
    private int requestsPerMinute;

    @Value("${ai.rate-limit.max-concurrent:3}")
    private int maxConcurrent;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return true;
        UserState state = states.computeIfAbsent(userId, ignored -> new UserState());
        long now = System.currentTimeMillis();
        synchronized (state) {
            if (now - state.windowStartedAt >= 60_000L) {
                state.windowStartedAt = now;
                state.requests = 0;
            }
            if (state.requests >= Math.max(1, requestsPerMinute)) {
                reject(response, "AI 请求过于频繁，请稍后再试");
                return false;
            }
            if (state.active >= Math.max(1, maxConcurrent)) {
                reject(response, "当前已有多个 AI 请求处理中，请等待已有回答完成");
                return false;
            }
            state.requests++;
            state.active++;
            request.setAttribute(STATE_ATTRIBUTE, state);
            return true;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception exception) {
        Object value = request.getAttribute(STATE_ATTRIBUTE);
        if (!(value instanceof UserState state)) return;
        synchronized (state) {
            state.active = Math.max(0, state.active - 1);
        }
    }

    private void reject(HttpServletResponse response, String message) throws Exception {
        response.setStatus(429);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":429,\"message\":\"" + escapeJson(message) + "\"}");
    }

    private String escapeJson(String value) {
        return StringUtils.hasText(value) ? value.replace("\\", "\\\\").replace("\"", "\\\"") : "";
    }

    private static final class UserState {
        private long windowStartedAt = System.currentTimeMillis();
        private int requests;
        private int active;
    }
}
