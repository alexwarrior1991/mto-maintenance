package com.alejandro.mtomaintenance.application.dto.shift;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionKind;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Una inspeccion registrada en el turno, tal como sale en su parte: no es una tarea, asi que va en
 * su propia lista y no entre las filas.
 *
 * <p>Ejemplo, un elemento de {@code ShiftReportResponse.inspections}:</p>
 * <pre>{@code
 * {"code": "INS-000004", "inspectionDate": "2026-01-28", "assetCode": "DSC-77", "assetName": "HSA-NS5",
 *  "assetType": "DISCONNECTOR", "trackId": 2, "inspectionKind": "TECHNICAL", "result": "UNSAFE",
 *  "generatedDefectCode": "DEF-000003", "generatedOrderCode": "MO-000002", …}
 * }</pre>
 *
 * @param trackId           la de la inspeccion, que es la de su activo al registrarla
 * @param kp                la de la inspeccion: la que se escribio, o la del activo
 * @param detectedDefects   lo observado, en texto libre
 * @param generatedDefectCode el defecto que se abrio desde ella, si se abrio
 * @param generatedOrderCode  la orden correctiva que se abrio desde ella, si se abrio
 */
public record ShiftReportInspectionResponse(
        UUID inspectionId,
        String code,
        LocalDate inspectionDate,
        String assetCode,
        String assetName,
        CatenaryAssetType assetType,
        Long trackId,
        BigDecimal kp,
        InspectionKind inspectionKind,
        InspectionResult result,
        String inspector,
        String detectedDefects,
        String generatedDefectCode,
        String generatedOrderCode
) {
}
