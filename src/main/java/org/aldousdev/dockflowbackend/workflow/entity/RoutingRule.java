package org.aldousdev.dockflowbackend.workflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.aldousdev.dockflowbackend.workflow.enums.RoutingType;

@Entity
@Table(name = "routing_rules", indexes = {
        @Index(name = "idx_template", columnList = "template_id"),
        @Index(name = "idx_step_order", columnList = "step_order")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutingRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private WorkflowTemplate template;

    /**
     * Шаг, с которого происходит переход
     */
    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    /**
     * Тип маршрутизации (ON_APPROVE, ON_REJECT, ON_TIMEOUT)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "routing_type", nullable = false)
    private RoutingType routingType;

    /**
     * Шаг, на который перейти (если null, завершить workflow)
     */
    @Column(name = "target_step")
    private Integer targetStep;

    /**
     * Может ли быть переопределено (for flexibility)
     */
    @Column(name = "is_override_allowed")
    private Boolean isOverrideAllowed = true;

    /**
     * Дополнительная информация
     */
    @Column(name = "description", length = 500)
    private String description;


}
