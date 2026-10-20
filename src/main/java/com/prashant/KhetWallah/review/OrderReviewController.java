package com.prashant.KhetWallah.review;

import com.prashant.KhetWallah.admin.AdminActionRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController
public class OrderReviewController {
    private final OrderReviewService service;
    public OrderReviewController(OrderReviewService service){this.service=service;}
    @GetMapping("/api/listings/{id}/reviews") public OrderReviewService.ReviewPage listing(@PathVariable Long id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="5")int size){return service.publicReviews(id,page,size);}
    @GetMapping("/api/orders/{id}/review") public OrderReviewService.ReviewEligibility own(@PathVariable Long id,Principal principal){return service.eligibility(id,principal.getName());}
    @PostMapping("/api/orders/{id}/review") @ResponseStatus(HttpStatus.CREATED) public OrderReviewService.ReviewView create(@PathVariable Long id,@Valid @RequestBody CreateReviewRequest request,Principal principal){return service.create(id,request,principal.getName());}
    @GetMapping("/api/admin/reviews") public Page<OrderReviewService.AdminReviewView> admin(Principal principal,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.adminList(principal.getName(),page,size);}
    @PostMapping("/api/admin/reviews/{id}/hide") public OrderReviewService.AdminReviewView hide(@PathVariable Long id,@Valid @RequestBody AdminActionRequest request,Principal principal){return service.moderate(id,true,request.reason(),principal.getName());}
    @PostMapping("/api/admin/reviews/{id}/restore") public OrderReviewService.AdminReviewView restore(@PathVariable Long id,@Valid @RequestBody AdminActionRequest request,Principal principal){return service.moderate(id,false,request.reason(),principal.getName());}
}
