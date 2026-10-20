package com.prashant.KhetWallah.farmer;
import com.prashant.KhetWallah.listing.*;
import com.prashant.KhetWallah.order.OrderStatus;
import com.prashant.KhetWallah.user.*;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
@Service @Transactional(readOnly=true)
public class FarmerProfileService {
 private final AppUserRepository users;private final FarmerProfileRepository profiles;private final EntityManager em;
 public FarmerProfileService(AppUserRepository users,FarmerProfileRepository profiles,EntityManager em){this.users=users;this.profiles=profiles;this.em=em;}
 public ProfileView own(String email){AppUser u=account(email);return view(u,profiles.findById(u.getId()).orElse(null));}
 @Transactional public ProfileView update(String email,UpdateFarmerProfileRequest request){
  AppUser u=account(email);users.findByIdForUpdate(u.getId()).orElseThrow(()->missing());
  validate(request);FarmerProfile p=profiles.findForUpdate(u.getId()).orElseGet(()->new FarmerProfile(u.getId()));
  p.update(request.farmName(),request.bio(),request.district(),request.pinCode());profiles.save(p);return view(u,p);
 }
 public FarmerPage publicProfile(Long id,int page,int size){
  bounds(page,size);AppUser u=users.findById(id).filter(a->!a.isSuspended()).orElseThrow(FarmerProfileService::missing);
  long historical=em.createQuery("select count(l) from ListingEntity l where l.owner.id=:id and l.status<>:hidden",Long.class).setParameter("id",id).setParameter("hidden",ListingStatus.HIDDEN).getSingleResult();
  if(historical==0)throw missing();
  // Available listings are filtered in SQL so pagination counts stay correct.
  String visible="l.owner.id=:id and l.status=:active and (l.availableUntil is null or l.availableUntil>=:today)";
  List<ListingEntity> crops=em.createQuery("select l from ListingEntity l where "+visible+" order by l.id desc",ListingEntity.class).setParameter("id",id).setParameter("active",ListingStatus.ACTIVE).setParameter("today",LocalDate.now()).setFirstResult(page*size).setMaxResults(size).getResultList();
  long total=em.createQuery("select count(l) from ListingEntity l where "+visible,Long.class).setParameter("id",id).setParameter("active",ListingStatus.ACTIVE).setParameter("today",LocalDate.now()).getSingleResult();
  Object[] rating=em.createQuery("select count(r),avg(r.rating) from OrderReview r where r.order.listing.owner.id=:id and r.hidden=false and r.order.status=:completed and r.order.listing.status<>:hidden",Object[].class).setParameter("id",id).setParameter("completed",OrderStatus.COMPLETED).setParameter("hidden",ListingStatus.HIDDEN).getSingleResult();
  return new FarmerPage(view(u,profiles.findById(id).orElse(null)),new Rating(((Number)rating[0]).longValue(),rating[1]==null?null:((Number)rating[1]).doubleValue()),crops.stream().map(Crop::from).toList(),page,size,total,(int)((total+size-1)/size));
 }
 private static void validate(UpdateFarmerProfileRequest r){if(r==null||length(r.farmName())>100||length(r.bio())>1000||length(r.district())>100||r.pinCode()!=null&&!r.pinCode().isBlank()&&!r.pinCode().matches("[1-9][0-9]{5}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Check the profile field lengths and six-digit PIN code");}
 private static int length(String v){return v==null?0:v.length();}
 private AppUser account(String email){return users.findByEmail(email).filter(u->!u.isSuspended()).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Sign in to your account"));}
 private static ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Farmer profile not found");}
 private static void bounds(int p,int s){if(p<0||p>100000||s<1||s>50)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Use page 0–100000 and size 1–50");}
 private static ProfileView view(AppUser u,FarmerProfile p){return new ProfileView(u.getId(),u.getDisplayName(),p==null?null:p.getFarmName(),p==null?null:p.getBio(),p==null?null:p.getDistrict(),p==null?null:p.getPinCode());}
 public record ProfileView(Long farmerId,String displayName,String farmName,String bio,String district,String pinCode){}
 public record Rating(long reviewCount,Double averageRating){}
 public record Crop(Long id,Long farmerId,String produceName,BigDecimal pricePerKg,BigDecimal availableQuantityKg,BigDecimal minimumOrderQuantityKg,String pickupArea,String district,String pinCode,LocalDate availableUntil,String photoUrl,boolean canRequest){
  public static Crop from(ListingEntity l){return new Crop(l.getId(),l.getOwner()==null?null:l.getOwner().getId(),l.getProduceName(),l.getPricePerKg(),l.getAvailableQuantityKg(),l.getMinimumOrderQuantityKg(),l.getPickupArea(),l.getDistrict(),l.getPinCode(),l.getAvailableUntil(),l.getPhotoFileName()==null?null:"/api/listings/"+l.getId()+"/photo",l.isAvailableForOrders());}
 }
 public record FarmerPage(ProfileView profile,Rating ratings,List<Crop> content,int page,int size,long totalElements,int totalPages){}
}
