package com.prashant.KhetWallah.listing;
import java.security.Principal;

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
            int size
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

        return listingService.getAllListings(
                produceName,
                area,
                page,
                size
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
    @PostMapping("/api/listings/{id}/close")
    public ResponseEntity<Void> closeListing(
            @PathVariable("id") Long id,
            Principal principal
    ) {
        listingService.closeListing(id, principal.getName());

        return ResponseEntity.noContent().build();
    }
}