package com.example.DataPreparationApp.Model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.example.DataPreparationApp.Services.AuthorityService;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.sql.Array;
import java.util.Collections;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
// Exclude collections from equals/hashCode to prevent circular references
@EqualsAndHashCode(exclude = {"userProfiles", "refreshTokensSet", "assignedProfiles"})
@ToString(exclude = {"userProfiles", "refreshTokensSet", "assignedProfiles"})
public class User implements UserDetails {

    @Transient
    private AuthorityService authorityService;

    @Transient
    private Collection<? extends GrantedAuthority> authorities = new HashSet<>();

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(
            name = "UUID",
            strategy = "org.hibernate.id.UUIDGenerator"
    )
    @Column(name = "id", updatable = false, nullable = false)
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID id;

    @Column(unique = true, nullable = false, name = "username")
    private String username;

    @JsonIgnore
    @Column(nullable = false, name = "password")
    private String password;

    @Column(unique = true, nullable = false, name = "email")
    private String email;
    
    @Column(name = "first_name")
    private String firstName;
    
    @Column(name = "last_name")
    private String lastName;
    
    @Column(name = "avatar_url")
    private String avatarUrl;
    
    @Column(name = "account_non_expired")
    @Builder.Default
    private boolean accountNonExpired = true;
    
    @Column(name = "account_non_locked")
    @Builder.Default
    private boolean accountNonLocked = true;
    
    @Column(name = "credentials_non_expired")
    @Builder.Default
    private boolean credentialsNonExpired = true;
    
    @Column(name = "enabled")
    @Builder.Default
    private boolean enabled = true;
    
    @Column(name = "failed_attempt_count")
    @Builder.Default
    private int failedAttemptCount = 0;
    
    @Column(name = "last_failed_attempt")
    private LocalDateTime lastFailedAttempt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserProfile> userProfiles = new HashSet<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private Set<RefreshToken> refreshTokensSet = new HashSet<>();

    @OneToMany(mappedBy = "assignedBy", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserProfile> assignedProfiles = new HashSet<>();

    @Autowired
    public void setAuthorityService(AuthorityService authorityService) {
        this.authorityService = authorityService;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Use the authority service if available, otherwise return the cached authorities
        if (authorityService != null) {
            return authorityService.getAuthorities(this);
        }
        return authorities;
    }

    // Utility method to set authorities (used manually when authority service is not available)
    public void setAuthorities(Collection<? extends GrantedAuthority> authorities) {
        this.authorities = authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return accountNonExpired;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return credentialsNonExpired;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}