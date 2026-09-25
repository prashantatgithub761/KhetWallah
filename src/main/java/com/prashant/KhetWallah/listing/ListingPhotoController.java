package com.prashant.KhetWallah.listing;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;

@RestController
public class ListingPhotoController {

    private final ListingPhotoService photos;

    public ListingPhotoController(ListingPhotoService photos) {
        this.photos = photos;
    }

    @PostMapping(
            value = "/api/listings/{id}/photo",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<Void> upload(
            @PathVariable("id") Long id,
            @RequestParam("photo") MultipartFile photo,
            Principal principal
    ) {
        photos.upload(id, photo, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/listings/{id}/photo")
    public ResponseEntity<byte[]> read(
            @PathVariable("id") Long id
    ) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.noStore())
                .body(photos.read(id));
    }
}