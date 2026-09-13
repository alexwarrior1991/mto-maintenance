package com.alejandro.mtomaintenance.application.dto.defect;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ResolveDefectRequest(
        @NotBlank String resolutionNotes,
        UUID resolvedInShiftId,
        @Size(max = 120) String correctionType,
        String partsReplaced
) {
}
