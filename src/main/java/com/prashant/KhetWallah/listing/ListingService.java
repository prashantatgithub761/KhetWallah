package com.prashant.KhetWallah.listing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.prashant.KhetWallah.user.AppUser;
import com.prashant.KhetWallah.user.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class ListingService {

    private final ListingRepository listingRepository;
    private final AppUserRepository appUserRepository;

    public ListingService(
            ListingRepository listingRepository,
            AppUserRepository appUserRepository
    ) {
        this.listingRepository = listingRepository;
        this.appUserRepository = appUserRepository;
    }

    public ListingPageResponse getAllListings(
            String produceName,
            String area,
            int page,
            int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, size);

        Page<ListingEntity> result = listingRepository
                .findByStatusAndProduceNameContainingIgnoreCaseAndPickupAreaContainingIgnoreCaseOrderByIdAsc(
                        ListingStatus.ACTIVE,
                        produceName.strip(),
                        area.strip(),
                        pageRequest
                );

        List<ProduceListing> content = new ArrayList<>();

        for (ListingEntity entity : result.getContent()) {
            content.add(toResponse(entity));
        }

        return new ListingPageResponse(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    public Optional<ProduceListing> getListingById(Long id) {
        return listingRepository
                .findByIdAndStatus(id, ListingStatus.ACTIVE)
                .map(this::toResponse);
    }

    @Transactional
    public ProduceListing createListing(
            CreateListingRequest request,
            String authenticatedEmail
    ) {
        AppUser owner = appUserRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "Account no longer exists"
                        )
                );

        ListingEntity entity = new ListingEntity(
                request.produceName().strip(),
                owner,
                request.availableQuantityKg(),
                request.pricePerKg(),
                request.pickupArea().strip()
        );

        ListingEntity saved = listingRepository.save(entity);

        return toResponse(saved);
    }

    private ProduceListing toResponse(ListingEntity entity) {
        return new ProduceListing(
                entity.getId(),
                entity.getProduceName(),
                entity.getFarmerName(),
                entity.getAvailableQuantityKg(),
                entity.getPricePerKg(),
                entity.getPickupArea(),
                entity.getStatus()
        );
    }
    public ListingPageResponse getMyListings(
            String authenticatedEmail,
            int page,
            int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, size);

        Page<ListingEntity> result =
                listingRepository.findByOwner_EmailOrderByIdDesc(
                        authenticatedEmail,
                        pageRequest
                );

        List<ProduceListing> content = new ArrayList<>();

        for (ListingEntity entity : result.getContent()) {
            content.add(toResponse(entity));
        }

        return new ListingPageResponse(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional
    public ProduceListing updateListing(
            Long listingId,
            UpdateListingRequest request,
            String authenticatedEmail
    ) {
        ListingEntity listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Listing not found"
                ));

        AppUser owner = listing.getOwner();

        if (owner == null
                || !owner.getEmail().equals(authenticatedEmail)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only edit your own listings"
            );
        }
        if (listing.getStatus() == ListingStatus.CLOSED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Closed listings cannot be edited"
            );
        }

        listing.updateDetails(
                request.availableQuantityKg(),
                request.pricePerKg(),
                request.pickupArea().strip()
        );

        return toResponse(listing);
    }
    @Transactional
    public void closeListing(
            Long listingId,
            String authenticatedEmail
    ) {
        ListingEntity listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Listing not found"
                ));

        AppUser owner = listing.getOwner();

        if (owner == null
                || !owner.getEmail().equals(authenticatedEmail)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only close your own listings"
            );
        }

        listing.close();
    }
}