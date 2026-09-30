package com.vtr.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 可插拔的 Embedding 服务适配器。
 * 默认关闭；开启后按 OpenAI 兼容协议调用 /embeddings，失败时回退本地 RAG。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiEmbeddingService {
    private final RestTemplate kimiRestTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ai.embedding.enabled:false}")
    private boolean enabled;

    @Value("${ai.embedding.base-url:}")
    private String baseUrl;

    @Value("${ai.embedding.api-key:}")
    private String apiKey;

    @Value("${ai.embedding.model:text-embedding-3-small}")
    private String model;

    @PostConstruct
    public void logConfiguration() {
        log.info("AI Embedding 配置：enabled={}, endpointConfigured={}, model={}",
                enabled, StringUtils.hasText(baseUrl) && StringUtils.hasText(apiKey), model);
    }

    public boolean isAvailable() {
        return enabled && StringUtils.hasText(baseUrl)
                && StringUtils.hasText(apiKey) && StringUtils.hasText(model);
    }

    public List<Double> embed(String input) {
        List<List<Double>> result = embedBatch(List.of(input == null ? "" : input));
        return result.isEmpty() ? List.of() : result.get(0);
    }

    public List<List<Double>> embedBatch(List<String> inputs) {
        if (!isAvailable() || inputs == null || inputs.isEmpty()) return List.of();
        List<String> safeInputs = inputs.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(StringUtils::hasText)
                .toList();
        if (safeInputs.isEmpty()) return List.of();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey.trim());
            Map<String, Object> payload = new HashMap<>();
            payload.put("model", model.trim());
            payload.put("input", safeInputs);
            ResponseEntity<String> response = kimiRestTemplate.exchange(
                    normalizeBaseUrl() + "/embeddings", HttpMethod.POST,
                    new HttpEntity<>(payload, headers), String.class);
            JsonNode data = objectMapper.readTree(response.getBody()).path("data");
            if (!data.isArray() || data.isEmpty()) return List.of();

            List<List<Double>> embeddings = new ArrayList<>();
            for (JsonNode item : data) {
                JsonNode vector = item.path("embedding");
                if (!vector.isArray() || vector.isEmpty()) return List.of();
                List<Double> values = new ArrayList<>();
                for (JsonNode number : vector) values.add(number.asDouble());
                embeddings.add(values);
            }
            return embeddings.size() == safeInputs.size() ? embeddings : List.of();
        } catch (Exception exception) {
            log.warn("Embedding 服务暂不可用，课程 RAG 将回退到本地检索：{}", safeMessage(exception));
            return List.of();
        }
    }

    public String toJson(List<Double> vector) {
        if (vector == null || vector.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (Exception exception) {
            return null;
        }
    }

    public List<Double> fromJson(String value) {
        if (!StringUtils.hasText(value)) return List.of();
        try {
            JsonNode node = objectMapper.readTree(value);
            if (!node.isArray()) return List.of();
            List<Double> vector = new ArrayList<>();
            for (JsonNode item : node) vector.add(item.asDouble());
            return vector;
        } catch (Exception exception) {
            return List.of();
        }
    }

    public double cosineSimilarity(List<Double> left, List<Double> right) {
        if (left == null || right == null || left.isEmpty() || left.size() != right.size()) return 0D;
        double dot = 0D;
        double leftNorm = 0D;
        double rightNorm = 0D;
        for (int index = 0; index < left.size(); index++) {
            double a = left.get(index);
            double b = right.get(index);
            dot += a * b;
            leftNorm += a * a;
            rightNorm += b * b;
        }
        if (leftNorm <= 0D || rightNorm <= 0D) return 0D;
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private String normalizeBaseUrl() {
        String value = baseUrl == null ? "" : baseUrl.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private String safeMessage(Exception exception) {
        String message = exception == null ? "unknown" : exception.getMessage();
        if (!StringUtils.hasText(message)) return "unknown";
        return message.length() > 180 ? message.substring(0, 180) : message;
    }
}
