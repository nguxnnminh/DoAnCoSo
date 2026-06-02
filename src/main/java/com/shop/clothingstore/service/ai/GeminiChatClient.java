package com.shop.clothingstore.service.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.clothingstore.config.ChatbotAiProperties;

/**
 * Thin REST client for the Google Gemini Generative Language API (v1beta).
 *
 * <p>Supports multi-turn conversations and <b>function calling</b>: the caller
 * passes tool (function) declarations, and Gemini may respond with a
 * {@code functionCall} part instead of plain text. The caller executes the
 * function, appends the {@code functionResponse} to the conversation, and calls
 * {@link #generate} again until Gemini returns a final text answer.</p>
 */
@Component
public class GeminiChatClient {

    private final ChatbotAiProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GeminiChatClient(ChatbotAiProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1_000, props.getTimeoutMs())))
                .build();
    }

    public boolean isConfigured() {
        return props.isEnabled()
                && props.getGeminiApiKey() != null
                && !props.getGeminiApiKey().isBlank()
                && props.getGeminiModel() != null
                && !props.getGeminiModel().isBlank();
    }

    /**
     * Run one Gemini generateContent turn.
     *
     * @param systemInstruction system prompt (shop knowledge + behaviour rules); may be null
     * @param contents          conversation so far (Gemini "contents": role user/model + parts)
     * @param tools             tool declarations (Gemini "tools"); may be null/empty
     * @return the first candidate's {@code content} node (has "parts" and "role"),
     *         or null if the response had no usable candidate
     */
    public JsonNode generate(String systemInstruction,
            List<Map<String, Object>> contents,
            List<Map<String, Object>> tools) throws IOException, InterruptedException {

        String base = props.getGeminiBaseUrl().replaceAll("/+$", "");
        URI uri = URI.create(base + "/models/" + props.getGeminiModel()
                + ":generateContent?key=" + props.getGeminiApiKey());

        Map<String, Object> payload = new HashMap<>();
        if (systemInstruction != null && !systemInstruction.isBlank()) {
            payload.put("system_instruction", Map.of("parts", List.of(Map.of("text", systemInstruction))));
        }
        payload.put("contents", contents);
        if (tools != null && !tools.isEmpty()) {
            payload.put("tools", tools);
        }
        // Disable "thinking" on 2.5 flash so the token budget goes to the actual
        // answer (thinking tokens otherwise count against maxOutputTokens and can
        // truncate the reply, especially across function-calling rounds).
        payload.put("generationConfig", Map.of(
                "temperature", 0.4,
                "maxOutputTokens", 1024,
                "thinkingConfig", Map.of("thinkingBudget", 0)));

        String json = objectMapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofMillis(Math.max(2_000, props.getTimeoutMs())))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            int status = response.statusCode();
            String body = truncate(response.body(), 1000);
            throw new AiRequestException(status, "Gemini request failed: HTTP " + status + " - " + body, body);
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            return null;
        }
        JsonNode content = candidates.get(0).path("content");
        return content.isMissingNode() ? null : content;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
