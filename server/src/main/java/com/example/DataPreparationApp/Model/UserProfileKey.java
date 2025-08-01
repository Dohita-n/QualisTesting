package com.example.DataPreparationApp.Model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileKey implements Serializable {
    @Column(name="user_id", nullable=false)
    private UUID userId;
    @Column(name="profile_id", nullable=false)
    private UUID profileId;
}
