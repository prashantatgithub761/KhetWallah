package com.prashant.KhetWallah.dashboard;

import com.prashant.KhetWallah.listing.*;
import com.prashant.KhetWallah.order.*;
import com.prashant.KhetWallah.user.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Explicit local test database only; all inserted rows roll back. */
@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "app.email.enabled=false"})
@Transactional
class DashboardIntegrationTest {
    @Autowired DashboardService dashboard;
    @Autowired AppUserRepository users;
    @Autowired ListingRepository listings;
    @Autowired OrderRepository orders;

    @Test
    void upcomingPickupsArePrivateFutureAcceptedAndBoundedWithRealCropDetails() {
        String marker = UUID.randomUUID().toString();
        AppUser farmer = user("Farmer", marker+"f@example.test");
        AppUser buyer = user("Buyer", marker+"b@example.test");
        AppUser otherFarmer = user("Other", marker+"o@example.test");
        var crop = crop(farmer);
        Instant start = Instant.now().plusSeconds(7200).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        for (int i=0; i<7; i++) accepted(crop,buyer,start.plusSeconds(i*60));
        var past = accepted(crop,buyer,start);
        ReflectionTestUtils.setField(past,"pickupAt",Instant.now().minusSeconds(7200));
        orders.saveAndFlush(past);
        orders.saveAndFlush(new OrderEntity(crop,buyer,BigDecimal.ONE));
        accepted(crop(otherFarmer),otherFarmer,start); // Unrelated account's record.
        var summary = dashboard.forAccount(farmer.getEmail());
        assertEquals(1,summary.activeListings());
        assertEquals(1,summary.pendingReceivedOrders());
        assertEquals(7,summary.upcomingPickups());
        assertEquals(5,summary.pickups().size());
        assertEquals(start,summary.pickups().getFirst().pickupAt());
        assertTrue(summary.pickups().stream().allMatch(p -> p.role().equals("FARMER")
                && p.farmerName().equals("Farmer") && p.produceName().equals("Tomato")));
        assertEquals(7,dashboard.forAccount(buyer.getEmail()).upcomingPickups());
        assertTrue(dashboard.forAccount(buyer.getEmail()).pickups().stream()
                .allMatch(p->p.role().equals("BUYER")));
    }
    private AppUser user(String name,String email){return users.saveAndFlush(new AppUser(name,email,"unused-test-hash"));}
    private ListingEntity crop(AppUser owner){return listings.saveAndFlush(new ListingEntity("Tomato",owner,new BigDecimal("500"),new BigDecimal("20"),"Meerut"));}
    private OrderEntity accepted(ListingEntity crop,AppUser buyer,Instant time){
        var order=new OrderEntity(crop,buyer,BigDecimal.ONE);
        order.accept();order.schedulePickup(time,"Test pickup point");
        return orders.saveAndFlush(order);
    }
}
