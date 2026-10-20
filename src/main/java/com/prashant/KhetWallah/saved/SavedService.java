package com.prashant.KhetWallah.saved;
import com.prashant.KhetWallah.farmer.FarmerProfile;
import com.prashant.KhetWallah.farmer.FarmerProfileService;
import com.prashant.KhetWallah.listing.*;
import com.prashant.KhetWallah.user.*;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
@Service @Transactional(readOnly=true)
public class SavedService {
 private final AppUserRepository users;private final SavedItemRepository items;private final EntityManager em;
 public SavedService(AppUserRepository users,SavedItemRepository items,EntityManager em){this.users=users;this.items=items;this.em=em;}
 public SavedState state(String type,Long id,String email){type=type(type);return new SavedState(items.existsByAccountIdAndTargetTypeAndTargetId(account(email).getId(),type,id));}
 @Transactional public void save(String type,Long id,String email){
  type=type(type);AppUser u=account(email);users.findByIdForUpdate(u.getId()).orElseThrow(()->unauthorized());
  if("LISTING".equals(type)){
   ListingEntity crop=em.find(ListingEntity.class,id);if(crop==null||!crop.isPubliclyAvailable())throw missing();
  }else{AppUser target=users.findById(id).filter(a->!a.isSuspended()).orElseThrow(SavedService::missing);
   long listings=em.createQuery("select count(l) from ListingEntity l where l.owner.id=:id and l.status<>:hidden",Long.class).setParameter("id",target.getId()).setParameter("hidden",ListingStatus.HIDDEN).getSingleResult();if(listings==0)throw missing();
  }
  if(items.findForUpdate(u.getId(),type,id).isEmpty())items.save(new SavedItem(u.getId(),type,id));
 }
 @Transactional public void remove(String type,Long id,String email){type=type(type);AppUser u=account(email);users.findByIdForUpdate(u.getId()).orElseThrow(SavedService::unauthorized);items.findForUpdate(u.getId(),type,id).ifPresent(items::delete);}
 public SavedPage<SavedCrop> listings(String email,int page,int size){
  bounds(page,size);Long uid=account(email).getId();String where="s.accountId=:uid and s.targetType='LISTING' and l.id=s.targetId and l.status<>:hidden and (owner is null or owner.suspended=false)";
  List<ListingEntity> crops=em.createQuery("select l from SavedItem s,ListingEntity l left join l.owner owner where "+where+" order by s.id desc",ListingEntity.class).setParameter("uid",uid).setParameter("hidden",ListingStatus.HIDDEN).setFirstResult(page*size).setMaxResults(size).getResultList();
  long total=em.createQuery("select count(s) from SavedItem s,ListingEntity l left join l.owner owner where "+where,Long.class).setParameter("uid",uid).setParameter("hidden",ListingStatus.HIDDEN).getSingleResult();
  return new SavedPage<>(crops.stream().map(l->new SavedCrop(FarmerProfileService.Crop.from(l),l.getStatus().name(),availability(l))).toList(),page,size,total,(int)((total+size-1)/size));
 }
 public SavedPage<SavedFarmer> farmers(String email,int page,int size){
  bounds(page,size);Long uid=account(email).getId();String where="s.accountId=:uid and s.targetType='FARMER' and u.id=s.targetId and u.suspended=false and exists(select l.id from ListingEntity l where l.owner.id=u.id and l.status<>:hidden)";
  List<AppUser> farmers=em.createQuery("select u from SavedItem s,AppUser u where "+where+" order by s.id desc",AppUser.class).setParameter("uid",uid).setParameter("hidden",ListingStatus.HIDDEN).setFirstResult(page*size).setMaxResults(size).getResultList();
  long total=em.createQuery("select count(s) from SavedItem s,AppUser u where "+where,Long.class).setParameter("uid",uid).setParameter("hidden",ListingStatus.HIDDEN).getSingleResult();
  return new SavedPage<>(farmers.stream().map(u->{FarmerProfile p=em.find(FarmerProfile.class,u.getId());return new SavedFarmer(u.getId(),u.getDisplayName(),p==null?null:p.getFarmName(),p==null?null:p.getDistrict(),p==null?null:p.getPinCode());}).toList(),page,size,total,(int)((total+size-1)/size));
 }
 public static String availability(ListingEntity l){if(!l.isPubliclyAvailable())return l.getStatus()==ListingStatus.ACTIVE?"EXPIRED":l.getStatus().name();if(l.getAvailableQuantityKg().signum()<=0)return "SOLD_OUT";if(!l.isAvailableForOrders())return "BELOW_MINIMUM";return "AVAILABLE";}
 private AppUser account(String email){return users.findByEmail(email).filter(u->!u.isSuspended()).orElseThrow(SavedService::unauthorized);}
 private static ResponseStatusException unauthorized(){return new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Sign in to your account");}
 private static ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Crop or farmer not found");}
 private static String type(String value){if(!Set.of("LISTING","FARMER").contains(value))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a crop or farmer");return value;}
 private static void bounds(int page,int size){if(page<0||page>100000||size<1||size>50)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Use page 0–100000 and size 1–50");}
 public record SavedState(boolean saved){}
 public record SavedCrop(FarmerProfileService.Crop listing,String status,String availability){}
 public record SavedFarmer(Long farmerId,String displayName,String farmName,String district,String pinCode){}
 public record SavedPage<T>(List<T> content,int page,int size,long totalElements,int totalPages){}
}
