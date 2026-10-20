package com.prashant.KhetWallah.notification;

import com.prashant.KhetWallah.order.OrderEntity;
import com.prashant.KhetWallah.listing.ListingEntity;
import com.prashant.KhetWallah.user.*;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.*;
import org.springframework.web.server.ResponseStatusException;
import org.mockito.ArgumentCaptor;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OrderNotificationServiceTest {
 final OrderNotificationRepository repository=mock(OrderNotificationRepository.class);
 final AppUserRepository users=mock(AppUserRepository.class);
 final OrderNotificationService service=new OrderNotificationService(repository,users);
 final AppUser buyer=mock(AppUser.class),farmer=mock(AppUser.class);
 final OrderEntity order=mock(OrderEntity.class);final ListingEntity listing=mock(ListingEntity.class);
 @BeforeEach void setup(){when(buyer.getId()).thenReturn(2L);when(farmer.getId()).thenReturn(1L);when(order.getId()).thenReturn(10L);when(order.getBuyer()).thenReturn(buyer);when(order.getListing()).thenReturn(listing);when(listing.getOwner()).thenReturn(farmer);when(listing.getProduceName()).thenReturn("Tomato");when(users.findByEmail("buyer@example.test")).thenReturn(Optional.of(buyer));}
 @Test void queuesPrivateSeparateEntriesForBothParticipants(){service.queue(order,"ACCEPTED");ArgumentCaptor<OrderNotification>captor=ArgumentCaptor.forClass(OrderNotification.class);verify(repository,times(2)).save(captor.capture());assertEquals(Set.of("BUYER","FARMER"),new HashSet<>(captor.getAllValues().stream().map(OrderNotification::getRecipientRole).toList()));assertEquals(2,captor.getAllValues().stream().map(OrderNotification::getEventKey).distinct().count());assertNotNull(captor.getValue().getCreatedAt());}
 @Test void retriesDoNotCreateDuplicates(){when(repository.existsByEventKey(anyString())).thenReturn(true);service.queue(order,"CREATED");verify(repository,never()).save(any());}
 @Test void changedPickupGetsANewDeduplicationKey(){when(order.getPickupRevision()).thenReturn(1L);when(order.getPickupAt()).thenReturn(Instant.parse("2027-01-01T12:00:00Z"));when(order.getPickupInstructions()).thenReturn("At the farm gate");service.queue(order,"PICKUP_UPDATED");when(order.getPickupRevision()).thenReturn(2L);when(order.getPickupInstructions()).thenReturn("At the market gate");service.queue(order,"PICKUP_UPDATED");when(order.getPickupRevision()).thenReturn(3L);when(order.getPickupInstructions()).thenReturn("At the farm gate");service.queue(order,"PICKUP_UPDATED");ArgumentCaptor<OrderNotification>cap=ArgumentCaptor.forClass(OrderNotification.class);verify(repository,times(6)).save(cap.capture());assertEquals(3,cap.getAllValues().stream().filter(n->n.getRecipientRole().equals("BUYER")).map(OrderNotification::getEventKey).distinct().count());}
 @Test void rejectsUnrecognizedEventsAndUnsavedOrder(){assertThrows(IllegalArgumentException.class,()->service.queue(order,"http://bad.test"));when(order.getId()).thenReturn(null);assertThrows(IllegalArgumentException.class,()->service.queue(order,"CREATED"));verify(repository,never()).save(any());}
 @Test void listingIsScopedToRecipientAndLinksAreLocal(){OrderNotification n=new OrderNotification(buyer,10L,"key","ACCEPTED","BUYER","Tomato");when(repository.findByRecipient_IdOrderByIdDesc(eq(2L),any())).thenReturn(new PageImpl<>(List.of(n)));when(repository.countByRecipient_IdAndReadAtIsNull(2L)).thenReturn(1L);var page=service.list("buyer@example.test",0,20);assertEquals(1,page.unreadCount());assertEquals("/my-orders.html?orderId=10#order-10",page.content().getFirst().orderUrl());assertEquals("order.notification.accepted",page.content().getFirst().labelKey());}
 @Test void cannotReadAnotherRecipientsNotification(){when(repository.findByIdAndRecipient_Id(55L,2L)).thenReturn(Optional.empty());var error=assertThrows(ResponseStatusException.class,()->service.read("buyer@example.test",55L));assertEquals(404,error.getStatusCode().value());verify(repository).findByIdAndRecipient_Id(55L,2L);}
 @Test void markReadIsIdempotent(){OrderNotification n=new OrderNotification(buyer,10L,"key","CREATED","BUYER","Tomato");when(repository.findByIdAndRecipient_Id(9L,2L)).thenReturn(Optional.of(n));service.read("buyer@example.test",9L);Instant first=n.getReadAt();service.read("buyer@example.test",9L);assertEquals(first,n.getReadAt());}
 @Test void markAllReadOnlyUpdatesCurrentAccount(){service.readAll("buyer@example.test");verify(repository).markAllRead(eq(2L),any(Instant.class));}
 @Test void invalidPagingAndSuspendedAccountAreRejected(){assertThrows(ResponseStatusException.class,()->service.list("buyer@example.test",-1,20));assertThrows(ResponseStatusException.class,()->service.list("buyer@example.test",0,51));when(buyer.isSuspended()).thenReturn(true);assertThrows(ResponseStatusException.class,()->service.list("buyer@example.test",0,20));}
}
