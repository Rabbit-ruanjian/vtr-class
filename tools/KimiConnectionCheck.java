import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Checks the same Java HTTP transport as the backend without logging credentials. */
public class KimiConnectionCheck {
    public static void main(String[] args) {
        try {
            Map<String, String> config = new HashMap<>(System.getenv());
            for (String line : Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8)) {
                String value = line.replace("\uFEFF", "").trim();
                int separator = value.indexOf('=');
                if (!value.startsWith("#") && separator > 0) {
                    config.put(value.substring(0, separator).trim(), value.substring(separator + 1).trim());
                }
            }
            String key = config.getOrDefault("KIMI_API_KEY", "");
            if (key.isBlank()) key = config.getOrDefault("MOONSHOT_API_KEY", "");
            if (key.isBlank()) throw new IllegalArgumentException("API key is not configured");
            String base = config.getOrDefault("KIMI_BASE_URL", "https://api.moonshot.cn/v1").replaceAll("/+$", "");
            URI endpoint = URI.create(base);
            if (!"https".equals(endpoint.getScheme()) || endpoint.getUserInfo() != null) {
                throw new IllegalArgumentException("Use an HTTPS API endpoint without embedded credentials");
            }
            boolean chat = java.util.Arrays.asList(args).contains("--chat");
            boolean direct = java.util.Arrays.asList(args).contains("--direct");
            String host = config.getOrDefault("KIMI_PROXY_HOST", "");
            Proxy proxy = Proxy.NO_PROXY;
            if (!direct && !host.isBlank()) {
                int port = Integer.parseInt(config.getOrDefault("KIMI_PROXY_PORT", "0"));
                proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
            }
            System.out.println("Java=" + System.getProperty("java.version") + " endpoint=" + endpoint.getHost()
                    + " transport=" + (proxy == Proxy.NO_PROXY ? "DIRECT" : "HTTP_PROXY"));
            HttpURLConnection connection = (HttpURLConnection) URI.create(base + (chat ? "/chat/completions" : "/models"))
                    .toURL().openConnection(proxy);
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(chat ? 60000 : 10000);
            connection.setRequestProperty("Authorization", "Bearer " + key);
            if (chat) {
                String model = config.getOrDefault("KIMI_MODEL", "kimi-k2.6");
                if (!model.matches("[A-Za-z0-9._/-]+")) throw new IllegalArgumentException("Invalid model name");
                String options = model.equals("kimi-k2.6") ? ",\"thinking\":{\"type\":\"disabled\"}" : "";
                String payload = "{\"model\":\"" + model + "\",\"messages\":[{\"role\":\"user\","
                        + "\"content\":\"Reply briefly: what is 17 plus 26?\"}],\"stream\":false,\"max_tokens\":128" + options + "}";
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                try (var output = connection.getOutputStream()) {
                    output.write(payload.getBytes(StandardCharsets.UTF_8));
                }
            }
            int status = connection.getResponseCode();
            System.out.println("Kimi HTTP status=" + status);
            if (status != 200) {
                connection.disconnect();
                System.exit(2);
            }
            String body;
            try (var input = connection.getInputStream()) {
                body = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            connection.disconnect();
            if (chat) {
                var content = Pattern.compile("\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)+)\"").matcher(body);
                if (!content.find()) throw new IllegalStateException("No text reply in AI response");
                System.out.println("AI reply=" + content.group(1));
            } else if (!body.contains("\"data\"")) {
                throw new IllegalStateException("Unexpected models response");
            }
            System.out.println("AI_CHECK_OK");
        } catch (Exception error) {
            // Raw remote bodies and exception messages can contain sensitive provider details.
            System.err.println("AI_CHECK_FAILED: " + error.getClass().getSimpleName());
            System.exit(1);
        }
    }
}
