package org.aldousdev.dockflowbackend.auth.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@Table(name = "company_roles")
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CompanyRoleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String name;
    private Integer level;
    private Boolean isSystem;

    @ManyToOne
    @JoinColumn(name = "company_id")
    private Company company;
}
