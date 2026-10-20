package com.prashant.KhetWallah.saved;
import java.security.Principal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
public class SavedController {
 private final SavedService service;public SavedController(SavedService service){this.service=service;}
 @GetMapping("/api/me/saved-listings") public ResponseEntity<?> listings(Principal p,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="12") int size){return privateBody(service.listings(p.getName(),page,size));}
 @GetMapping("/api/me/saved-farmers") public ResponseEntity<?> farmers(Principal p,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="12") int size){return privateBody(service.farmers(p.getName(),page,size));}
 @GetMapping("/api/me/saved-listings/{id}") public ResponseEntity<?> listingState(@PathVariable Long id,Principal p){return privateBody(service.state("LISTING",id,p.getName()));}
 @GetMapping("/api/me/saved-farmers/{id}") public ResponseEntity<?> farmerState(@PathVariable Long id,Principal p){return privateBody(service.state("FARMER",id,p.getName()));}
 @PutMapping("/api/me/saved-listings/{id}") public ResponseEntity<Void> saveListing(@PathVariable Long id,Principal p){service.save("LISTING",id,p.getName());return empty();}
 @PutMapping("/api/me/saved-farmers/{id}") public ResponseEntity<Void> saveFarmer(@PathVariable Long id,Principal p){service.save("FARMER",id,p.getName());return empty();}
 @DeleteMapping("/api/me/saved-listings/{id}") public ResponseEntity<Void> removeListing(@PathVariable Long id,Principal p){service.remove("LISTING",id,p.getName());return empty();}
 @DeleteMapping("/api/me/saved-farmers/{id}") public ResponseEntity<Void> removeFarmer(@PathVariable Long id,Principal p){service.remove("FARMER",id,p.getName());return empty();}
 private static <T> ResponseEntity<T> privateBody(T body){return ResponseEntity.ok().header("Cache-Control","no-store").body(body);}
 private static ResponseEntity<Void> empty(){return ResponseEntity.noContent().header("Cache-Control","no-store").build();}
}
