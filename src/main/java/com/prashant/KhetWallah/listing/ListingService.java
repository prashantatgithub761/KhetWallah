package com.prashant.KhetWallah.listing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import java.util.Locale;
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
        return getAllListings(produceName, area, page, size, null, null, false, "oldest");
    }

    public ListingPageResponse getAllListings(
            String produceName, String area, int page, int size,
            BigDecimal minPrice, BigDecimal maxPrice, boolean inStockOnly, String sort
    ) {
        return getAllListings(produceName, area, page, size, minPrice, maxPrice, inStockOnly, sort,
                null, null, null, null, null);
    }

    public ListingPageResponse getAllListings(String produceName, String area, int page, int size,
            BigDecimal minPrice, BigDecimal maxPrice, boolean inStockOnly, String sort,
            String district, String pinCode, BigDecimal latitude, BigDecimal longitude, BigDecimal radiusKm) {
        validateDiscovery(district, pinCode, latitude, longitude, null);
        if ((latitude == null) != (radiusKm == null) || radiusKm != null
                && (radiusKm.signum() <= 0 || radiusKm.compareTo(new BigDecimal("200")) > 0)) {
            throw badSearch("Choose a location and a radius between 0 and 200 km");
        }
        String crop = produceName == null ? "" : produceName.strip();
        String pickup = area == null ? "" : area.strip();
        if (crop.length() > 100 || pickup.length() > 150) {
            throw badSearch("Use at most 100 characters for a crop and 150 for an area");
        }
        if (page < 0 || size < 1 || size > 50) {
            throw badSearch("Page must be zero or greater; size must be between 1 and 50");
        }
        validatePrice(minPrice);
        validatePrice(maxPrice);
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw badSearch("Minimum price cannot be greater than maximum price");
        }
        Sort ordering = switch (sort == null ? "oldest" : sort) {
            case "oldest" -> Sort.by(Sort.Direction.ASC, "id");
            case "newest" -> Sort.by(Sort.Direction.DESC, "id");
            case "price_asc" -> Sort.by("pricePerKg").ascending().and(Sort.by("id"));
            case "price_desc" -> Sort.by("pricePerKg").descending().and(Sort.by("id"));
            default -> throw badSearch("Choose oldest, newest, price_asc or price_desc for sort");
        };
        Specification<ListingEntity> filters = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("status"), ListingStatus.ACTIVE));
            predicates.add(builder.or(builder.isNull(root.get("availableUntil")),
                    builder.greaterThanOrEqualTo(root.get("availableUntil"), java.time.LocalDate.now())));
            var owner = root.join("owner", jakarta.persistence.criteria.JoinType.LEFT);
            predicates.add(builder.or(builder.isNull(owner.get("id")), builder.isFalse(owner.get("suspended"))));
            if (district != null && !district.isBlank()) predicates.add(builder.like(builder.lower(root.get("district")), containsPattern(district.strip()), '\\'));
            if (pinCode != null && !pinCode.isBlank()) predicates.add(builder.equal(root.get("pinCode"), pinCode.strip()));
            if (latitude != null) {
                // Haversine remains stable for close points and clamp avoids rounding errors near antipodes.
                var latRadians = builder.function("radians", Double.class, root.get("latitude"));
                var latDiff = builder.quot(builder.diff(latRadians, Math.toRadians(latitude.doubleValue())), 2d);
                var lngRadians = builder.function("radians", Double.class, root.get("longitude"));
                var lngDiff = builder.quot(builder.diff(lngRadians, Math.toRadians(longitude.doubleValue())), 2d);
                var sinLat = builder.function("sin", Double.class, latDiff);
                var sinLng = builder.function("sin", Double.class, lngDiff);
                var a = builder.sum(builder.prod(sinLat, sinLat), builder.prod(builder.prod(
                        builder.function("cos", Double.class, latRadians), Math.cos(Math.toRadians(latitude.doubleValue()))), builder.prod(sinLng, sinLng)));
                var clamped = builder.function("least", Double.class, builder.literal(1d), a);
                var distance = builder.prod(12742d, builder.function("asin", Double.class,
                        builder.function("sqrt", Double.class, clamped)));
                predicates.add(builder.isNotNull(root.get("latitude")));
                predicates.add(builder.isNotNull(root.get("longitude")));
                predicates.add(builder.le(distance, radiusKm.doubleValue()));
            }
            if (!crop.isEmpty()) {
                predicates.add(builder.like(builder.lower(root.get("produceName")),
                        containsPattern(crop), '\\'));
            }
            if (!pickup.isEmpty()) {
                predicates.add(builder.like(builder.lower(root.get("pickupArea")),
                        containsPattern(pickup), '\\'));
            }
            if (minPrice != null) predicates.add(builder.greaterThanOrEqualTo(root.get("pricePerKg"), minPrice));
            if (maxPrice != null) predicates.add(builder.lessThanOrEqualTo(root.get("pricePerKg"), maxPrice));
            if (inStockOnly) {
                predicates.add(builder.greaterThan(root.get("availableQuantityKg"), BigDecimal.ZERO));
                predicates.add(builder.greaterThanOrEqualTo(root.get("availableQuantityKg"), root.get("minimumOrderQuantityKg")));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        Page<ListingEntity> result = listingRepository.findAll(filters, PageRequest.of(page, size, ordering));

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

    private static String containsPattern(String text) {
        // A user's percent/underscore is literal crop text, not a SQL wildcard.
        return "%" + text.toLowerCase(Locale.ROOT).replace("\\", "\\\\")
                .replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private static void validatePrice(BigDecimal price) {
        if (price != null && (price.signum() < 0
                || price.compareTo(new BigDecimal("99999999.99")) > 0
                || price.stripTrailingZeros().scale() > 2)) {
            throw badSearch("Prices must be between 0 and 99999999.99, with at most two decimal places");
        }
    }

    private static ResponseStatusException badSearch(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public Optional<ProduceListing> getListingById(Long id) {
        return listingRepository
                .findByIdAndStatus(id, ListingStatus.ACTIVE)
                .filter(ListingEntity::isPubliclyAvailable)
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

        validateCropDetails(request.variety(), request.harvestDate(), request.minimumOrderQuantityKg(),
                request.packagingDetails(), request.availableQuantityKg());

        ListingEntity entity = new ListingEntity(
                request.produceName().strip(),
                owner,
                request.availableQuantityKg(),
                request.pricePerKg(),
                request.pickupArea().strip(), request.variety(), request.harvestDate(),
                request.minimumOrderQuantityKg(), request.packagingDetails()
        );

        validateDiscovery(request.district(), request.pinCode(), request.latitude(), request.longitude(), request.availableUntil());
        entity.updateDiscoveryDetails(request.district(), request.pinCode(), request.latitude(), request.longitude(),
                request.availableUntil(), false, false);
        ListingEntity saved = listingRepository.save(entity);

        return toResponse(saved);
    }

    private ProduceListing toResponse(ListingEntity entity) {
        ProduceListing response = new ProduceListing(
                entity.getId(),
                entity.getProduceName(),
                entity.getFarmerName(),
                entity.getAvailableQuantityKg(),
                entity.getPricePerKg(),
                entity.getPickupArea(),
                entity.getStatus(), entity.getVariety(), entity.getHarvestDate(),
                entity.getMinimumOrderQuantityKg(), entity.getPackagingDetails()
        );

        response.setDiscoveryDetails(entity.getOwner() == null ? null : entity.getOwner().getId(),
                entity.getDistrict(), entity.getPinCode(), entity.getLatitude(), entity.getLongitude(), entity.getAvailableUntil());
        if (entity.getPhotoFileName() != null) {
            response.setPhotoUrl(
                    "/api/listings/" + entity.getId() + "/photo"
            );
        }

        return response;
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
        ListingEntity listing = listingRepository.findByIdForUpdate(listingId)
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
        if (listing.getStatus() == ListingStatus.CLOSED || listing.getStatus() == ListingStatus.HIDDEN) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Hidden or closed listings cannot be edited"
            );
        }

        validateCropDetails(request.variety(), request.harvestDate(),
                request.minimumOrderQuantityKg() == null ? listing.getMinimumOrderQuantityKg() : request.minimumOrderQuantityKg(),
                request.packagingDetails(), request.availableQuantityKg());

        validateDiscovery(request.district(), request.pinCode(), request.latitude(), request.longitude(), request.availableUntil());
        listing.updateDiscoveryDetails(request.district(), request.pinCode(), request.latitude(), request.longitude(),
                request.availableUntil(), Boolean.TRUE.equals(request.clearLocation()), Boolean.TRUE.equals(request.clearAvailableUntil()));
        listing.updateDetails(
                request.availableQuantityKg(),
                request.pricePerKg(),
                request.pickupArea().strip()
        );

        listing.updateCropDetails(request.variety(), request.harvestDate(), request.minimumOrderQuantityKg(),
                request.packagingDetails(), Boolean.TRUE.equals(request.clearHarvestDate()));

        return toResponse(listing);
    }
    private static void validateCropDetails(String variety, java.time.LocalDate harvestDate,
                                            BigDecimal minimum, String packaging, BigDecimal available) {
        if (variety != null && variety.length() > 100 || packaging != null && packaging.length() > 300) {
            throw badSearch("Use at most 100 characters for variety and 300 for packaging");
        }
        if (harvestDate != null && harvestDate.isAfter(java.time.LocalDate.now())) {
            throw badSearch("Harvest date cannot be in the future");
        }
        BigDecimal effectiveMinimum = minimum == null ? new BigDecimal("0.01") : minimum;
        if (effectiveMinimum.signum() <= 0 || effectiveMinimum.stripTrailingZeros().scale() > 2
                || effectiveMinimum.compareTo(new BigDecimal("99999999.99")) > 0) {
            throw badSearch("Minimum order quantity must be positive with at most two decimal places");
        }
        if (available != null && available.signum() > 0 && effectiveMinimum.compareTo(available) > 0) {
            throw badSearch("Minimum order quantity cannot exceed the available quantity");
        }
    }

    private static void validateDiscovery(String district, String pinCode, BigDecimal latitude,
            BigDecimal longitude, java.time.LocalDate availableUntil) {
        if (district != null && district.length() > 100) throw badSearch("District must be at most 100 characters");
        if (pinCode != null && !pinCode.isBlank() && !pinCode.strip().matches("[1-9][0-9]{5}"))
            throw badSearch("Enter a valid six-digit Indian PIN code");
        if ((latitude == null) != (longitude == null)) throw badSearch("Provide both latitude and longitude");
        if (latitude != null && (latitude.compareTo(new BigDecimal("-90")) < 0 || latitude.compareTo(new BigDecimal("90")) > 0
                || longitude.compareTo(new BigDecimal("-180")) < 0 || longitude.compareTo(new BigDecimal("180")) > 0
                || latitude.stripTrailingZeros().scale() > 6 || longitude.stripTrailingZeros().scale() > 6))
            throw badSearch("Use valid coordinates with at most six decimal places");
        if (availableUntil != null && availableUntil.isBefore(java.time.LocalDate.now()))
            throw badSearch("Available-until date cannot be in the past");
    }

    @Transactional
    public ProduceListing changeAvailability(Long id, String email, String action) {
        ListingEntity listing = listingRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));
        if (listing.getOwner() == null || !listing.getOwner().getEmail().equals(email))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only change your own listings");
        try {
            switch (action) {
                case "pause" -> listing.pause();
                case "resume" -> listing.resume();
                case "sold-out" -> listing.markSoldOut();
                default -> throw badSearch("Choose pause, resume or sold-out");
            }
        } catch (IllegalStateException ex) { throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage()); }
        return toResponse(listing);
    }

    @Transactional
    public void closeListing(
            Long listingId,
            String authenticatedEmail
    ) {
        ListingEntity listing = listingRepository.findByIdForUpdate(listingId)
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

        if (listing.getStatus() == ListingStatus.HIDDEN) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Hidden listings cannot be closed"
            );
        }
        listing.close();
    }
}
