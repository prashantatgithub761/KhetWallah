package com.prashant.KhetWallah.review;

import com.prashant.KhetWallah.order.OrderEntity;
import com.prashant.KhetWallah.user.AppUser;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="order_reviews",uniqueConstraints=@UniqueConstraint(name="uk_review_order",columnNames="order_id"),indexes=@Index(name="idx_review_listing",columnList="listing_id,hidden,id"))
public class OrderReview {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="order_id",nullable=false) private OrderEntity order;
    @Column(name="listing_id",nullable=false) private Long listingId;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="buyer_id",nullable=false) private AppUser buyer;
    @Column(nullable=false) private int rating;
    @Column(nullable=false,length=1000) private String comment;
    @Column(nullable=false) private boolean hidden;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    protected OrderReview(){}
    public OrderReview(OrderEntity order,int rating,String comment){
        if(rating<1 || rating>5) throw new IllegalArgumentException("Rating must be 1–5");
        String clean=comment==null?"":comment.strip();
        if(clean.isBlank() || clean.length()>1000) throw new IllegalArgumentException("Review must contain 1–1000 characters");
        this.order=order;this.listingId=order.getListing().getId();this.buyer=order.getBuyer();this.rating=rating;this.comment=clean;this.createdAt=Instant.now();
    }
    public Long getId(){return id;}
    public OrderEntity getOrder(){return order;}
    public Long getListingId(){return listingId;}
    public AppUser getBuyer(){return buyer;}
    public int getRating(){return rating;}
    public String getComment(){return comment;}
    public boolean isHidden(){return hidden;}
    public Instant getCreatedAt(){return createdAt;}
    public void setHidden(boolean hidden){this.hidden=hidden;}
}
