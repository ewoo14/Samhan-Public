package com.samhanair.logis.accounting.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhanair.logis.security.InternalAuthProperties;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class ProductAliasClient {

    private static final Logger log = LoggerFactory.getLogger(ProductAliasClient.class);
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final RestClient restClient;
    private final InternalAuthProperties internalAuthProperties;
    private final ObjectMapper objectMapper;

    public ProductAliasClient(@Qualifier("loadBalancedRestClientBuilder") RestClient.Builder builder,
                              InternalAuthProperties internalAuthProperties,
                              ObjectMapper objectMapper,
                              @Value("${app.services.product-service.base-url:http://product-service}")
                              String productServiceBaseUrl) {
        this.restClient = builder.baseUrl(productServiceBaseUrl).build();
        this.internalAuthProperties = internalAuthProperties;
        this.objectMapper = objectMapper;
    }

    public Map<String, UUID> resolveAliases(List<String> aliasCodes) {
        LinkedHashSet<String> distinct = normalize(aliasCodes);
        if (distinct.isEmpty()) {
            return Map.of();
        }
        String token = internalAuthProperties.getToken();
        if (token == null || token.isBlank()) {
            log.warn("ProductAliasClient - X-Internal-Token missing (aliasCount={})", distinct.size());
            return Map.of();
        }
        try {
            String body = restClient.post()
                    .uri("/products/internal/resolve-ecount-aliases")
                    .header(INTERNAL_TOKEN_HEADER, token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("aliasCodes", List.copyOf(distinct)))
                    .retrieve()
                    .body(String.class);
            return parseResolved(body);
        } catch (RestClientResponseException ex) {
            log.warn("ProductAliasClient resolve failed - aliasCount={}, status={}",
                    distinct.size(), ex.getStatusCode().value());
            return Map.of();
        } catch (Exception ex) {
            log.warn("ProductAliasClient resolve failed - aliasCount={}, msg={}",
                    distinct.size(), ex.getMessage());
            return Map.of();
        }
    }

    private Map<String, UUID> parseResolved(String body) throws java.io.IOException {
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        JsonNode root = objectMapper.readTree(body);
        JsonNode data = root.has("data") ? root.get("data") : root;
        JsonNode resolved = data == null ? null : data.get("resolved");
        if (resolved == null || !resolved.isObject()) {
            return Map.of();
        }
        Map<String, UUID> result = new LinkedHashMap<>();
        resolved.fields().forEachRemaining(entry -> {
            if (entry.getValue() != null && !entry.getValue().isNull()) {
                try {
                    result.put(entry.getKey(), UUID.fromString(entry.getValue().asText()));
                } catch (IllegalArgumentException ex) {
                    log.warn("ProductAliasClient ignored invalid UUID for aliasCode={}", entry.getKey());
                }
            }
        });
        return result;
    }

    private static LinkedHashSet<String> normalize(List<String> aliasCodes) {
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        if (aliasCodes == null) {
            return distinct;
        }
        for (String aliasCode : aliasCodes) {
            if (aliasCode != null && !aliasCode.isBlank()) {
                distinct.add(aliasCode.trim());
            }
        }
        return distinct;
    }
}
