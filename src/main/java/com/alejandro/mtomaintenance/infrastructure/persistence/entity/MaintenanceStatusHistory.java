package com.alejandro.mtomaintenance.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Cambio de estado de una orden o de un defecto: quien, cuando, desde que estado y con que
 * comentario. Append-only y sin Envers: seria auditar un historial. Exactamente uno de
 * {@code order} / {@code defect} es no nulo (lo garantiza un CHECK).
 */
@Entity
@Table(
        name = "maintenance_status_history",
        indexes = {
                @Index(name = "idx_maintenance_status_history_order", columnList = "order_id, changed_at"),
                @Index(name = "idx_maintenance_status_history_defect", columnList = "defect_id, changed_at")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class MaintenanceStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    @Setter(AccessLevel.PROTECTED)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", foreignKey = @ForeignKey(name = "fk_maintenance_status_history_order"))
    private MaintenanceOrder order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "defect_id", foreignKey = @ForeignKey(name = "fk_maintenance_status_history_defect"))
    private CatenaryDefect defect;

    @Size(max = 32)
    @Column(name = "previous_status", length = 32)
    private String previousStatus;

    @NotBlank
    @Size(max = 32)
    @Column(name = "new_status", nullable = false, length = 32)
    private String newStatus;

    @NotNull
    @Builder.Default
    @Column(name = "changed_at", nullable = false)
    private Instant changedAt = Instant.now();

    @NotBlank
    @Size(max = 100)
    @Column(name = "changed_by", nullable = false, length = 100)
    private String changedBy;

    @Column(name = "comment", columnDefinition = "text")
    private String comment;
}
