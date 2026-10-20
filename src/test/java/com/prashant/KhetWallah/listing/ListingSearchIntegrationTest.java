package com.prashant.KhetWallah.listing;

import com.prashant.KhetWallah.user.AppUser;
import com.prashant.KhetWallah.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Run against an explicitly configured test database; all inserted rows roll back. */
@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "app.email.enabled=false"})
@Transactional
class ListingSearchIntegrationTest {
    @Autowired ListingService service;
    @Autowired ListingRepository listings;
    @Autowired AppUserRepository users;

    @Test
    void combinesFiltersBeforePaginationAndExcludesClosedHiddenAndEmptyStock() {
        String marker = "Search-" + UUID.randomUUID();
        AppUser farmer = users.saveAndFlush(new AppUser("Search test farmer", marker + "@example.test", "unused-test-hash"));
        ListingEntity cheap = save(marker, farmer, "10.00", "5", "Meerut");
        ListingEntity firstTie = save(marker, farmer, "20.00", "5", "Meerut");
        ListingEntity secondTie = save(marker, farmer, "20.00", "5", "Meerut");
        ListingEntity expensive = save(marker, farmer, "30.00", "5", "Meerut");
        save(marker, farmer, "15.00", "0", "Meerut");
        ListingEntity otherArea = save(marker, farmer, "15.00", "5", "Delhi");
        ListingEntity closed = save(marker, farmer, "15.00", "5", "Meerut");
        closed.close();
        ListingEntity hidden = save(marker, farmer, "15.00", "5", "Meerut");
        hidden.hide();
        listings.flush();

        ListingPageResponse page = service.getAllListings(marker.toLowerCase(), "meer", 0, 1,
                new BigDecimal("20.00"), new BigDecimal("30.00"), true, "price_asc");
        assertEquals(3, page.totalElements());
        assertEquals(firstTie.getId(), page.content().getFirst().getId());
        assertEquals(secondTie.getId(), service.getAllListings(marker, "Meerut", 1, 1,
                new BigDecimal("20.00"), new BigDecimal("30.00"), true, "price_asc")
                .content().getFirst().getId());
        assertEquals(expensive.getId(), service.getAllListings(marker, "Meerut", 0, 6,
                null, null, true, "price_desc").content().getFirst().getId());
        assertEquals(cheap.getId(), service.getAllListings(marker, "Meerut", 0, 6,
                null, null, true, "oldest").content().getFirst().getId());
        ListingPageResponse allActive = service.getAllListings(marker, "", 0, 10,
                null, null, false, "newest");
        assertEquals(6, allActive.totalElements());
        assertEquals(otherArea.getId(), allActive.content().getFirst().getId());
    }

    @Test
    void treatsSqlWildcardsAsLiteralCropText() {
        String marker = "Literal-" + UUID.randomUUID();
        AppUser farmer = users.saveAndFlush(new AppUser("Literal test farmer", marker + "@example.test", "unused-test-hash"));
        save(marker + "%_crop", farmer, "10", "1", "Meerut");
        save(marker + "OTHERcrop", farmer, "10", "1", "Meerut");
        assertEquals(1, service.getAllListings(marker + "%_", "", 0, 6,
                null, null, false, "oldest").totalElements());
    }

    private ListingEntity save(String crop, AppUser farmer, String price, String stock, String area) {
        return listings.saveAndFlush(new ListingEntity(crop, farmer,
                new BigDecimal(stock), new BigDecimal(price), area));
    }
}
