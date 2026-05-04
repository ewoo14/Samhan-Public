package com.samhanair.logis.slip.delivery.sms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Solapi v4 SMS 게이트웨이 — Plan §6.
 *
 * <p>API: {@code POST https://api.solapi.com/messages/v4/send} body
 * {@code {"message":{"to":..., "from":..., "text":...}}}
 *
 * <p>인증 헤더 (Solapi v4 HMAC-SHA256):
 * {@code Authorization: HMAC-SHA256 apiKey={key}, date={iso8601}, salt={salt}, signature={hex}}
 * — signature = HMAC-SHA256(apiSecret, date + salt) HEX 인코딩.
 *
 * <p>본 클래스는 {@link RestClient} (LB 비활성, 외부 URL 직접 호출) 를 사용.
 * 운영 활성화는 {@link SmsConfig} 의 PgSQL 프로파일 분기 @Bean 으로만 가능.
 */
public class SolapiSmsGateway implements SmsGateway {

    private static final Logger log = LoggerFactory.getLogger(SolapiSmsGateway.class);
    private static final SecureRandom RNG = new SecureRandom();
    private static final String SEND_PATH = "/messages/v4/send";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SmsProperties props;
    private final RestClient restClient;

    public SolapiSmsGateway(SmsProperties props) {
        this.props = props;
        this.restClient = RestClient.builder()
                .baseUrl(props.baseUrl())
                .build();
    }

    @Override
    public SmsResult sendSms(String phone, String message) {
        try {
            String date = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
            String salt = randomSalt();
            String signature = sign(props.apiSecret(), date + salt);
            String authHeader = String.format(
                    "HMAC-SHA256 apiKey=%s, date=%s, salt=%s, signature=%s",
                    props.apiKey(), date, salt, signature);

            String normalizedPhone = phone == null ? "" : phone.replace("-", "");
            Map<String, Object> body = Map.of(
                    "message", Map.of(
                            "to", normalizedPhone,
                            "from", props.senderPhone(),
                            "text", message == null ? "" : message));

            String response = restClient.post()
                    .uri(SEND_PATH)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            String messageId = extractMessageId(response);
            log.info("[SolapiSmsGateway] sent phone={} messageId={}", normalizedPhone, messageId);
            return SmsResult.success(messageId);
        } catch (Exception ex) {
            log.warn("[SolapiSmsGateway] send failed phone={} error={}", phone, ex.getMessage());
            return SmsResult.failure(ex.getMessage());
        }
    }

    private static String randomSalt() {
        byte[] bytes = new byte[16];
        RNG.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String sign(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] sig = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(sig);
    }

    private static String extractMessageId(String response) {
        try {
            if (response == null || response.isBlank()) {
                return null;
            }
            JsonNode root = MAPPER.readTree(response);
            JsonNode id = root.path("messageId");
            if (id.isMissingNode() || id.isNull()) {
                id = root.path("message").path("messageId");
            }
            return id.asText(null);
        } catch (Exception ex) {
            return null;
        }
    }
}
