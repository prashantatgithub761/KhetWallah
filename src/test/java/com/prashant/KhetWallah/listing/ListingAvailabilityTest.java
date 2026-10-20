package com.prashant.KhetWallah.listing;
import com.prashant.KhetWallah.user.*;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ListingAvailabilityTest {
    AppUser farmer=new AppUser("Farmer","farmer@example.test","hash");
    ListingEntity crop=new ListingEntity("Onion",farmer,new BigDecimal("10"),new BigDecimal("20"),"Meerut");
    @Test void expiryAndPausePreventOrdersWhileTodayIsAvailable() {
        crop.updateDiscoveryDetails("Meerut","250001",null,null,LocalDate.now(),false,false);
        assertTrue(crop.isPubliclyAvailable());assertTrue(crop.isAvailableForOrders());
        crop.pause();assertFalse(crop.isPubliclyAvailable());crop.resume();assertTrue(crop.isAvailableForOrders());
        crop.updateDiscoveryDetails(null,null,null,null,LocalDate.now().minusDays(1),false,false);
        assertFalse(crop.isPubliclyAvailable());assertFalse(crop.isAvailableForOrders());
    }
    @Test void soldOutDoesNotEraseStockAndHiddenCannotBeResumed() {
        crop.markSoldOut();assertEquals(new BigDecimal("10"),crop.getAvailableQuantityKg());assertFalse(crop.isAvailableForOrders());
        crop.resume();crop.hide();assertThrows(IllegalStateException.class,crop::resume);
        assertThrows(IllegalStateException.class,crop::markSoldOut);
    }
    @Test void belowMinimumRemainsViewableButCannotBeOrdered() {
        crop.updateCropDetails(null,null,new BigDecimal("5"),null,false);
        crop.updateDetails(new BigDecimal("3"),new BigDecimal("20"),"Meerut");
        assertTrue(crop.isPubliclyAvailable());assertFalse(crop.isAvailableForOrders());
    }
    @Test void validatesCoordinatePairsPinAndRadius() {
        ListingService service=new ListingService(mock(ListingRepository.class),mock(AppUserRepository.class));
        assertThrows(ResponseStatusException.class,()->service.getAllListings("","",0,6,null,null,false,"oldest",null,null,new BigDecimal("10"),null,new BigDecimal("20")));
        assertThrows(ResponseStatusException.class,()->service.getAllListings("","",0,6,null,null,false,"oldest",null,"123",null,null,null));
        assertThrows(ResponseStatusException.class,()->service.getAllListings("","",0,6,null,null,false,"oldest",null,null,new BigDecimal("91"),new BigDecimal("10"),new BigDecimal("20")));
        assertThrows(ResponseStatusException.class,()->service.getAllListings("","",0,6,null,null,false,"oldest",null,null,new BigDecimal("20"),new BigDecimal("10"),new BigDecimal("201")));
    }
    @Test void ownershipAndHiddenStatusAreCheckedOnAvailabilityChange() {
        ListingRepository repo=mock(ListingRepository.class);when(repo.findByIdForUpdate(1L)).thenReturn(Optional.of(crop));
        ListingService service=new ListingService(repo,mock(AppUserRepository.class));
        assertThrows(ResponseStatusException.class,()->service.changeAvailability(1L,"other@example.test","pause"));
        service.changeAvailability(1L,farmer.getEmail(),"pause");assertEquals(ListingStatus.PAUSED,crop.getStatus());
        crop.hide();assertThrows(ResponseStatusException.class,()->service.changeAvailability(1L,farmer.getEmail(),"resume"));
    }
 @org.junit.jupiter.api.Test void moderationRestoresPausedAndSoldOutWithoutReopeningThem(){
  var owner=new com.prashant.KhetWallah.user.AppUser("Farmer","restore@example.test","hash");
  var l=new ListingEntity("Crop",owner,new java.math.BigDecimal("20"),java.math.BigDecimal.TEN,"Area");
  l.pause();l.hide();l.restore();org.junit.jupiter.api.Assertions.assertEquals(ListingStatus.PAUSED,l.getStatus());
  l.markSoldOut();l.hide();l.restore();org.junit.jupiter.api.Assertions.assertEquals(ListingStatus.SOLD_OUT,l.getStatus());
 }
}
