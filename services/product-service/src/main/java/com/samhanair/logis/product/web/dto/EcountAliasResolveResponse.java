package com.samhanair.logis.product.web.dto;

import java.util.Map;
import java.util.UUID;

public record EcountAliasResolveResponse(Map<String, UUID> resolved) {
}
