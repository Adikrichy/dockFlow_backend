package org.aldousdev.dockflowbackend.auth.repository;

import org.aldousdev.dockflowbackend.auth.entity.CompanyAccessKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompanyAccessKeyRepository extends JpaRepository<CompanyAccessKey, Long> {
    
    Optional<CompanyAccessKey> findByUserIdAndCompanyId(Long userId, Long companyId);
    
    boolean existsByUserIdAndCompanyId(Long userId, Long companyId);
    
    void deleteByUserIdAndCompanyId(Long userId, Long companyId);
}
