package com.vtr.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vtr.common.exception.BusinessException;
import com.vtr.vo.WebSearchCapabilityVO;
import com.vtr.vo.WebSearchResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Kimi 内置 $web_search 工具的能力探针和显式搜索适配器。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KimiBuiltInWebSearchProvider implements WebSearchProvider {

    private static final String DEFAULT_BASE_URL = "https://api.moonshot.cn/v1";
    private static final String PROVIDER_NAME = "kimi-builtin";
    private static final String WEB_SEARCH_TOOL_NAME = "$web_search";
    private static final int MAX_SEARCH_ROUNDS = 3;
    private static final Pattern MARKDOWN_LINK_PATTERN =
            Pattern.compile("\\[([^\\]]{1,200})\\]\\((https?://[^\\s)]+)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern URL_PATTERN =
            Pattern.compile("https?://[^\\s<>()\\\"']+", Pattern.CASE_INSENSITIVE);

    private final RestTemplate kimiRestTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kimi.api-key:${MOONSHOT_API_KEY:}}")
    private String apiKey;

    @Value("${kimi.base-url:https://api.moonshot.cn/v1}")
    private String baseUrl;

    @Value("${kimi.model:kimi-k2.6}")
    private String model;

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public WebSearchCapabilityVO detectCapability() {
        if (!StringUtils.hasText(apiKey)) {
            return result(false, "KIMI_API_KEY 尚未配置");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        String effectiveModel = effectiveModel();
        payload.put("model", effectiveModel);
        payload.put("messages", List.of(Map.of(
                "role", "user",
                "content", "请检测当前模型是否支持联网搜索工具。若支持，请调用一次联网搜索并只返回：检测成功。"
        )));
        payload.put("tools", List.of(Map.of(
                "type", "builtin_function",
                "function", Map.of("name", "$web_search")
        )));
        payload.put("stream", false);
        if ("kimi-k2.6".equalsIgnoreCase(effectiveModel)) {
            payload.put("thinking", Map.of("type", "disabled"));
            payload.put("max_completion_tokens", 512);
        } else {
            payload.put("max_tokens", 512);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        try {
            ResponseEntity<String> response = kimiRestTemplate.exchange(
                    normalizeBaseUrl() + "/chat/completions",
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    String.class
            );
            JsonNode root = objectMapper.readTree(response.getBody() == null ? "" : response.getBody());
            JsonNode message = root.path("choices").path(0).path("message");
            boolean toolCalled = message.path("tool_calls").isArray()
                    && message.path("tool_calls").size() > 0;
            if (toolCalled) {
                return result(true, "当前 Kimi 模型可以接受联网搜索工具调用");
            }
            if (StringUtils.hasText(message.path("content").asText(null))) {
                return result(false, "模型完成了普通回答，但未发起 $web_search 工具调用");
            }
            return result(false, "Kimi 返回内容中未发现可用的联网搜索工具调用");
        } catch (HttpStatusCodeException exception) {
            log.info("Kimi 内置联网搜索能力检测失败，status={}", exception.getStatusCode().value());
            return result(false, extractRemoteReason(exception.getResponseBodyAsString()));
        } catch (RestClientException exception) {
            log.warn("Kimi 内置联网搜索能力检测网络异常", exception);
            return result(false, "无法连接 Kimi 服务，请检查网络后重试");
        } catch (Exception exception) {
            log.warn("解析 Kimi 联网搜索能力检测结果失败", exception);
            return result(false, "Kimi 返回内容无法识别");
        }
    }

    @Override
    public List<WebSearchResultVO> search(String query) {
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException(503, "KIMI_API_KEY 尚未配置");
        }
        if (!StringUtils.hasText(query)) {
            throw new BusinessException(400, "搜索内容不能为空");
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(message("system", "你是联网资料检索助手。请只使用 $web_search 获取外部资料，"
                + "最后输出简洁的检索结果，并尽量使用 Markdown 链接列出实际来源。不要编造网址。"));
        messages.add(message("user", "请搜索：" + trim(query, 1000)
                + "。请给出与该问题最相关的网页标题、简要摘要和可访问链接。"));

        for (int round = 0; round <= MAX_SEARCH_ROUNDS; round++) {
            JsonNode root = requestSearchCompletion(messages);
            JsonNode choice = root.path("choices").path(0);
            JsonNode assistant = choice.path("message");
            JsonNode toolCalls = assistant.path("tool_calls");
            if ("tool_calls".equals(choice.path("finish_reason").asText())
                    && toolCalls.isArray() && !toolCalls.isEmpty()) {
                if (round == MAX_SEARCH_ROUNDS) {
                    throw new BusinessException(502, "联网检索步骤过多，请换一种问法后重试");
                }
                appendAssistantMessage(messages, assistant);
                for (JsonNode toolCall : toolCalls) {
                    if (!WEB_SEARCH_TOOL_NAME.equals(toolCall.path("function").path("name").asText())) {
                        throw new BusinessException(502, "Kimi 返回了无法识别的联网工具调用");
                    }
                    Map<String, Object> toolMessage = new LinkedHashMap<>();
                    toolMessage.put("role", "tool");
                    toolMessage.put("tool_call_id", toolCall.path("id").asText(""));
                    toolMessage.put("name", WEB_SEARCH_TOOL_NAME);
                    toolMessage.put("content", normalizeToolArguments(
                            toolCall.path("function").path("arguments").asText("{}")));
                    messages.add(toolMessage);
                }
                continue;
            }

            String content = assistant.path("content").asText("").trim();
            if (!StringUtils.hasText(content)) {
                throw new BusinessException(502, "Kimi 没有返回有效搜索结果");
            }
            return parseSearchResults(content);
        }
        throw new BusinessException(502, "Kimi 没有返回有效搜索结果");
    }

    private JsonNode requestSearchCompletion(List<Map<String, Object>> messages) {
        Map<String, Object> payload = new LinkedHashMap<>();
        String effectiveModel = effectiveModel();
        payload.put("model", effectiveModel);
        payload.put("messages", messages);
        payload.put("stream", false);
        if ("kimi-k2.6".equalsIgnoreCase(effectiveModel)) {
            payload.put("thinking", Map.of("type", "disabled"));
            payload.put("max_completion_tokens", 2048);
        } else {
            payload.put("max_tokens", 2048);
        }
        payload.put("tools", List.of(Map.of(
                "type", "builtin_function",
                "function", Map.of("name", WEB_SEARCH_TOOL_NAME)
        )));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        try {
            ResponseEntity<String> response = kimiRestTemplate.exchange(
                    normalizeBaseUrl() + "/chat/completions",
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    String.class
            );
            return objectMapper.readTree(response.getBody() == null ? "" : response.getBody());
        } catch (HttpStatusCodeException exception) {
            String reason = extractRemoteReason(exception.getResponseBodyAsString());
            if (isUnsupportedSearchError(exception, reason)) {
                throw new BusinessException(503, "当前 Kimi 模型或账号不支持内置联网搜索");
            }
            throw new BusinessException(502, reason);
        } catch (RestClientException exception) {
            log.warn("Kimi 显式联网搜索网络异常", exception);
            throw new BusinessException(502, "无法连接 Kimi 服务，请检查网络后重试");
        } catch (Exception exception) {
            log.warn("解析 Kimi 显式联网搜索结果失败", exception);
            throw new BusinessException(502, "Kimi 返回内容无法识别");
        }
    }

    private void appendAssistantMessage(List<Map<String, Object>> messages, JsonNode assistant) {
        try {
            Map<String, Object> item = objectMapper.convertValue(
                    assistant, new TypeReference<Map<String, Object>>() {});
            item.put("role", "assistant");
            messages.add(item);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(502, "Kimi 联网工具调用结果无法继续处理");
        }
    }

    private List<WebSearchResultVO> parseSearchResults(String content) {
        List<WebSearchResultVO> results = new ArrayList<>();
        Matcher markdownMatcher = MARKDOWN_LINK_PATTERN.matcher(content);
        while (markdownMatcher.find()) {
            String url = trimUrl(markdownMatcher.group(2));
            results.add(new WebSearchResultVO(
                    markdownMatcher.group(1).trim(), url, trim(content, 1200), PROVIDER_NAME));
        }
        if (!results.isEmpty()) {
            return deduplicate(results);
        }

        Matcher urlMatcher = URL_PATTERN.matcher(content);
        while (urlMatcher.find()) {
            String url = trimUrl(urlMatcher.group());
            results.add(new WebSearchResultVO("联网搜索结果", url, trim(content, 1200), PROVIDER_NAME));
        }
        if (!results.isEmpty()) {
            return deduplicate(results);
        }

        // Kimi 可能返回带有内部引用而没有公开 URL 的摘要，仍保留可读结果，不伪造链接。
        return List.of(new WebSearchResultVO("联网搜索摘要", null, trim(content, 1200), PROVIDER_NAME));
    }

    private List<WebSearchResultVO> deduplicate(List<WebSearchResultVO> results) {
        Map<String, WebSearchResultVO> unique = new LinkedHashMap<>();
        for (WebSearchResultVO result : results) {
            unique.putIfAbsent((result.getUrl() == null ? result.getTitle() : result.getUrl()), result);
        }
        return new ArrayList<>(unique.values());
    }

    private Map<String, Object> message(String role, String content) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("role", role);
        item.put("content", content);
        return item;
    }

    private String normalizeToolArguments(String arguments) {
        try {
            return objectMapper.writeValueAsString(objectMapper.readTree(arguments));
        } catch (Exception exception) {
            return arguments;
        }
    }

    private boolean isUnsupportedSearchError(HttpStatusCodeException exception, String reason) {
        int status = exception.getStatusCode().value();
        if (status != 400 && status != 404 && status != 422) return false;
        String normalized = reason == null ? "" : reason.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("$web_search") || normalized.contains("builtin_function")
                || normalized.contains("tool") || normalized.contains("function")
                || normalized.contains("工具") || normalized.contains("不支持");
    }

    private String trimUrl(String value) {
        return value == null ? "" : value.replaceAll("[。，；：、.!！?？)）]+$", "");
    }

    private String trim(String value, int maxLength) {
        String text = value == null ? "" : value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private WebSearchCapabilityVO result(boolean supported, String message) {
        return new WebSearchCapabilityVO(supported, PROVIDER_NAME, effectiveModel(), message);
    }

    private String effectiveModel() {
        if (!StringUtils.hasText(model)
                || "moonshot-v1-128k-vision-preview".equalsIgnoreCase(model.trim())) {
            return "kimi-k2.6";
        }
        return model.trim();
    }

    private String extractRemoteReason(String body) {
        try {
            JsonNode root = objectMapper.readTree(body == null ? "" : body);
            String remoteMessage = root.path("error").path("message").asText(null);
            if (StringUtils.hasText(remoteMessage)) {
                String reason = remoteMessage.trim();
                return reason.length() <= 300 ? reason : reason.substring(0, 300);
            }
        } catch (Exception ignored) {
            // 使用统一提示，避免把远端原始响应直接返回给前端。
        }
        return "当前模型不支持 Kimi 内置联网搜索，或当前账号没有相应权限";
    }

    private String normalizeBaseUrl() {
        String configured = StringUtils.hasText(baseUrl) ? baseUrl.trim() : DEFAULT_BASE_URL;
        return configured.endsWith("/") ? configured.substring(0, configured.length() - 1) : configured;
    }
}
