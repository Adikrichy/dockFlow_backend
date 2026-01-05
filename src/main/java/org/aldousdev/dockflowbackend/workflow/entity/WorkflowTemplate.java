package org.aldousdev.dockflowbackend.workflow.entity;

import jakarta.persistence.*;
import lombok.*;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "workflow_templates")
@EntityListeners(AuditingEntityListener.class)
public class WorkflowTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "steps_xml", columnDefinition = "TEXT", nullable = false)
    private String stepsXml;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_user_id", nullable = false)
    private User createdBy;

    @Column(nullable = false)
    private Boolean isActive = true;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    /**
     * Уровни ролей, которым разрешено запускать этот workflow
     * Например: [60, 70, 100] = Manager, Director, CEO могут запускать
     * По умолчанию: [100] = только CEO
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "allowed_role_levels", columnDefinition = "integer[]")
    private Integer[] allowedRoleLevels;  // ✅ МАССИВ!

    @OneToMany(mappedBy = "template", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<RoutingRule> routingRules;

    /**
     * Проверяет может ли пользователь с данным уровнем роли запустить этот workflow
     */
    public boolean canStartWorkflow(Integer userRoleLevel) {
        if (allowedRoleLevels == null || allowedRoleLevels.length == 0) {
            // Если не указаны разрешения - разрешаем только CEO (100)
            return userRoleLevel != null && userRoleLevel >= 100;
        }

        if (userRoleLevel == null) {
            return false;
        }

        // Проверяем есть ли уровень пользователя в списке разрешенных
        for (Integer allowedLevel : allowedRoleLevels) {
            if (userRoleLevel >= allowedLevel) {
                return true;
            }
        }

        return false;
    }
}