import java.nio.file.*;
import java.util.*;

public class AiServiceSmoke {
    public static void main(String[] args) throws Exception {
        Map<String, String> config = new HashMap<>();
        for (String line : Files.readAllLines(Path.of(args[0]))) {
            int split = line.indexOf('=');
            if (!line.startsWith("#") && split > 0) config.put(line.substring(0, split).trim(), line.substring(split + 1).trim());
        }
        Class<?> builderType = Class.forName("org.springframework.boot.web.client.RestTemplateBuilder");
        Class<?> customizerType = Class.forName("org.springframework.boot.web.client.RestTemplateCustomizer");
        Object emptyCustomizers = java.lang.reflect.Array.newInstance(customizerType, 0);
        Object builder = builderType.getConstructor(emptyCustomizers.getClass()).newInstance(emptyCustomizers);
        Class<?> configType = Class.forName("com.vtr.config.KimiAiConfig");
        Object rest = configType.getMethod("kimiRestTemplate", builderType, long.class, long.class, String.class, int.class)
                .invoke(configType.getConstructor().newInstance(), builder, 10000L, 60000L,
                        config.getOrDefault("KIMI_PROXY_HOST", ""), Integer.parseInt(config.getOrDefault("KIMI_PROXY_PORT", "0")));
        Object mapper = Class.forName("com.fasterxml.jackson.databind.ObjectMapper").getConstructor().newInstance();
        Class<?> serviceType = Class.forName("com.vtr.service.KimiAiService");
        Object service = serviceType.getConstructors()[0].newInstance(rest, mapper, null);
        for (String[] field : new String[][] { {"apiKey", "KIMI_API_KEY"}, {"baseUrl", "KIMI_BASE_URL"}, {"model", "KIMI_MODEL"} }) {
            var property = serviceType.getDeclaredField(field[0]);
            property.setAccessible(true);
            property.set(service, config.get(field[1]));
        }
        var fast = serviceType.getDeclaredField("fastMode");
        fast.setAccessible(true);
        fast.set(service, true);
        var fallback = serviceType.getDeclaredField("fallbackModels");
        fallback.setAccessible(true);
        fallback.set(service, "kimi-k2.6");
        Class<?> requestType = Class.forName("com.vtr.dto.AiChatRequest");
        Object request = requestType.getConstructor().newInstance();
        requestType.getMethod("setMessage", String.class).invoke(request, "\u4f60\u597d\uff0c\u8bf7\u7528\u4e00\u53e5\u8bdd\u4ecb\u7ecd\u4f60\u80fd\u600e\u4e48\u5e2e\u6211\u5b66\u4e60\u3002");
        try {
            Object result = serviceType.getMethod("chat", requestType).invoke(service, request);
            String reply = (String) result.getClass().getMethod("getReply").invoke(result);
            if (reply == null || reply.isBlank()) throw new IllegalStateException("Empty reply");
            System.out.println("BACKEND_SERVICE_REPLY=" + reply);
            System.out.println("BACKEND_SERVICE_MODEL=" + result.getClass().getMethod("getModel").invoke(result));
            System.out.println("BACKEND_SERVICE_SMOKE_OK");
        } catch (java.lang.reflect.InvocationTargetException error) {
            System.err.println("BACKEND_SERVICE_FAILED=" + error.getCause().getClass().getSimpleName());
            System.exit(1);
        }
    }
}
