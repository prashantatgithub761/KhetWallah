package com.prashant.KhetWallah.listing;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import com.prashant.KhetWallah.config.PasswordConfig;
import com.prashant.KhetWallah.config.SecurityConfig;
import com.prashant.KhetWallah.user.AppUserRepository;
import org.springframework.security.test.context.support.WithMockUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import com.prashant.KhetWallah.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ListingController.class)
@Import({
        GlobalExceptionHandler.class,
        SecurityConfig.class,
        PasswordConfig.class
})
@WithMockUser(username = "farmer@example.com", roles = "USER")
class ListingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListingService listingService;

    @MockitoBean
    private AppUserRepository appUserRepository;



    @Test
    void rejectsNegativePriceBeforeCallingService() throws Exception {
        String request = """
                {
                  "produceName": "Onion",
                  "farmerName": "Amit",
                  "availableQuantityKg": 75.50,
                  "pricePerKg": -22.00,
                  "pickupArea": "Meerut"
                }
                """;

        mockMvc.perform(
                        post("/api/listings")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(
                        jsonPath("$.fieldErrors.pricePerKg").isNotEmpty()
                );

        verifyNoInteractions(listingService);
    }
    @Test
    void closesListingUsingAuthenticatedAccount() throws Exception {
        mockMvc.perform(
                        post("/api/listings/3/close")
                                .with(csrf())
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(listingService).closeListing(
                3L,
                "farmer@example.com"
        );
    }

    @Test
    void returnsForbiddenWhenServiceRejectsClosing() throws Exception {
        doThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "You can only close your own listings"
        )).when(listingService).closeListing(
                3L,
                "farmer@example.com"
        );

        mockMvc.perform(
                        post("/api/listings/3/close")
                                .with(csrf())
                )
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.message")
                                .value("You can only close your own listings")
                );
    }

    @Test
    void rejectsZeroPageSizeBeforeCallingService() throws Exception {
        mockMvc.perform(
                        get("/api/listings")
                                .param("size", "0")
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.message")
                                .value("Size must be between 1 and 50")
                );

        verifyNoInteractions(listingService);
    }

    @Test
    void returnsHelpfulErrorWhenListingDoesNotExist() throws Exception {
        when(listingService.getListingById(999L))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/listings/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(
                        jsonPath("$.message").value("Listing not found")
                );
    }
    @Test
    void createsListingWhenRequestIsValid() throws Exception {
        CreateListingRequest expectedRequest = new CreateListingRequest(
                "Onion",

                new BigDecimal("75.50"),
                new BigDecimal("22.00"),
                "Meerut"
        );

        ProduceListing savedListing = new ProduceListing(
                42L,
                "Onion",
                "Amit",
                new BigDecimal("75.50"),
                new BigDecimal("22.00"),
                "Meerut",
                ListingStatus.ACTIVE
        );

        when(listingService.createListing(
                expectedRequest,
                "farmer@example.com"
        )).thenReturn(savedListing);

        String requestJson = """
            {
              "produceName": "Onion",
              "availableQuantityKg": 75.50,
              "pricePerKg": 22.00,
              "pickupArea": "Meerut"
            }
            """;

        mockMvc.perform(
                        post("/api/listings")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/listings/42"))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.produceName").value("Onion"))
                .andExpect(jsonPath("$.farmerName").value("Amit"))
                .andExpect(jsonPath("$.availableQuantityKg").value(75.50))
                .andExpect(jsonPath("$.pricePerKg").value(22.00))
                .andExpect(jsonPath("$.pickupArea").value("Meerut"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(listingService).createListing(
                expectedRequest,
                "farmer@example.com"
        );
    }
    @Test
    void updatesListingUsingAuthenticatedAccount() throws Exception {
        UpdateListingRequest expectedRequest = new UpdateListingRequest(
                new BigDecimal("30.00"),
                new BigDecimal("20.00"),
                "Jani"
        );

        ProduceListing updatedListing = new ProduceListing(
                3L,
                "Tomato",
                "Demo Farmer",
                new BigDecimal("30.00"),
                new BigDecimal("20.00"),
                "Jani",
                ListingStatus.ACTIVE
        );

        when(listingService.updateListing(
                3L,
                expectedRequest,
                "farmer@example.com"
        )).thenReturn(updatedListing);

        String requestJson = """
            {
              "availableQuantityKg": 30.00,
              "pricePerKg": 20.00,
              "pickupArea": "Jani"
            }
            """;

        mockMvc.perform(
                        put("/api/listings/3/details")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.availableQuantityKg").value(30.00))
                .andExpect(jsonPath("$.pricePerKg").value(20.00))
                .andExpect(jsonPath("$.pickupArea").value("Jani"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        verify(listingService).updateListing(
                3L,
                expectedRequest,
                "farmer@example.com"
        );
    }
    @Test
    void rejectsNegativePriceWhenUpdatingListing() throws Exception {
        String requestJson = """
            {
              "availableQuantityKg": 30.00,
              "pricePerKg": -20.00,
              "pickupArea": "Jani"
            }
            """;

        mockMvc.perform(
                        put("/api/listings/3/details")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.pricePerKg").isNotEmpty());

        verifyNoInteractions(listingService);
    }
}