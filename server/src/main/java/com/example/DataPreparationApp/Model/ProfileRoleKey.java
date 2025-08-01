package com.example.DataPreparationApp.Model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileRoleKey implements Serializable {

    @Column(name="id",updatable = false,nullable = false)
    private UUID profileId;
    @Column(name="id",updatable = false,nullable = false)
    private UUID roleId;
}