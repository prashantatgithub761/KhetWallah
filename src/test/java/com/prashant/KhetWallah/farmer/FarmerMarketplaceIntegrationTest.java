package com.prashant.KhetWallah.farmer;
import com.prashant.KhetWallah.insights.SellerInsightsService;
import com.prashant.KhetWallah.listing.*;
import com.prashant.KhetWallah.order.*;
import com.prashant.KhetWallah.review.*;
import com.prashant.KhetWallah.saved.*;
import com.prashant.KhetWallah.user.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"spring.jpa.hibernate.ddl-auto=validate","app.email.enabled=false"}) @Transactional
class FarmerMarketplaceIntegrationTest {
 @Autowired AppUserRepository users;@Autowired ListingRepository listings;@Autowired OrderRepository orders;@Autowired OrderReviewRepository reviews;@Autowired FarmerProfileService profiles;@Autowired SavedService saved;@Autowired SellerInsightsService insights;@Autowired EntityManager em;
 @Test void publicProfileHasVisibleCompletedReviewAggregateAndOnlyCurrentCrops(){
  var farmer=user("Farmer");var buyer=user("Buyer");var crop=crop(farmer,"Tomato");var closed=crop(farmer,"Wheat");closed.close();var hidden=crop(farmer,"Hidden");hidden.hide();var expired=crop(farmer,"Expired");expired.updateDiscoveryDetails(null,null,null,null,LocalDate.now().minusDays(1),false,false);
  var order=completed(crop,buyer,"2");reviews.saveAndFlush(new OrderReview(order,4,"Fresh produce"));var hiddenReview=new OrderReview(completed(closed,buyer,"1"),1,"Hidden by admin");hiddenReview.setHidden(true);reviews.saveAndFlush(hiddenReview);reviews.saveAndFlush(new OrderReview(completed(hidden,buyer,"1"),1,"Removed crop"));
  profiles.update(farmer.getEmail(),new UpdateFarmerProfileRequest("My Farm","Seasonal vegetables","Meerut","250001"));var p=profiles.publicProfile(farmer.getId(),0,12);
  assertEquals("My Farm",p.profile().farmName());assertEquals(1,p.totalElements());assertEquals(crop.getId(),p.content().getFirst().id());assertEquals(1,p.ratings().reviewCount());assertEquals(4.0,p.ratings().averageRating());
  farmer.suspend();users.flush();assertThrows(ResponseStatusException.class,()->profiles.publicProfile(farmer.getId(),0,12));
 }
 @Test void savedItemsAreIdempotentPrivateAndHideModeratedOrSuspendedTargets(){
  var farmer=user("Farmer");var buyer=user("Buyer");var other=user("Other");var crop=crop(farmer,"Onion");saved.save("LISTING",crop.getId(),buyer.getEmail());saved.save("LISTING",crop.getId(),buyer.getEmail());saved.save("FARMER",farmer.getId(),buyer.getEmail());em.flush();
  assertEquals(1,saved.listings(buyer.getEmail(),0,12).totalElements());assertEquals(0,saved.listings(other.getEmail(),0,12).totalElements());assertFalse(saved.state("LISTING",crop.getId(),other.getEmail()).saved());
  crop.close();em.flush();assertEquals("CLOSED",saved.listings(buyer.getEmail(),0,12).content().getFirst().availability());assertFalse(saved.listings(buyer.getEmail(),0,12).content().getFirst().listing().canRequest());
  saved.remove("LISTING",crop.getId(),buyer.getEmail());saved.remove("LISTING",crop.getId(),buyer.getEmail());assertEquals(0,saved.listings(buyer.getEmail(),0,12).totalElements());
  farmer.suspend();em.flush();assertEquals(0,saved.farmers(buyer.getEmail(),0,12).totalElements());
 }
 @Test void savedListingDisappearsWhenHiddenAndCannotBeSavedDirectly(){
  var farmer=user("Farmer");var buyer=user("Buyer");var crop=crop(farmer,"Potato");saved.save("LISTING",crop.getId(),buyer.getEmail());crop.hide();em.flush();assertEquals(0,saved.listings(buyer.getEmail(),0,12).totalElements());assertThrows(ResponseStatusException.class,()->saved.save("LISTING",crop.getId(),buyer.getEmail()));
 }
 @Test void insightsUseCompletedSnapshotAmountsAndExcludeOtherSellersOrPendingOrders(){
  var farmer=user("Farmer");var buyer=user("Buyer");var other=user("Other Farmer");var crop=crop(farmer,"Tomato");completed(crop,buyer,"2.50");orders.saveAndFlush(new OrderEntity(crop,buyer,BigDecimal.ONE));completed(crop(other,"Other"),buyer,"10");crop.updateDetails(new BigDecimal("20"),new BigDecimal("999"),"Meerut");em.flush();
  var data=insights.forAccount(farmer.getEmail());assertEquals(2,data.totalOrders());assertEquals(1,data.completedOrders());assertEquals(1,data.pendingOrders());assertEquals(0,new BigDecimal("50.00").compareTo(data.completedOrderValue()));assertEquals(0,new BigDecimal("2.50").compareTo(data.completedQuantityKg()));assertFalse(data.paymentsTracked());assertEquals("Tomato",data.topCrops().getFirst().produceName());
 }
 private AppUser user(String name){return users.saveAndFlush(new AppUser(name,UUID.randomUUID()+"@example.test","unused-test-hash"));}
 private ListingEntity crop(AppUser owner,String name){return listings.saveAndFlush(new ListingEntity(name,owner,new BigDecimal("100"),new BigDecimal("20"),"Meerut"));}
 private OrderEntity completed(ListingEntity crop,AppUser buyer,String qty){var o=new OrderEntity(crop,buyer,new BigDecimal(qty));o.accept();o.schedulePickup(Instant.now().plusSeconds(7200),"Test pickup");o.complete();return orders.saveAndFlush(o);}
}
