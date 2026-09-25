package com.prashant.KhetWallah.order;

import com.prashant.KhetWallah.config.PasswordConfig;
import com.prashant.KhetWallah.config.SecurityConfig;
import com.prashant.KhetWallah.error.GlobalExceptionHandler;
import com.prashant.KhetWallah.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, PasswordConfig.class, GlobalExceptionHandler.class})
@WithMockUser(username = "farmer@example.com", roles = "USER")
class OrderPickupControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private OrderService orderService;
    @MockitoBean private AppUserRepository userRepository;

    private static final String VALID = """
        {"pickupAt":"2099-01-01T10:00:00Z","pickupInstructions":"Village market gate"}
        """;

    @Test
    void schedulesUsingAuthenticatedFarmer() throws Exception {
        mockMvc.perform(post("/api/orders/1/pickup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isNoContent());
        verify(orderService).schedulePickup(1L, new SchedulePickupRequest(
                Instant.parse("2099-01-01T10:00:00Z"), "Village market gate"
        ), "farmer@example.com");
    }

    @Test
    void rejectsPastTime() throws Exception {
        mockMvc.perform(post("/api/orders/1/pickup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID.replace("2099-01-01", "2000-01-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.pickupAt").isNotEmpty());
        verifyNoInteractions(orderService);
    }

    @Test
    void rejectsBlankInstructions() throws Exception {
        mockMvc.perform(post("/api/orders/1/pickup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID.replace("Village market gate", "   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.pickupInstructions").isNotEmpty());
        verifyNoInteractions(orderService);
    }

    @Test
    void rejectsMissingFieldsAndExcessiveInstructions() throws Exception {
        mockMvc.perform(post("/api/orders/1/pickup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.pickupAt").isNotEmpty());
        mockMvc.perform(post("/api/orders/1/pickup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID.replace("Village market gate", "x".repeat(501))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(orderService);
    }

    @Test
    void mutationEndpointsRequireCsrf() throws Exception {
        mockMvc.perform(post("/api/orders/1/pickup")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/orders/1/complete"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(orderService);
    }

    @Test
    @WithAnonymousUser
    void anonymousVisitorCannotScheduleOrComplete() throws Exception {
        mockMvc.perform(post("/api/orders/1/pickup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/api/orders/1/complete").with(csrf()))
                .andExpect(status().is3xxRedirection());
        verifyNoInteractions(orderService);
    }

    @Test
    @WithMockUser(username = "buyer@example.com", roles = "USER")
    void completionUsesAuthenticatedBuyer() throws Exception {
        mockMvc.perform(post("/api/orders/1/complete").with(csrf()))
                .andExpect(status().isNoContent());
        verify(orderService).completeOrder(1L, "buyer@example.com");
    }
}
