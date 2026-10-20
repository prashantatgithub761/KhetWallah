package com.prashant.KhetWallah.listing;
import com.prashant.KhetWallah.mail.EmailSender;
import com.prashant.KhetWallah.order.*;
import com.prashant.KhetWallah.user.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.jpa.hibernate.ddl-auto=validate","app.email.enabled=false"})
@Transactional
class ListingDiscoveryIntegrationTest {
 @Autowired EntityManager em;@Autowired AppUserRepository users;@Autowired ListingRepository crops;@Autowired ListingService search;@Autowired OrderService orders;
 @MockitoBean EmailSender sender;
 AppUser farmer,buyer;String marker;
 @BeforeEach void fixtures(){marker="discovery-"+UUID.randomUUID();farmer=users.saveAndFlush(new AppUser("Farmer",marker+"-farmer@example.test","hash"));buyer=users.saveAndFlush(new AppUser("Buyer",marker+"-buyer@example.test","hash"));}
 ListingEntity crop(String name,String lat,String lng){var l=new ListingEntity(marker+name,farmer,new BigDecimal("20"),new BigDecimal("30"),"General pickup area");l.updateDiscoveryDetails("Meerut","250001",new BigDecimal(lat),new BigDecimal(lng),null,false,false);return crops.saveAndFlush(l);}
 @Test void mysqlDistanceFiltersCoordinatesAndPreservesOtherFilters(){var nearby=crop("near","28.990000","77.700000");crop("far","19.000000","72.800000");var result=search.getAllListings(marker,"",0,20,null,null,false,"oldest","Meerut","250001",new BigDecimal("28.990000"),new BigDecimal("77.700000"),new BigDecimal("10"));assertEquals(1,result.totalElements());assertEquals(nearby.getId(),result.content().getFirst().getId());}
 @Test void expiredPausedAndSuspendedListingsCannotTakeNewOrders(){var l=crop("expiry","28.990000","77.700000");l.updateDiscoveryDetails(null,null,null,null,LocalDate.now().minusDays(1),false,false);crops.flush();assertFalse(l.isPubliclyAvailable());assertEquals(0,search.getAllListings(marker,"",0,20).totalElements());assertEquals(409,assertThrows(ResponseStatusException.class,()->orders.createOrder(new CreateOrderRequest(l.getId(),BigDecimal.ONE),buyer.getEmail())).getStatusCode().value());l.updateDiscoveryDetails(null,null,null,null,null,false,true);l.pause();assertEquals(409,assertThrows(ResponseStatusException.class,()->orders.createOrder(new CreateOrderRequest(l.getId(),BigDecimal.ONE),buyer.getEmail())).getStatusCode().value());l.resume();farmer.suspend();assertEquals(409,assertThrows(ResponseStatusException.class,()->orders.createOrder(new CreateOrderRequest(l.getId(),BigDecimal.ONE),buyer.getEmail())).getStatusCode().value());assertEquals(0,l.getAvailableQuantityKg().compareTo(new BigDecimal("20")));}
}
