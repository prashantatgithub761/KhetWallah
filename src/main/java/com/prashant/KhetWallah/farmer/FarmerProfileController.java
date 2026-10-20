package com.prashant.KhetWallah.farmer;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
public class FarmerProfileController {
 private final FarmerProfileService service;public FarmerProfileController(FarmerProfileService service){this.service=service;}
 @GetMapping("/api/farmers/{id}") public FarmerProfileService.FarmerPage profile(@PathVariable Long id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="12") int size){return service.publicProfile(id,page,size);}
 @GetMapping("/api/me/farmer-profile") public ResponseEntity<FarmerProfileService.ProfileView> own(Principal principal){return ResponseEntity.ok().header("Cache-Control","no-store").body(service.own(principal.getName()));}
 @PutMapping("/api/me/farmer-profile") public ResponseEntity<FarmerProfileService.ProfileView> update(@Valid @RequestBody UpdateFarmerProfileRequest request,Principal principal){return ResponseEntity.ok().header("Cache-Control","no-store").body(service.update(principal.getName(),request));}
}
