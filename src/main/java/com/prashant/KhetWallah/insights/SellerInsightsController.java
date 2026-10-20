package com.prashant.KhetWallah.insights;
import java.security.Principal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
public class SellerInsightsController {
 private final SellerInsightsService service;public SellerInsightsController(SellerInsightsService service){this.service=service;}
 @GetMapping("/api/me/insights") public ResponseEntity<SellerInsightsService.Insights> insights(Principal p){return ResponseEntity.ok().header("Cache-Control","no-store").body(service.forAccount(p.getName()));}
}
