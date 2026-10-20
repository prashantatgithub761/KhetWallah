package com.prashant.KhetWallah.listing;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ListingPhotoRepository extends JpaRepository<ListingPhoto,Long> {
    List<ListingPhoto> findByListingIdOrderByIdAsc(Long listingId);
    long countByListingId(Long listingId);
}
