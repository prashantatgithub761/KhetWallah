package com.prashant.KhetWallah.listing;
import java.security.Principal;
import java.math.BigDecimal;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;


@RestController
public class ListingController {

    private final ListingService listingService;

    public ListingController(ListingService listingService) {
        this.listingService = listingService;
    }


    @GetMapping("/api/listings")
    public ListingPageResponse getAllListings(
            @RequestParam(name = "produceName", defaultValue = "")
            String produceName,

            @RequestParam(name = "area", defaultValue = "")
            String area,

            @RequestParam(name = "page", defaultValue = "0")
            int page,

            @RequestParam(name = "size", defaultValue = "10")
            int size,
            @RequestParam(name = "minPrice", required = false) BigDecimal minPrice,
            @RequestParam(name = "maxPrice", required = false) BigDecimal maxPrice,
            @RequestParam(name = "inStockOnly", defaultValue = "false") boolean inStockOnly,
            @RequestParam(name = "sort", defaultValue = "oldest") String sort,
            @RequestParam(name = "district", required = false) String district,
            @RequestParam(name = "pinCode", required = false) String pinCode,
            @RequestParam(name = "lat", required = false) BigDecimal lat,
            @RequestParam(name = "lng", required = false) BigDecimal lng,
            @RequestParam(name = "radiusKm", required = false) BigDecimal radiusKm
    ) {
        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be zero or greater"
            );
        }

        if (size < 1 || size > 50) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Size must be between 1 and 50"
            );
        }

        if (district != null || pinCode != null || lat != null || lng != null || radiusKm != null)
            return listingService.getAllListings(produceName, area, page, size, minPrice, maxPrice,
                    inStockOnly, sort, district, pinCode, lat, lng, radiusKm);
        return listingService.getAllListings(
                produceName,
                area,
                page,
                size,
                minPrice,
                maxPrice,
                inStockOnly,
                sort
        );
    }
    @GetMapping("/api/listings/{id}")
    public ResponseEntity<ProduceListing> getListingById(
            @PathVariable("id") Long id
    ) {
        ProduceListing listing = listingService.getListingById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Listing not found"
                ));

        return ResponseEntity.ok(listing);
    }
    @PostMapping("/api/listings")
    public ResponseEntity<ProduceListing> createListing(
            @Valid @RequestBody CreateListingRequest request,
            Principal principal
    ) {
        ProduceListing created = listingService.createListing(
                request,
                principal.getName()
        );

        return ResponseEntity
                .created(URI.create("/api/listings/" + created.getId()))
                .body(created);
    }
    @PutMapping("/api/listings/{id}/details")
    public ProduceListing updateListing(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateListingRequest request,
            Principal principal
    ) {
        return listingService.updateListing(
                id,
                request,
                principal.getName()
        );
    }
    @PostMapping({"/api/listings/{id}/pause", "/api/listings/{id}/resume", "/api/listings/{id}/sold-out"})
    public ProduceListing availability(@PathVariable("id") Long id, Principal principal,
            jakarta.servlet.http.HttpServletRequest request) {
        String path = request.getRequestURI();
        return listingService.changeAvailability(id, principal.getName(), path.substring(path.lastIndexOf('/') + 1));
    }
    @PostMapping("/api/listings/{id}/close")
    public ResponseEntity<Void> closeListing(
            @PathVariable("id") Long id,
            Principal principal
    ) {
        listingService.closeListing(id, principal.getName());

        return ResponseEntity.noContent().build();
    }
}
