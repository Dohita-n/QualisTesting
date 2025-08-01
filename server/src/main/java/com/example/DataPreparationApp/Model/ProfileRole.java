package com.example.DataPreparationApp.Model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// ProfileRole.java (Join Table Entity)
@Entity
@Table(name = "profile_role")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileRole {
    @EmbeddedId
    private ProfileRoleKey id;

    @ManyToOne
    @MapsId("profileId")
    @JoinColumn(name = "profile_id")
    private Profile profile;

    @ManyToOne
    @MapsId("roleId")
    @JoinColumn(name = "role_id")
    private Role role;
}