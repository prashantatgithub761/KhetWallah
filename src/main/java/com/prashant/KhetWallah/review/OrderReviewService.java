package com.prashant.KhetWallah.review;

import com.prashant.KhetWallah.admin.*;
import com.prashant.KhetWallah.listing.*;
import com.prashant.KhetWallah.order.*;
import com.prashant.KhetWallah.user.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

@Service
public class OrderReviewService {
    private final OrderReviewRepository reviews;
    private final OrderRepository orders;
    private final ListingRepository listings;
    private final AppUserRepository users;
    private final AdminActionRepository actions;
    public OrderReviewService(OrderReviewRepository reviews,OrderRepository orders,ListingRepository listings,AppUserRepository users,AdminActionRepository actions){this.reviews=reviews;this.orders=orders;this.listings=listings;this.users=users;this.actions=actions;}
    @Transactional(readOnly=true)
    public ReviewPage publicReviews(Long listingId,int page,int size){
        ListingEntity listing=listings.findById(listingId).filter(ListingEntity::isPubliclyAvailable).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Listing not found"));
        Page<ReviewView> result=reviews.findByListingIdAndHiddenFalseOrderByIdDesc(listing.getId(),pageable(page,size)).map(ReviewView::from);
        Double average=reviews.averageVisible(listing.getId());
        return new ReviewPage(result.getContent(),result.getNumber(),result.getTotalPages(),reviews.countByListingIdAndHiddenFalse(listing.getId()),average);
    }
    @Transactional(readOnly=true)
    public ReviewEligibility eligibility(Long orderId,String email){
        OrderEntity order=ownedOrder(orders.findById(orderId),email);
        OrderReview existing=reviews.findByOrder_Id(orderId).orElse(null);
        return new ReviewEligibility(order.getStatus()==OrderStatus.COMPLETED && existing==null,existing==null?null:ReviewView.from(existing));
    }
    @Transactional
    public ReviewView create(Long orderId,CreateReviewRequest request,String email){
        OrderEntity order=ownedOrder(orders.findByIdForUpdate(orderId),email);
        if(order.getStatus()!=OrderStatus.COMPLETED) throw new ResponseStatusException(HttpStatus.CONFLICT,"Reviews are available after collection is completed");
        if(reviews.findByOrder_Id(orderId).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT,"You have already reviewed this order");
        if(request.rating()<1 || request.rating()>5 || request.comment()==null || request.comment().isBlank() || request.comment().strip().length()>1000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose 1–5 stars and write 1–1000 characters");
        return ReviewView.from(reviews.save(new OrderReview(order,request.rating(),request.comment())));
    }
    private OrderEntity ownedOrder(Optional<OrderEntity> candidate,String email){
        AppUser user=users.findByEmail(email).filter(u->!u.isSuspended()).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Sign in to your account"));
        return candidate.filter(o->Objects.equals(o.getBuyer().getId(),user.getId())).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found"));
    }
    @Transactional(readOnly=true)
    public Page<AdminReviewView> adminList(String email,int page,int size){requireAdmin(email);return reviews.findAllByOrderByIdDesc(pageable(page,size)).map(AdminReviewView::from);}
    @Transactional
    public AdminReviewView moderate(Long id,boolean hidden,String reason,String email){
        requireAdmin(email);
        String clean=reason==null?"":reason.strip();
        if(clean.isBlank() || clean.length()>500) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Reason must contain 1–500 characters");
        OrderReview review=reviews.findByIdForUpdate(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Review not found"));
        if(review.isHidden()==hidden) throw new ResponseStatusException(HttpStatus.CONFLICT,hidden?"Review is already hidden":"Review is already visible");
        review.setHidden(hidden);
        actions.save(new AdminAction(email,hidden?"HIDE_REVIEW":"RESTORE_REVIEW","REVIEW",id,clean));
        return AdminReviewView.from(review);
    }
    private void requireAdmin(String email){users.findByEmail(email).filter(u->u.getRole()==UserRole.ADMIN && !u.isSuspended()).orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN,"Administrator access required"));}
    private static PageRequest pageable(int page,int size){if(page<0||size<1||size>50)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Use page >= 0 and size 1–50");return PageRequest.of(page,size);}
    public record ReviewView(Long id,int rating,String comment,String buyerName,Instant createdAt){static ReviewView from(OrderReview review){return new ReviewView(review.getId(),review.getRating(),review.getComment(),review.getBuyer().getDisplayName(),review.getCreatedAt());}}
    public record ReviewPage(List<ReviewView> content,int page,int totalPages,long reviewCount,Double averageRating){}
    public record ReviewEligibility(boolean eligible,ReviewView review){}
    public record AdminReviewView(Long id,Long orderId,Long listingId,int rating,String comment,String buyerName,boolean hidden,Instant createdAt){static AdminReviewView from(OrderReview review){return new AdminReviewView(review.getId(),review.getOrder().getId(),review.getListingId(),review.getRating(),review.getComment(),review.getBuyer().getDisplayName(),review.isHidden(),review.getCreatedAt());}}
}
