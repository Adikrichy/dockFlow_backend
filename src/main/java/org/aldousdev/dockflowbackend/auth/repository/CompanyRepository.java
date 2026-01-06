package org.aldousdev.dockflowbackend.auth.repository;

import org.aldousdev.dockflowbackend.auth.entity.Company;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company,Long> {
    Optional<Company> findByName(String name);
    boolean existsByName(String name);
    List<Company> findByNameContainingIgnoreCase(String name);
    List<Company> findDistinctByMembershipsUser(User user);
}
