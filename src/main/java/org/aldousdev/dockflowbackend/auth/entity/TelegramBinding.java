package org.aldousdev.dockflowbackend.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "user_telegram_bindings")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelegramBinding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "telegram_id", unique = true)
    private Long telegramId;

    @Column(name = "linking_token", unique = true)
    private String linkingToken;

    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
