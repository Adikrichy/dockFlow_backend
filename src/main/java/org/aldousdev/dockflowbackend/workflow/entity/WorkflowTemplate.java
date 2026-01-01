package org.aldousdev.dockflowbackend.workflow.entity;

import jakarta.persistence.*;
import lombok.*;
import org.aldousdev.dockflowbackend.auth.entity.User;
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

    /**
     * XML-описание маршрута согласования с правилами маршрутизации.
     * Пример структуры:
     * <workflow>
     *   <step order="1" roleName="Manager" roleLevel="60" action="review" parallel="false"/>
     *   <step order="2" roleName="Director" roleLevel="80" action="approve" parallel="true"/>
     *   <step order="3" roleName="CEO" roleLevel="100" action="sign" parallel="false"/>
     *   <onReject stepOrder="2" targetStep="1" description="Return to manager for revision"/>
     * </workflow>
     */
    @Column(name = "steps_xml", columnDefinition = "TEXT", nullable = false)
    private String stepsXml;

    /**
     * Company ID (хранится как Long, а не relationship для гибкости)
     */
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
     * Правила маршрутизации для этого template
     */
    @OneToMany(mappedBy = "template", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<RoutingRule> routingRules;
}
