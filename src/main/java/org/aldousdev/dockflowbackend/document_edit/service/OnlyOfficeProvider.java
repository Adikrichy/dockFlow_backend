package org.aldousdev.dockflowbackend.document_edit.service;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.document_edit.dto.EditorConfigResponse;
import org.aldousdev.dockflowbackend.document_edit.entity.DocumentEditSession;
import org.aldousdev.dockflowbackend.document_edit.enums.EditorType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OnlyOfficeProvider implements EditorProvider {

    @Value("${onlyoffice.jwt.secret}")
    private String jwtSecret;

    @Value("${onlyoffice.docs.url:http://localhost:8081}")
    private String onlyOfficeDocsUrl;

    @Value("${app.internal.url:http://host.docker.internal:8080}")
    private String internalBaseUrl;

    @Override
    public EditorConfigResponse generateConfig(DocumentEditSession session, User user) {
        log.info("Generating OnlyOffice config for session: {}", session.getSessionKey());

        String fileUrl = internalBaseUrl + "/api/document-edit/file/" + session.getSessionKey();
        String callbackUrl = internalBaseUrl + "/api/document-edit/onlyoffice/callback/" + session.getSessionKey();

        Map<String, Object> doc = new HashMap<>();
        doc.put("fileType", "docx");
        doc.put("key", session.getOnlyOfficeKey());
        doc.put("title", session.getDocument().getOriginalFilename());
        doc.put("url", fileUrl);

        Map<String, Object> fileAccessClaims = new HashMap<>();
        fileAccessClaims.put("sessionKey", session.getSessionKey());
        String fileToken = generateToken(fileAccessClaims);
        doc.put("token", fileToken);

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", String.valueOf(user.getId()));
        userMap.put("name", user.getFirstName() + " " + user.getLastName());

        Map<String, Object> editorConfig = new HashMap<>();
        editorConfig.put("mode", "edit");
        
        Map<String, Object> coEditing = new HashMap<>();
        coEditing.put("mode", "fast");
        editorConfig.put("coEditing", coEditing);

        editorConfig.put("callbackUrl", callbackUrl);
        editorConfig.put("user", userMap);

        Map<String, Object> customization = new HashMap<>();
        customization.put("forcesave", true);
        editorConfig.put("customization", customization);

        Map<String, Object> config = new HashMap<>();
        config.put("editorType", "ONLYOFFICE");
        config.put("documentType", "word");
        config.put("document", doc);
        config.put("editorConfig", editorConfig);

        String configToken = generateToken(config);
        config.put("token", configToken);
        config.put("documentServerUrl", onlyOfficeDocsUrl);

        return EditorConfigResponse.builder()
                .config(config)
                .build();
    }

    @Override
    public EditorType getSupportedType() {
        return EditorType.ONLYOFFICE;
    }

    private String generateToken(Map<String, Object> payload) {
        try {
            SecretKey secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            JwtBuilder builder = Jwts.builder();

            for (Map.Entry<String, Object> entry : payload.entrySet()) {
                builder.claim(entry.getKey(), entry.getValue());
            }

            builder.expiration(new Date(System.currentTimeMillis() + 86400000)); // 24 hours
            return builder.signWith(secretKey).compact();
        } catch (Exception e) {
            log.error("Failed to generate OnlyOffice token: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate OnlyOffice token", e);
        }
    }
}
