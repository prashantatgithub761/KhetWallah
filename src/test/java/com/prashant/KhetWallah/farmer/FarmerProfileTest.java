package com.prashant.KhetWallah.farmer;
import com.prashant.KhetWallah.listing.ListingEntity;
import com.prashant.KhetWallah.saved.*;
import com.prashant.KhetWallah.user.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class FarmerProfileTest {
 @Test void optionalFieldsAreTrimmedAndCanBeCleared(){var p=new FarmerProfile(1L);p.update(" Farm "," About "," Meerut ","250001");assertEquals("Farm",p.getFarmName());assertEquals("About",p.getBio());p.update(" ",null,"",null);assertNull(p.getFarmName());assertNull(p.getBio());assertNull(p.getDistrict());}
 @Test void badPageDoesNotQueryDatabase(){var users=mock(AppUserRepository.class);var em=mock(EntityManager.class);var s=new FarmerProfileService(users,mock(FarmerProfileRepository.class),em);assertThrows(ResponseStatusException.class,()->s.publicProfile(1L,-1,12));assertThrows(ResponseStatusException.class,()->s.publicProfile(1L,0,51));verifyNoInteractions(users,em);}
 @Test void suspendedAccountCannotReadOrEditOwnProfile(){var users=mock(AppUserRepository.class);var u=new AppUser("X","x@example.test","hash");u.suspend();when(users.findByEmail(u.getEmail())).thenReturn(Optional.of(u));var s=new FarmerProfileService(users,mock(FarmerProfileRepository.class),mock(EntityManager.class));assertThrows(ResponseStatusException.class,()->s.own(u.getEmail()));assertThrows(ResponseStatusException.class,()->s.update(u.getEmail(),new UpdateFarmerProfileRequest("Farm",null,null,null)));}
 @Test void savedAvailabilityNeverPretendsClosedOrExpiredStockCanBeOrdered(){var u=new AppUser("Farmer","f@example.test","hash");var l=new ListingEntity("Tomato",u,new BigDecimal("30"),new BigDecimal("20"),"Meerut");assertEquals("AVAILABLE",SavedService.availability(l));l.updateDiscoveryDetails(null,null,null,null,LocalDate.now().minusDays(1),false,false);assertEquals("EXPIRED",SavedService.availability(l));l.close();assertEquals("CLOSED",SavedService.availability(l));}
 @Test void soldOutAndBelowMinimumHaveDistinctAvailability(){var u=new AppUser("Farmer","f@example.test","hash");var l=new ListingEntity("Tomato",u,new BigDecimal("0"),new BigDecimal("20"),"Meerut");assertEquals("SOLD_OUT",SavedService.availability(l));l.updateDetails(new BigDecimal("0.50"),new BigDecimal("20"),"Meerut");l.updateCropDetails(null,null,BigDecimal.ONE,null,false);assertEquals("BELOW_MINIMUM",SavedService.availability(l));}
}
