package com.vtr.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;

@Configuration
public class KimiAiConfig {

    private static final Logger log = LoggerFactory.getLogger(KimiAiConfig.class);

    @Bean
    public RestTemplate kimiRestTemplate(
            RestTemplateBuilder builder,
            @Value("${kimi.connect-timeout-ms:10000}") long connectTimeoutMs,
            @Value("${kimi.read-timeout-ms:180000}") long readTimeoutMs,
            @Value("${kimi.proxy.host:}") String proxyHost,
            @Value("${kimi.proxy.port:0}") int proxyPort
    ) {
        int safeConnectTimeoutMs = toTimeout(connectTimeoutMs, 10000);
        int safeReadTimeoutMs = toTimeout(readTimeoutMs, 180000);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(safeConnectTimeoutMs);
        requestFactory.setReadTimeout(safeReadTimeoutMs);

        if (StringUtils.hasText(proxyHost)) {
            if (proxyPort < 1 || proxyPort > 65535) {
                throw new IllegalStateException("KIMI_PROXY_PORT 必须是 1 到 65535 之间的端口");
            }
            requestFactory.setProxy(new Proxy(
                    Proxy.Type.HTTP,
                    new InetSocketAddress(proxyHost.trim(), proxyPort)
            ));
            log.info("Kimi 网络代理已启用：host={}, port={}", proxyHost.trim(), proxyPort);
        } else if (proxyPort > 0) {
            log.warn("检测到 KIMI_PROXY_PORT，但未设置 KIMI_PROXY_HOST，已忽略代理配置");
        } else {
            log.info("Kimi 网络代理未启用，将直接连接 Kimi 服务");
        }

        return builder
                .requestFactory(() -> requestFactory)
                .setConnectTimeout(Duration.ofMillis(safeConnectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(safeReadTimeoutMs))
                .build();
    }

    private int toTimeout(long value, int fallback) {
        if (value <= 0) return fallback;
        return (int) Math.min(Integer.MAX_VALUE, value);
    }
}
