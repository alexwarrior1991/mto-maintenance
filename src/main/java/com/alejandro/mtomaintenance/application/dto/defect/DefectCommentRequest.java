package com.alejandro.mtomaintenance.application.dto.defect;

import jakarta.validation.constraints.NotBlank;

/** Cuerpo de close / discard: el motivo queda en el historial (y en discard_reason). */
public record DefectCommentRequest(@NotBlank String reason) {
}
