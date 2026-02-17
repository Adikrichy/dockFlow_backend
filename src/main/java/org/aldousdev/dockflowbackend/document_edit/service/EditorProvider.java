package org.aldousdev.dockflowbackend.document_edit.service;

import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.document_edit.dto.EditorConfigResponse;
import org.aldousdev.dockflowbackend.document_edit.entity.DocumentEditSession;
import org.aldousdev.dockflowbackend.document_edit.enums.EditorType;

public interface EditorProvider {
    EditorConfigResponse generateConfig(DocumentEditSession session, User user);
    EditorType getSupportedType();
}
