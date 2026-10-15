package com.alejandro.mtomaintenance.application.dto.defect;

import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo de close / discard: el motivo queda en el historial (y en discard_reason).
 *
 * <p>Ejemplo, {@code POST /defects/{id}/close}:</p>
 * <pre>{@code
 * {"reason": "Revisado por supervision: pendola sustituida en SH-000001"}
 * }</pre>
 */
public record DefectCommentRequest(@NotBlank String reason) {
}
