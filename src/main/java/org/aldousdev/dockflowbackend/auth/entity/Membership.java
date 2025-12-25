package org.aldousdev.dockflowbackend.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.aldousdev.dockflowbackend.auth.enums.CompanyRole;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(of = "id")
@EntityListeners(AuditingEntityListener.class)
@Table(name = "memberships")
public class Membership {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

//    @Enumerated(EnumType.STRING)
//    @Column(nullable = false)
//    private CompanyRole companyRole;

    @Column(nullable = false, updatable = false)
    private LocalDateTime joinDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private CompanyRoleEntity role;

    @PrePersist
    protected void onCreate(){
        if(joinDate == null) {
            joinDate = LocalDateTime.now();
        }
    }
}
