package com.prashant.KhetWallah.order;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
@RestController
public class PickupCodeController {
    private final PickupCodeService service;
    public PickupCodeController(PickupCodeService service){this.service=service;}
    @PostMapping("/api/orders/{id}/pickup-code")
    public ResponseEntity<PickupCodeService.Challenge> issue(@PathVariable Long id,Principal principal){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.issue(id,principal.getName()));
    }
    @PostMapping("/api/orders/{id}/confirm-pickup")
    public ResponseEntity<Void> confirm(@PathVariable Long id,Principal principal,@Valid @RequestBody Confirm input){
        service.confirm(id,principal.getName(),input.code());return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    public record Confirm(@NotBlank @Size(max=128) String code){@Override public String toString(){return "PickupConfirmation[REDACTED]";}}
}
