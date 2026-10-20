package com.prashant.KhetWallah.mail;

import org.springframework.context.annotation.Profile;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/** Captures messages in the local inbox; never contacts an external mail provider. */
@Component
@Profile("email-test")
public class LocalTestEmailSender implements EmailSender {
    static final String BASE_URL = "http://127.0.0.1:8025";
    private final EmailSender delegate;

    public LocalTestEmailSender() {
        this(createClient());
    }

    LocalTestEmailSender(RestClient client) {
        this.delegate = new ResendEmailSender(true, "local-test",
                "KhetWallah <test@khetwallah.local>", client);
    }

    private static RestClient createClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder().baseUrl(BASE_URL).requestFactory(factory).build();
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public void send(String idempotencyKey, String recipient, String subject, String body) {
        delegate.send(idempotencyKey, recipient, subject, body);
    }
}
