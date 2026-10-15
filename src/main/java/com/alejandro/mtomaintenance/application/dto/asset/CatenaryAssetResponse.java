package com.alejandro.mtomaintenance.application.dto.asset;

import com.alejandro.mtomaintenance.application.dto.common.AuditMetadataResponse;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DisconnectorDriveType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.SectionInsulatorInstallation;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.TrackKind;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Un activo. {@code enabled} es el valor efectivo; {@code enabledAtSource} (null en los propios) y
 * {@code disabledLocally} dicen por que esta desactivado: en mto-configuration, aqui, o las dos.
 * {@code driveType}, solo en un seccionador: con motor, a mano o null sin dato (V16).
 *
 * <p>Ejemplo, {@code GET /assets/{id}} (un seccionador llegado de mto-configuration):</p>
 * <pre>{@code
 * {"id": "…(DSC-77)", "code": "DSC-77", "name": "HSA-NS5", "type": "DISCONNECTOR", "executionPackageId": 6, "trackId": 2,
 *  "stationId": 4, "startKp": 14025.600, "endKp": 14025.600, "driveType": "MOTOR", "switches": [],
 *  "sourceService": "mto-configuration", "sourceEntityId": "77", "enabled": true, "enabledAtSource": true,
 *  "disabledLocally": false, "version": 3, …}
 * }</pre>
 */
public record CatenaryAssetResponse(
        UUID id,
        String code,
        String name,
        CatenaryAssetType type,
        String description,
        Long executionPackageId,
        Long trackId,
        Long stationId,
        BigDecimal startKp,
        BigDecimal endKp,
        String profileSourceId,
        String sectioning,
        TrackKind trackKind,
        Long connectedTrackId,
        SectionInsulatorInstallation installationType,
        DisconnectorDriveType driveType,
        List<CatenaryAssetSwitchResponse> switches,
        String sourceService,
        String sourceEntityId,
        Long sourceSequenceNumber,
        Boolean enabled,
        Boolean enabledAtSource,
        Boolean disabledLocally,
        Integer preventiveIntervalDays,
        Instant lastPreventiveCompletedAt,
        Instant nextPreventiveDueAt,
        Long version,
        AuditMetadataResponse audit
) {
}
