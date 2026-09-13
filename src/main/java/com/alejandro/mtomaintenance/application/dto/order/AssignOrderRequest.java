package com.alejandro.mtomaintenance.application.dto.order;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AssignOrderRequest(UUID teamId, @Size(max = 100) String assignedUser, String comment) {

    @AssertTrue(message = "teamId or assignedUser is required")
    public boolean hasAssignee() {
        return teamId != null || (assignedUser != null && !assignedUser.isBlank());
    }
}
