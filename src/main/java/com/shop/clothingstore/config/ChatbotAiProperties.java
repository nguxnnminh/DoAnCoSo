package com.shop.clothingstore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "chatbot.ai")
public class ChatbotAiProperties {

    /**
     * Master switch for the AI chatbot.
     * If disabled (or no API key), the app falls back to a lightweight response.
     */
    private boolean enabled = false;

    /**
     * Google Gemini API key (free tier — get one at https://aistudio.google.com/apikey).
     */
    private String geminiApiKey = "";

    /**
     * Gemini model name, e.g. gemini-2.5-flash.
     */
    private String geminiModel = "gemini-2.5-flash";

    /**
     * Gemini REST base URL (Generative Language API, v1beta supports function calling).
     */
    private String geminiBaseUrl = "https://generativelanguage.googleapis.com/v1beta";

    /**
     * Request timeout in milliseconds.
     */
    private int timeoutMs = 15_000;

    /**
     * If the API returns a rate-limit / quota / auth error, temporarily disable AI
     * calls to avoid spamming the API and logs.
     */
    private int cooldownSeconds = 300;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getGeminiApiKey() {
        return geminiApiKey;
    }

    public void setGeminiApiKey(String geminiApiKey) {
        this.geminiApiKey = geminiApiKey;
    }

    public String getGeminiModel() {
        return geminiModel;
    }

    public void setGeminiModel(String geminiModel) {
        this.geminiModel = geminiModel;
    }

    public String getGeminiBaseUrl() {
        return geminiBaseUrl;
    }

    public void setGeminiBaseUrl(String geminiBaseUrl) {
        this.geminiBaseUrl = geminiBaseUrl;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public void setCooldownSeconds(int cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }
}
