package com.prashant.KhetWallah.mail;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class LocalTestEmailSenderTest {
    @Test
    void sendsOnlyToTheFixedLoopbackInboxUsingLocalCredentials() {
        RestClient.Builder builder = RestClient.builder().baseUrl(LocalTestEmailSender.BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LocalTestEmailSender sender = new LocalTestEmailSender(builder.build());
        server.expect(requestTo("http://127.0.0.1:8025/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer local-test"))
                .andExpect(header("Idempotency-Key", "signup-local-1"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"from":"KhetWallah <test@khetwallah.local>",
                         "to":["buyer@example.com"], "subject":"Verification code",
                         "text":"Your code is 123456"}
                        """))
                .andRespond(withSuccess("{\"id\":\"local-message-id\"}", MediaType.APPLICATION_JSON));

        assertTrue(sender.isConfigured());
        sender.send("signup-local-1", "buyer@example.com", "Verification code", "Your code is 123456");
        server.verify();
    }

    @Test
    void localInboxFailuresUseTheSameSanitizedDeliveryException() {
        RestClient.Builder builder = RestClient.builder().baseUrl(LocalTestEmailSender.BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LocalTestEmailSender sender = new LocalTestEmailSender(builder.build());
        server.expect(requestTo("http://127.0.0.1:8025/emails"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("buyer@example.com private OTP 123456"));

        EmailDeliveryException exception = assertThrows(EmailDeliveryException.class,
                () -> sender.send("local-1", "buyer@example.com", "Verification", "Code 123456"));

        assertEquals("Email delivery is temporarily unavailable", exception.getMessage());
        assertNull(exception.getCause());
        server.verify();
    }

    @Test
    void profilesSelectExactlyOneSenderAndTheLocalProfileNeedsNoProviderConfiguration() {
        assertArrayEquals(new String[]{"email-test"},
                LocalTestEmailSender.class.getAnnotation(Profile.class).value());
        assertArrayEquals(new String[]{"!email-test"},
                ResendEmailSender.class.getAnnotation(Profile.class).value());
        assertEquals("http://127.0.0.1:8025", LocalTestEmailSender.BASE_URL);
        assertTrue(new LocalTestEmailSender().isConfigured());
    }
}
