package com.alejandro.mtomaintenance.infrastructure.persistence.entity;

import com.alejandro.mtomaintenance.application.dto.operation.OperationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Una operacion de fondo ({@link com.alejandro.mtomaintenance.application.service.BudgetedOperations}).
 *
 * <p>Solo se lee con JPA: se escribe con las sentencias nativas de {@code BackgroundOperationRepository},
 * porque el tope del despliegue y el turno de cada ambito se deciden dentro de la sentencia que escribe,
 * con recuento de filas, nunca leyendo y escribiendo despues. No se audita: es su propio historico.</p>
 */
@Entity
@Table(name = "background_operation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BackgroundOperation {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "kind", nullable = false, length = 60)
    private String kind;

    @Column(name = "scope", length = 100)
    private String scope;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "background_operation_status")
    private OperationStatus status;

    @Column(name = "requested_by", nullable = false, length = 100)
    private String requestedBy;

    @Column(name = "correlation_id", length = 200)
    private String correlationId;

    @Column(name = "request_method", length = 10)
    private String requestMethod;

    @Column(name = "request_uri", length = 500)
    private String requestUri;

    @Column(name = "result_status")
    private Integer resultStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result", columnDefinition = "json")
    private String result;

    @Column(name = "error_status")
    private Integer errorStatus;

    @Column(name = "error_code", length = 40)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "heartbeat_at", nullable = false)
    private Instant heartbeatAt;
}
