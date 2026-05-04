package com.samhanair.logis.user.web.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Body of {@code POST /users/employees/{id}/terminate}. */
public record TerminateRequest(@NotNull LocalDate terminationDate) {
}
