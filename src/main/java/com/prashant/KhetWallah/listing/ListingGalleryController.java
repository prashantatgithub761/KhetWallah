package com.prashant.KhetWallah.listing;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.security.Principal;
import java.util.List;
@RestController
public class ListingGalleryController {
    private final ListingGalleryService gallery;
    public ListingGalleryController(ListingGalleryService gallery) { this.gallery=gallery; }
    @GetMapping("/api/listings/{id}/photos") public List<ListingGalleryService.Photo> list(@PathVariable Long id) { return gallery.list(id); }
    @GetMapping("/api/listings/{id}/photos/{photoId}") public ResponseEntity<byte[]> read(@PathVariable Long id,@PathVariable Long photoId) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noStore()).body(gallery.read(id,photoId));
    }
    @PostMapping(value="/api/listings/{id}/photos",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> add(@PathVariable Long id,@RequestParam("photo") MultipartFile photo,Principal principal) {
        gallery.add(id,photo,principal.getName()); return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/api/listings/{id}/photos/{photoId}") public ResponseEntity<Void> remove(@PathVariable Long id,@PathVariable Long photoId,Principal principal) {
        gallery.remove(id,photoId,principal.getName()); return ResponseEntity.noContent().build();
    }
}
