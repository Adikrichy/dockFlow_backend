# DocFlow Backend

**Intelligent Document Workflow Automation System**  
Current development date: December 22, 2025  
Technologies: Spring Boot 3, JPA/Hibernate, Spring Security, JWT (cookie-based), Lombok, Swagger (springdoc-openapi)

## Current Project Status

The core functionality — **PDF document upload** with correct binding to the user's company — is fully implemented and thoroughly tested.

### What Already Works

1. **Authentication & Authorization**
    - Login endpoint issues a JWT token stored in an HttpOnly Secure cookie (`JWT` or `jwtWithCompany`).
    - Custom `JwtFilter` validates the cookie and sets up authentication.
    - All endpoints are protected via Spring Security.

2. **User and Company Model**
    - `User` entity with fields: email, password, firstName, lastName, status, role, etc.
    - Lazy collection `memberships` → `Membership` → `Company` (a user can belong to multiple companies).

3. **Key Performance & Stability Fix**
    - In `JwtFilter`, the user is loaded using a dedicated repository method:
      ```java
      @Query("SELECT u FROM User u " +
             "LEFT JOIN FETCH u.memberships m " +
             "LEFT JOIN FETCH m.company " +
             "WHERE u.email = :email")
      Optional<User> findByEmailWithMemberships(@Param("email") String email);