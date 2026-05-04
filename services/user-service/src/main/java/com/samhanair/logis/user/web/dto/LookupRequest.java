package com.samhanair.logis.user.web.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Body of {@code POST /users/employees/lookup} — batch fetch by ID, max 100. */
public record LookupRequest(
        @NotEmpty @Size(max = 100) List<UUID> ids) {
}
