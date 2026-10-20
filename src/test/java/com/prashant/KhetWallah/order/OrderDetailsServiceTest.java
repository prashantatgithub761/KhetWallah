package com.prashant.KhetWallah.order;

import com.prashant.KhetWallah.listing.*;
import com.prashant.KhetWallah.user.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderDetailsServiceTest {
    @Test
    void onlyParticipantsCanReadAnOrderAndPriceRemainsTheAgreedPrice() {
        var farmer=new AppUser("Farmer","farmer@example.test","secret-hash");
        var buyer=new AppUser("Buyer","buyer@example.test","secret-hash");
        var listing=new ListingEntity("Tomato",farmer,new BigDecimal("50"),new BigDecimal("20"),"Meerut");
        ReflectionTestUtils.setField(listing,"id",9L);
        listing.changePhoto("test.jpg");
        var order=new OrderEntity(listing,buyer,new BigDecimal("3"));
        ReflectionTestUtils.setField(order,"id",12L);
        var orders=mock(OrderRepository.class);
        when(orders.findById(12L)).thenReturn(Optional.of(order));
        var service=new OrderService(orders,mock(ListingRepository.class),mock(AppUserRepository.class),mock(OrderEmailNotifications.class));
        listing.updateDetails(new BigDecimal("40"),new BigDecimal("99"),"Jani");
        var response=service.getOrderForParticipant(12L,buyer.getEmail());
        assertEquals("Tomato",response.produceName());
        assertEquals("Farmer",response.farmerName());
        assertEquals("Buyer",response.buyerName());
        assertEquals("/api/listings/9/photo",response.photoUrl());
        assertEquals("Jani",response.pickupArea());
        assertEquals(new BigDecimal("20"),response.pricePerKg());
        assertEquals(new BigDecimal("60"),response.totalAmount());
        assertEquals(response,service.getOrderForParticipant(12L,farmer.getEmail()));
        var error=assertThrows(ResponseStatusException.class,
                ()->service.getOrderForParticipant(12L,"outsider@example.test"));
        assertEquals(404,error.getStatusCode().value());
    }
}
