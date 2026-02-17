package org.aldousdev.dockflowbackend.document_edit.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "wopi_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WopiToken {
    @Id
    private String token; // The opaque token itself

    @Column(name = "session_key", nullable = false, length = 64)
    private String sessionKey;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
