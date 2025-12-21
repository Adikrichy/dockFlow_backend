package org.aldousdev.dockflowbackend.workflow.entity;

import jakarta.persistence.*;
import lombok.*;
import org.aldousdev.dockflowbackend.auth.entity.Company;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

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
     * XML-описание маршрута согласования.
     * Пример структуры:
     * <workflow>
     *   <step order="1" roleName="Director" roleLevel="80" action="approve" parallel="false"/>
     *   <step order="2" roleName="Lawyer" roleLevel="70" action="review"/>
     *   <step order="3" roleName="CEO" roleLevel="100" action="sign"/>
     * </workflow>
     */

    @Column(columnDefinition = "TEXT", nullable = false)
    private String stepsXml;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_user_id", nullable = false)
    private User createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

}
