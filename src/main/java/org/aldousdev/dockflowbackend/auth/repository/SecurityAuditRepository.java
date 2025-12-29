package org.aldousdev.dockflowbackend.auth.repository;

import org.aldousdev.dockflowbackend.auth.entity.SecurityAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository для security audit logs
 */
public interface SecurityAuditRepository extends JpaRepository<SecurityAuditLog, Long> {

    @Query("SELECT s FROM SecurityAuditLog s WHERE s.user.id = :userId ORDER BY s.timestamp DESC")
    List<SecurityAuditLog> findByUserIdOrderByTimestampDesc(@Param("userId") Long userId);

    @Query("SELECT s FROM SecurityAuditLog s WHERE s.ipAddress = :ipAddress ORDER BY s.timestamp DESC")
    List<SecurityAuditLog> findByIpAddressOrderByTimestampDesc(@Param("ipAddress") String ipAddress);

    @Query("SELECT s FROM SecurityAuditLog s WHERE s.eventType = :eventType ORDER BY s.timestamp DESC")
    List<SecurityAuditLog> findByEventTypeOrderByTimestampDesc(@Param("eventType") String eventType);

    @Query("SELECT s FROM SecurityAuditLog s WHERE s.timestamp BETWEEN :startDate AND :endDate ORDER BY s.timestamp DESC")
    List<SecurityAuditLog> findByTimestampBetweenOrderByTimestampDesc(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(s) FROM SecurityAuditLog s WHERE s.eventType = 'LOGIN_FAILED' AND s.ipAddress = :ipAddress AND s.timestamp > :since")
    long countFailedLoginsByIpSince(@Param("ipAddress") String ipAddress, @Param("since") LocalDateTime since);
}
