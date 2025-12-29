package org.aldousdev.dockflowbackend.auth.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.aldousdev.dockflowbackend.auth.enums.Status;
import org.aldousdev.dockflowbackend.auth.enums.UserType;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "users")
@ToString(exclude = {"memberships"})
@EqualsAndHashCode(exclude = {"id"})
@EntityListeners(AuditingEntityListener.class)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Email
    @Column(unique = true, nullable = false)
    private String email;

    @Size(min = 12, message = "Password must be at least 12 characters")
    @Column(nullable = false, length = 60)
    private String password;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserType userType;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Membership> memberships;

    @Column(nullable = false)
    private Boolean emailVerified = false;

    @Column(unique = true)
    private String emailVerificationCode;

    // Refresh token for JWT refresh functionality
    @Column(length = 500)
    private String refreshToken;

    @Column
    private LocalDateTime refreshTokenExpiry;

    @Column(columnDefinition = "integer default 0")
    private Integer resendCount = 0;

    @Column
    private LocalDateTime lastResendAt;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities(){
        return Collections.singletonList(new SimpleGrantedAuthority(userType.toString()));
    }

    @Override
    public String getUsername(){
        return email;
    }

    @Override
    public String getPassword(){
        return password;
    }

    @Override
    public boolean isAccountNonExpired(){
        return true;
    }

    @Override
    public boolean isAccountNonLocked(){
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired(){
        return true;
    }

    @Override
    public boolean isEnabled(){
        return true;
    }

}
