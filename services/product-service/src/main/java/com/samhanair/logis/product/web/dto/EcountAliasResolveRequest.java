package com.samhanair.logis.product.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record EcountAliasResolveRequest(
        @NotEmpty
        @Size(max = 500)
        List<@NotBlank String> aliasCodes) {
}
