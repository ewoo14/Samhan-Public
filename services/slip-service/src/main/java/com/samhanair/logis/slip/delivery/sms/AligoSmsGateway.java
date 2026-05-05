package com.samhanair.logis.slip.delivery.sms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Aligo SMS 게이트웨이 — 한국 SMS 게이트웨이 통합.
 *
 * <p>API: {@code POST https://apis.aligo.in/send/} (form-urlencoded body)
 * <ul>
 *   <li>{@code key} — API key (콘솔 발급)</li>
 *   <li>{@code user_id} — Aligo 계정 ID</li>
 *   <li>{@code sender} — 사전 등록된 발신번호</li>
 *   <li>{@code receiver} — 수신번호 (콤마 구분)</li>
 *   <li>{@code msg} — 메시지 텍스트</li>
 *   <li>{@code msg_type} — SMS/LMS/MMS (자동 감지면 비움)</li>
 *   <li>{@code testmode_yn} — Y/N (운영은 N)</li>
 * </ul>
 *
 * <p>응답 (JSON): {@code {"result_code": 1, "message": "성공", "msg_id": "..."}}.
 * {@code result_code == 1} 만 success, 그 외 모두 failure (Aligo 공식 문서).
 *
 * <p>Solapi (HMAC-SHA256) 와 다르게 Aligo 는 form-urlencoded + 단순 key 인증 — 통합 단순.
 * 운영 활성화는 {@link SmsConfig} 의 PgSQL 프로파일 분기 @Bean 으로만 가능.
 */
public class AligoSmsGateway implements SmsGateway {

    private static final Logger log = LoggerFactory.getLogger(AligoSmsGateway.class);
    private static final String SEND_PATH = "/send/";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SmsProperties props;
    private final RestClient restClient;

    public AligoSmsGateway(SmsProperties props) {
        this.props = props;
        this.restClient = RestClient.builder()
                .baseUrl(props.baseUrl())
                .build();
    }

    @Override
    public SmsResult sendSms(String phone, String message) {
        try {
            String normalizedPhone = phone == null ? "" : phone.replace("-", "");
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("key", props.apiKey());
            form.add("user_id", props.userId());
            form.add("sender", props.senderPhone());
            form.add("receiver", normalizedPhone);
            form.add("msg", message == null ? "" : message);
            form.add("testmode_yn", "N");

            String response = restClient.post()
                    .uri(SEND_PATH)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);

            JsonNode root = MAPPER.readTree(response == null ? "{}" : response);
            int resultCode = root.path("result_code").asInt(-1);
            if (resultCode == 1) {
                String messageId = root.path("msg_id").asText(null);
                log.info("[AligoSmsGateway] sent phone={} msgId={}", normalizedPhone, messageId);
                return SmsResult.success(messageId);
            }
            String errorMsg = root.path("message").asText("Aligo error code=" + resultCode);
            log.warn("[AligoSmsGateway] send failed phone={} code={} msg={}",
                    normalizedPhone, resultCode, errorMsg);
            return SmsResult.failure(errorMsg);
        } catch (Exception ex) {
            log.warn("[AligoSmsGateway] send failed phone={} error={}", phone, ex.getMessage());
            return SmsResult.failure(ex.getMessage());
        }
    }
}
