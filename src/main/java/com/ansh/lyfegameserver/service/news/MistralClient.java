package com.ansh.lyfegameserver.service.news;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thin client for the Mistral Chat Completions API.
 * Uses Java's built-in HttpClient and manual JSON string building/parsing
 * to avoid any Jackson dependency.
 */
@Component
public class MistralClient {

    private static final String API_URL = "https://api.mistral.ai/v1/chat/completions";
    private static final String MODEL   = "mistral-small-latest";
    private static final String SYSTEM_PROMPT =
        "You are a financial news writer for a fictional game economy called Lyfe Game. " +
        "You will be given a sentiment (BULLISH or BEARISH) and an event. " +
        "The sentiment of your article MUST match exactly: " +
        "BULLISH articles must be unambiguously good news — optimistic headline, positive language, no hedging or doubt. " +
        "BEARISH articles must be unambiguously bad news — alarming headline, negative language, no silver linings. " +
        "Never contradict the sentiment. Never mix positive and negative tones in the same article. " +
        "Always respond with valid JSON: {\"headline\": \"...\", \"body\": \"...\"}. " +
        "Body is exactly 2 sentences. Do not mention specific percentages.";

    /** Matches the inner "content" string from Mistral's response envelope. */
    private static final Pattern CONTENT_PATTERN =
        Pattern.compile("\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    /** Extracts headline and body from the structured JSON Mistral returns as content. */
    private static final Pattern HEADLINE_PATTERN =
        Pattern.compile("\"headline\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern BODY_PATTERN =
        Pattern.compile("\"body\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    @Value("${mistral.api.key:}")
    private String apiKey;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    /**
     * Calls Mistral and returns a {@link MistralArticle} with headline + body.
     * Returns a fallback article if the API key is missing or the call fails.
     */
    public MistralArticle generateArticle(String userPrompt) {
        if (apiKey == null || apiKey.isBlank()) {
            return fallback();
        }

        try {
            String requestBody = buildRequestJson(userPrompt);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(15))
                .build();

            HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return fallback();
            }

            return parseResponse(response.body());
        } catch (Exception e) {
            return fallback();
        }
    }

    // ─── JSON building ─────────────────────────────────────────────────────

    private String buildRequestJson(String userPrompt) {
        String systemContent = jsonEscape(SYSTEM_PROMPT);
        String userContent = jsonEscape(userPrompt);

        return "{"
            + "\"model\":\"" + MODEL + "\","
            + "\"messages\":["
            +   "{\"role\":\"system\",\"content\":\"" + systemContent + "\"},"
            +   "{\"role\":\"user\",\"content\":\"" + userContent + "\"}"
            + "],"
            + "\"response_format\":{\"type\":\"json_object\"},"
            + "\"max_tokens\":200,"
            + "\"temperature\":0.8"
            + "}";
    }

    // ─── Response parsing ──────────────────────────────────────────────────

    private MistralArticle parseResponse(String responseBody) {
        // Extract the content field from the Mistral envelope
        Matcher cm = CONTENT_PATTERN.matcher(responseBody);
        if (!cm.find()) return fallback();
        String content = jsonUnescape(cm.group(1));

        Matcher hm = HEADLINE_PATTERN.matcher(content);
        Matcher bm = BODY_PATTERN.matcher(content);
        if (!hm.find() || !bm.find()) return fallback();

        return new MistralArticle(
            jsonUnescape(hm.group(1)),
            jsonUnescape(bm.group(1))
        );
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String jsonUnescape(String s) {
        return s.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }

    private MistralArticle fallback() {
        return new MistralArticle(
            "Breaking: Market Movement Detected",
            "Analysts are observing significant activity in the Lyfe market. Traders are advised to proceed with caution."
        );
    }

    public record MistralArticle(String headline, String body) {}
}
