package org.aldousdev.dockflowbackend.document_edit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OnlyOfficeClient {

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${onlyoffice.docs.url:http://localhost:8081}")
    private String onlyOfficeDocsUrl;

    @Value("${onlyoffice.jwt.secret}")
    private String jwtSecret;

    public byte[] downloadFile(String url) {
        log.info("Downloading file from OnlyOffice: {}", url);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Successfully downloaded {} bytes from {}", response.body().length, url);
                return response.body();
            }
            log.error("Failed to download file from OnlyOffice. Status: {}, URL: {}", response.statusCode(), url);
            throw new IllegalStateException("Failed to download file, status=" + response.statusCode());
        } catch (Exception e) {
            throw new RuntimeException("Failed to download OnlyOffice file: " + e.getMessage(), e);
        }
    }

    public String convertDocxToPdf(String docxUrl, String key, String title) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("async", false);
            payload.put("filetype", "docx");
            payload.put("key", key);
            payload.put("outputtype", "pdf");
            payload.put("title", title);
            payload.put("url", docxUrl);

            // Генерируем токен для запроса конвертации
            String token = generateToken(payload);

            // Добавляем токен в payload
            payload.put("token", token);

            String body = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(onlyOfficeDocsUrl + "/ConvertService.ashx"))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("Conversion response status: {}, body: {}", response.statusCode(), response.body());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Convert request failed, status=" + response.statusCode() + ", body=" + response.body());
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> json = objectMapper.readValue(response.body(), Map.class);
            Object endConvert = json.get("endConvert");
            if (Boolean.TRUE.equals(endConvert)) {
                Object fileUrl = json.get("fileUrl");
                if (fileUrl == null) {
                    throw new IllegalStateException("Conversion response missing fileUrl");
                }
                return String.valueOf(fileUrl);
            }

            throw new IllegalStateException("Conversion did not finish: " + response.body());
        } catch (Exception e) {
            throw new RuntimeException("OnlyOffice conversion failed: " + e.getMessage(), e);
        }
    }

    public int executeCommand(String command, String key) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("c", command);
            payload.put("key", key);

            // Generate token for command
            String token = generateToken(payload);
            payload.put("token", token);

            String body = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(onlyOfficeDocsUrl + "/coauthoring/CommandService.ashx"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("Command '{}' for key '{}' response: {}", command, key, response.body());

            if (response.statusCode() != 200) {
                 log.warn("Command failed validation with status {}", response.statusCode());
                 // Don't throw exception yet, sometimes it returns 200 with error code in body
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> json = objectMapper.readValue(response.body(), Map.class);
            Object error = json.get("error");
            if (error instanceof Number && ((Number) error).intValue() != 0) {
                int errorCode = ((Number) error).intValue();
                log.error("OnlyOffice command '{}' returned error: {}", command, errorCode);
                return errorCode;
            }

            return 0; // Success or no specific error code
        } catch (Exception e) {
            log.error("Failed to execute OnlyOffice command: {}", e.getMessage(), e);
            return -1; // Indicate a general failure due to exception
        }
    }

    private String generateToken(Map<String, Object> payload) {
        try {
            SecretKey secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            JwtBuilder builder = Jwts.builder();
            for (Map.Entry<String, Object> entry : payload.entrySet()) {
                builder.claim(entry.getKey(), entry.getValue());
            }
            return builder.signWith(secretKey).compact();
        } catch (Exception e) {
            log.error("Failed to generate token: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate token", e);
        }
    }
}