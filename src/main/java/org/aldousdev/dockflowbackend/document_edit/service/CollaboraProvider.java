package org.aldousdev.dockflowbackend.document_edit.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.document_edit.dto.EditorConfigResponse;
import org.aldousdev.dockflowbackend.document_edit.entity.DocumentEditSession;
import org.aldousdev.dockflowbackend.document_edit.enums.EditorType;
import org.aldousdev.dockflowbackend.document_edit.repository.DocumentEditSessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Slf4j
public class CollaboraProvider implements EditorProvider {

    private final WopiTokenService tokenService;
    private final DocumentEditSessionRepository sessionRepository;

    @Value("${collabora.url.base:http://localhost:9980}")
    private String collaboraBaseUrl;

    @Value("${app.internal.url:http://localhost:8080}")
    private String internalBaseUrl;

    @Override
    @Transactional
    public EditorConfigResponse generateConfig(DocumentEditSession session, User user) {
        log.info("Generating Collabora (WOPI) config for session: {}", session.getSessionKey());

        // Create short-lived opaque token (60 min)
        String token = tokenService.createToken(session.getSessionKey(), user.getId(), 60);

        // We use sessionKey as the WOPI fileId
        String wopiFileId = session.getSessionKey();
        session.setWopiFileId(wopiFileId);
        sessionRepository.save(session);

        // Standard Collabora path (COOL = Collabora Online Office Online)
        // Note: in older versions it was /loleaflet/dist/loleaflet.html
        String wopiSrc = internalBaseUrl + "/api/wopi/files/" + wopiFileId;
        String editorUrl = collaboraBaseUrl + "/browser/dist/cool.html?WOPISrc=" 
                + URLEncoder.encode(wopiSrc, StandardCharsets.UTF_8)
                + "&permission=edit"
                + "&NotWOPIButIframe=true";

        Map<String, Object> config = new HashMap<>();
        config.put("editorType", "COLLABORA");
        config.put("url", editorUrl);
        config.put("token", token);
        config.put("access_token_ttl", System.currentTimeMillis() + (60L * 60 * 1000)); // 60 minutes from now (epoch ms)

        log.info("Generated Collabora config for session {}. WOPISrc: {}", session.getSessionKey(), wopiSrc);

        return EditorConfigResponse.builder()
                .config(config)
                .build();
    }

    @Override
    public EditorType getSupportedType() {
        return EditorType.COLLABORA;
    }
}
