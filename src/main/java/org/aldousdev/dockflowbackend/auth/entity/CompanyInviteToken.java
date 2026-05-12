package org.aldousdev.dockflowbackend.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.aldousdev.dockflowbackend.auth.enums.InviteChannel;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "company_invite_tokens")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyInviteToken {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(nullable = false, name = "invited_by_id")
    private User invitedBy;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(nullable = false, name = "company_id")
    private Company company;

    @Column(nullable = false)
    private String invitedEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InviteChannel channel;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(nullable = false, name = "role_id")
    private CompanyRoleEntity assignedRole;

    @Column(nullable = false)
    private boolean used;

    public boolean isExpired(){
        return expiresAt.isBefore(LocalDateTime.now());
    }
}
