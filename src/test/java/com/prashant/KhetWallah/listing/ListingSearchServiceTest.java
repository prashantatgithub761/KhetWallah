package com.prashant.KhetWallah.listing;

import com.prashant.KhetWallah.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ListingSearchServiceTest {
    private final ListingRepository listings = mock(ListingRepository.class);
    private final ListingService service = new ListingService(listings, mock(AppUserRepository.class));

    @Test
    void rejectsInvalidPriceBoundsWithoutQueryingTheDatabase() {
        for (BigDecimal price : new BigDecimal[]{new BigDecimal("-1"),
                new BigDecimal("0.001"), new BigDecimal("100000000")}) {
            assertEquals(400, assertThrows(ResponseStatusException.class,
                    () -> service.getAllListings("", "", 0, 6, price, null, false, "oldest"))
                    .getStatusCode().value());
        }
        assertThrows(ResponseStatusException.class,
                () -> service.getAllListings("", "", 0, 6,
                        new BigDecimal("40"), new BigDecimal("20"), false, "oldest"));
        verifyNoInteractions(listings);
    }

    @Test
    void doesNotAcceptArbitraryDatabasePropertiesAsSortKeys() {
        assertThrows(ResponseStatusException.class,
                () -> service.getAllListings("", "", 0, 6, null, null, false, "owner.passwordHash"));
        verifyNoInteractions(listings);
    }

    @Test
    void sortsAcrossPagesAndUsesIdToBreakEqualPriceTies() {
        when(listings.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        service.getAllListings("Tomato", "Meerut", 2, 6,
                BigDecimal.ZERO, new BigDecimal("25.50"), true, "price_desc");
        var paging = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(listings).findAll(any(Specification.class), paging.capture());
        assertEquals(2, paging.getValue().getPageNumber());
        assertEquals(6, paging.getValue().getPageSize());
        assertEquals(Sort.Direction.DESC, paging.getValue().getSort().getOrderFor("pricePerKg").getDirection());
        assertEquals(Sort.Direction.ASC, paging.getValue().getSort().getOrderFor("id").getDirection());
    }
}
