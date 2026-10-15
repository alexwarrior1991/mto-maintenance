package com.alejandro.mtomaintenance.application.dto.defect;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Resolucion de un defecto: que se hizo, con que piezas y en que turno.
 *
 * <p>Ejemplo, {@code POST /defects/{id}/resolve}:</p>
 * <pre>{@code
 * {"resolutionNotes": "Pendola sustituida", "resolvedInShiftId": "…(SH-000001)", "correctionType": "Sustitucion",
 *  "partsReplaced": "1 DROPPER-STD"}
 * }</pre>
 */
public record ResolveDefectRequest(
        @NotBlank String resolutionNotes,
        UUID resolvedInShiftId,
        @Size(max = 120) String correctionType,
        String partsReplaced
) {
}
