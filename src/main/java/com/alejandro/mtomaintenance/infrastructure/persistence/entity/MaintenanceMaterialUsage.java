package com.alejandro.mtomaintenance.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Linea de material de una orden (o de una tarea concreta). Nada de stock vive aqui: material y
 * almacen son uuid de mto-stock, la reserva vive alli y {@code stockSyncStatus} dice en que punto
 * esta la conversacion.
 */
@Audited
@Entity
@Table(
        name = "maintenance_material_usage",
        uniqueConstraints = @UniqueConstraint(name = "uq_maintenance_material_usage_line", columnNames = {"order_id", "task_id", "material_id", "warehouse_id"}),
        indexes = {
                @Index(name = "idx_maintenance_material_usage_order", columnList = "order_id"),
                @Index(name = "idx_maintenance_material_usage_sync_status", columnList = "stock_sync_status")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class MaintenanceMaterialUsage extends AuditableEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(name = "fk_maintenance_material_usage_order"))
    private MaintenanceOrder order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", foreignKey = @ForeignKey(name = "fk_maintenance_material_usage_task"))
    private MaintenanceTask task;

    @NotNull
    @Column(name = "material_id", nullable = false)
    @ToString.Include
    private UUID materialId;

    @Size(max = 64)
    @Column(name = "material_code", length = 64)
    @ToString.Include
    private String materialCode;

    @Size(max = 255)
    @Column(name = "material_description_snapshot")
    private String materialDescriptionSnapshot;

    @NotNull
    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @NotNull
    @PositiveOrZero
    @Digits(integer = 13, fraction = 6)
    @Column(name = "planned_quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal plannedQuantity;

    @NotNull
    @PositiveOrZero
    @Digits(integer = 13, fraction = 6)
    @Builder.Default
    @Column(name = "consumed_quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal consumedQuantity = BigDecimal.ZERO;

    @NotBlank
    @Size(max = 32)
    @Column(name = "unit", nullable = false, length = 32)
    private String unit;

    @NotNull
    @Builder.Default
    @Column(name = "allow_over_consumption", nullable = false)
    private Boolean allowOverConsumption = false;

    @Column(name = "stock_reservation_id")
    private UUID stockReservationId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Builder.Default
    @Column(name = "stock_sync_status", nullable = false, columnDefinition = "stock_sync_status")
    @ToString.Include
    private StockSyncStatus stockSyncStatus = StockSyncStatus.NOT_REQUESTED;

    @Column(name = "stock_sync_error", columnDefinition = "text")
    private String stockSyncError;

    public boolean isSyncFailed() {
        return StockSyncStatus.FAILED == stockSyncStatus;
    }

    public void markReserved(UUID reservationId) {
        this.stockReservationId = reservationId;
        this.stockSyncStatus = StockSyncStatus.RESERVED;
        this.stockSyncError = null;
    }

    public void markConsumed() {
        this.stockSyncStatus = StockSyncStatus.CONSUMED;
        this.stockSyncError = null;
    }

    public void markReleased() {
        this.stockSyncStatus = StockSyncStatus.RELEASED;
        this.stockSyncError = null;
    }

    public void markFailed(String error) {
        this.stockSyncStatus = StockSyncStatus.FAILED;
        this.stockSyncError = error;
    }
}
