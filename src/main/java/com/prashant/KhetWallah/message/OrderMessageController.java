package com.prashant.KhetWallah.message;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
@RestController
public class OrderMessageController {
 private final OrderMessageService service;
 public OrderMessageController(OrderMessageService service){this.service=service;}
 @GetMapping("/api/orders/{id}/messages") public ResponseEntity<OrderMessageService.MessagePage> list(@PathVariable Long id,Principal principal,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return privateResponse(service.list(id,principal.getName(),page,size));}
 @PostMapping("/api/orders/{id}/messages") public ResponseEntity<OrderMessageService.MessageView> send(@PathVariable Long id,@Valid @RequestBody SendRequest request,Principal principal){return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(service.send(id,request.body(),principal.getName()));}
 @PostMapping("/api/orders/{id}/messages/read") public ResponseEntity<Void> read(@PathVariable Long id,@Valid @RequestBody ReadRequest request,Principal principal){service.markRead(id,request.lastSeenId(),principal.getName());return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();}
 @PostMapping("/api/messages/{id}/report") public ResponseEntity<Void> report(@PathVariable Long id,@Valid @RequestBody ReasonRequest request,Principal principal){service.report(id,request.reason(),principal.getName());return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();}
 @GetMapping("/api/me/conversations") public ResponseEntity<?> conversations(Principal principal,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return privateResponse(service.conversations(principal.getName(),page,size));}
 @GetMapping("/api/me/messages/unread") public ResponseEntity<?> unread(Principal principal){return privateResponse(java.util.Map.of("unreadCount",service.unread(principal.getName())));}
 @GetMapping("/api/admin/messages/reports") public ResponseEntity<?> reports(Principal principal,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return privateResponse(service.reported(principal.getName(),page,size));}
 @PostMapping("/api/admin/messages/{id}/hide") public ResponseEntity<Void> hide(@PathVariable Long id,@Valid @RequestBody ReasonRequest request,Principal principal){service.moderate(id,true,request.reason(),principal.getName());return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();}
 @PostMapping("/api/admin/messages/{id}/restore") public ResponseEntity<Void> restore(@PathVariable Long id,@Valid @RequestBody ReasonRequest request,Principal principal){service.moderate(id,false,request.reason(),principal.getName());return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();}
 private static <T>ResponseEntity<T> privateResponse(T body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
 public record SendRequest(@NotBlank @Size(max=2000) String body){@Override public String toString(){return "SendRequest[REDACTED]";}}
 public record ReadRequest(@NotNull @Positive Long lastSeenId){}
 public record ReasonRequest(@NotBlank @Size(max=500) String reason){}
}
