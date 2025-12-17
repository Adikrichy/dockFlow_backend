package org.aldousdev.dockflowbackend.auth.repository;

import org.aldousdev.dockflowbackend.auth.entity.CompanyRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompanyRoleEntityRepository extends JpaRepository<CompanyRoleEntity, Long> {
    List<CompanyRoleEntity> findByCompanyId(Long companyId);
    List<CompanyRoleEntity> findByIsSystemTrue();
}
