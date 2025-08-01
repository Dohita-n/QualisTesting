package com.example.DataPreparationApp.Model;



import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transformation_steps")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransformationStep {

    @Id
    @GeneratedValue(generator ="UUID")
    @GenericGenerator(name="UUID",strategy="org.hibernate.id.UUIDGenerator")
    @Column(name="id",updatable = false,nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id", nullable = false)
    private Dataset dataset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "transformation_type", nullable = false)
    private String transformationType;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private JsonNode parameters;

    @Column(name = "applied_at", nullable = false)
    private LocalDateTime appliedAt;

    @OneToOne
    @JoinColumn(name = "previous_step_id")
    private TransformationStep previousStep;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preparation_id")
    private Preparation preparation;
    
    @Column(name = "sequence_order")
    private Integer sequenceOrder;
    
    @Column(name = "active")
    private Boolean active;
}