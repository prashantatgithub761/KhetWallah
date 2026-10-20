package com.prashant.KhetWallah.mail;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ResendEmailSenderTest {
    @Test
    void sendsPlainTextToResendWithAuthenticationAndStableIdempotencyKey() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.resend.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ResendEmailSender sender = new ResendEmailSender(true, "re_test_key",
                "KhetWallah <orders@example.com>", builder.build());

        server.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer re_test_key"))
                .andExpect(header("Idempotency-Key", "outbox-fixed-id"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"from":"KhetWallah <orders@example.com>",
                         "to":["buyer@example.com"], "subject":"Order accepted",
                         "text":"Your order is ready.\\nBring your order number."}
                        """))
                .andRespond(withSuccess("{\"id\":\"provider-message-id\"}", MediaType.APPLICATION_JSON));

        assertTrue(sender.isConfigured());
        sender.send("outbox-fixed-id", "buyer@example.com", "Order accepted",
                "Your order is ready.\nBring your order number.");
        server.verify();
    }

    @Test
    void disabledAndIncompleteConfigurationMakeNoHttpRequest() {
        RestClient client = mock(RestClient.class);
        for (ResendEmailSender sender : new ResendEmailSender[]{
                new ResendEmailSender(false, "key", "from@example.com", client),
                new ResendEmailSender(true, "", "from@example.com", client),
                new ResendEmailSender(true, "key", "", client),
                new ResendEmailSender(true, "key", "invalid-sender", client),
                new ResendEmailSender(true, "key\nsecret", "from@example.com", client),
                new ResendEmailSender(true, "key", "from@example.com\r\nBcc: other@example.com", client)
        }) {
            assertFalse(sender.isConfigured());
            assertThrows(EmailDeliveryException.class,
                    () -> sender.send("key", "buyer@example.com", "Subject", "Body"));
        }
        verifyNoInteractions(client);
    }

    @Test
    void providerErrorsExposeNoResponseBodyCredentialsRecipientOrOtp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.resend.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ResendEmailSender sender = new ResendEmailSender(true, "re_test_secret",
                "from@example.com", builder.build());
        server.expect(requestTo("https://api.resend.com/emails"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .body("re_test_secret buyer@example.com 123456"));

        EmailDeliveryException exception = assertThrows(EmailDeliveryException.class,
                () -> sender.send("registration-123", "buyer@example.com", "Verify email", "123456"));

        assertEquals("Email delivery is temporarily unavailable", exception.getMessage());
        assertNull(exception.getCause());
        server.verify();
    }

    @Test
    void rejectsRedirectResponsesInsteadOfTreatingThemAsAccepted() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.resend.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ResendEmailSender sender = new ResendEmailSender(true, "key",
                "from@example.com", builder.build());
        server.expect(requestTo("https://api.resend.com/emails"))
                .andRespond(withStatus(HttpStatus.FOUND));

        assertThrows(EmailDeliveryException.class,
                () -> sender.send("message-id", "buyer@example.com", "Subject", "Body"));
        server.verify();
    }

    @Test
    void rejectsHeaderInjectionWithoutCallingTheClient() {
        RestClient client = mock(RestClient.class);
        ResendEmailSender sender = new ResendEmailSender(true, "key", "from@example.com", client);

        assertThrows(EmailDeliveryException.class,
                () -> sender.send("id\r\nHeader: injected", "buyer@example.com", "Subject", "Body"));
        assertThrows(EmailDeliveryException.class,
                () -> sender.send("id", "buyer@example.com", "Subject\nBcc: attacker@example.com", "Body"));
        assertThrows(EmailDeliveryException.class,
                () -> sender.send("id", "buyer@example.com\nattacker@example.com", "Subject", "Body"));
        verifyNoInteractions(client);
    }
}
