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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import org.springframework.security.test.context.support.WithAnonymousUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import java.math.BigDecimal;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@Import({
        SecurityConfig.class,
        PasswordConfig.class,
        GlobalExceptionHandler.class
})
@WithMockUser(username = "buyer@example.com", roles = "USER")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private AppUserRepository userRepository;

    @Test
    void createsOrderUsingAuthenticatedBuyer() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                5L, new BigDecimal("10.00")
        );

        when(orderService.createOrder(request, "buyer@example.com"))
                .thenReturn(new OrderResponse(
                        1L,
                        5L,
                        new BigDecimal("10.00"),
                        new BigDecimal("24.00"),
                        new BigDecimal("240.0000"),
                        OrderStatus.PENDING,
                        null,
                        null
                ));

        mockMvc.perform(post("/api/orders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "listingId": 5,
                                  "quantityKg": 10.00
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(240));

        verify(orderService).createOrder(request, "buyer@example.com");
    }

    @Test
    void cancelsOrderUsingAuthenticatedBuyer() throws Exception {
        mockMvc.perform(post("/api/orders/1/cancel")
                        .with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(orderService).cancelOrder(1L, "buyer@example.com");
    }

    @Test
    void rejectsZeroQuantity() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "listingId": 5,
                                  "quantityKg": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.quantityKg").isNotEmpty());

        verifyNoInteractions(orderService);
    }
    @Test
    void myOrdersUsesTheSignedInBuyer() throws Exception {
        when(orderService.getMyOrders("buyer@example.com", 0, 10))
                .thenReturn(new OrderPageResponse(
                        List.of(), 0, 10, 0L, 0
                ));

        mockMvc.perform(get("/api/me/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        verify(orderService).getMyOrders(
                "buyer@example.com", 0, 10
        );
    }

    @Test
    @WithMockUser(username = "farmer@example.com", roles = "USER")
    void receivedOrdersUsesTheSignedInFarmer() throws Exception {
        when(orderService.getReceivedOrders("farmer@example.com", 0, 10))
                .thenReturn(new OrderPageResponse(
                        List.of(), 0, 10, 0L, 0
                ));

        mockMvc.perform(get("/api/me/received-orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        verify(orderService).getReceivedOrders(
                "farmer@example.com", 0, 10
        );
    }

    @Test
    @WithAnonymousUser
    void anonymousVisitorCannotReadPrivateOrders() throws Exception {
        mockMvc.perform(get("/api/me/orders"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/api/me/received-orders"))
                .andExpect(status().is3xxRedirection());

        verifyNoInteractions(orderService);
    }
    @Test
    void rejectsRequestWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "listingId": 5,
                                  "quantityKg": 10.00
                                }
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(orderService);
    }
}