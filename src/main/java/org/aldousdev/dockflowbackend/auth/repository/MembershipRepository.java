package org.aldousdev.dockflowbackend.auth.repository;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.auth.entity.Company;
import org.aldousdev.dockflowbackend.auth.entity.Membership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MembershipRepository extends JpaRepository<Membership, Long> {
    List<Membership> findByUser(User user);
    @Query("SELECT m FROM Membership m WHERE m.company.id = :companyId AND m.user.id = :userId")
    Optional<Membership> findByCompanyIdAndUserId(@Param("companyId") Long companyId, @Param("userId") Long userId);

    @Query("SELECT m.user FROM Membership m WHERE m.company.id = :companyId AND m.role.name = :roleName AND m.role.level >= :minLevel")
    List<User> findUsersByCompanyIdAndRoleNameAndMinLevel(@Param("companyId") Long companyId,
                                                         @Param("roleName") String roleName,
                                                         @Param("minLevel") Integer minLevel);
    List<Membership> findByCompany(Company company);
}
