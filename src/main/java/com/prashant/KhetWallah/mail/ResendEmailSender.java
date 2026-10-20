package com.prashant.KhetWallah.mail;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

@Component
@Profile("!email-test")
public class ResendEmailSender implements EmailSender {
    private final boolean configured;
    private final String apiKey;
    private final String from;
    private final RestClient client;

    @Autowired
    public ResendEmailSender(
            @Value("${app.email.enabled:false}") boolean enabled,
            @Value("${app.email.resend-api-key:}") String apiKey,
            @Value("${app.email.from:}") String from
    ) {
        this(enabled, apiKey, from, createClient());
    }

    ResendEmailSender(boolean enabled, String apiKey, String from, RestClient client) {
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        this.from = from == null ? "" : from.strip();
        this.configured = enabled && !this.apiKey.isBlank()
                && !EmailContent.hasLineBreak(apiKey == null ? "" : apiKey)
                && EmailContent.isFrom(from) && EmailContent.isFrom(this.from);
        this.client = client;
    }

    private static RestClient createClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder().baseUrl("https://api.resend.com")
                .requestFactory(factory).build();
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public void send(String idempotencyKey, String recipient, String subject, String body) {
        if (!configured || idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.length() > 256 || EmailContent.hasLineBreak(idempotencyKey)) {
            throw new EmailDeliveryException();
        }
        EmailContent.validate(recipient, subject, body);
        try {
            client.post().uri("/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ResendMessage(from, List.of(recipient), subject, body))
                    .retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(),
                            (request, response) -> { throw new EmailDeliveryException(); })
                    .toBodilessEntity();
        } catch (RuntimeException ignored) {
            // Deliberately omit the provider response/cause: it may contain private message data.
            throw new EmailDeliveryException();
        }
    }

    private record ResendMessage(String from, List<String> to, String subject, String text) {
    }
}
