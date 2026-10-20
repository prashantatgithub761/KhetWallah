package com.prashant.KhetWallah.insights;
import com.prashant.KhetWallah.order.OrderStatus;
import com.prashant.KhetWallah.user.*;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.List;
@Service @Transactional(readOnly=true)
public class SellerInsightsService {
 private final AppUserRepository users;private final EntityManager em;
 public SellerInsightsService(AppUserRepository users,EntityManager em){this.users=users;this.em=em;}
 public Insights forAccount(String email){
  Long id=users.findByEmail(email).filter(u->!u.isSuspended()).map(AppUser::getId).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Sign in to your account"));
  List<Object[]> states=em.createQuery("select o.status,count(o) from OrderEntity o where o.listing.owner.id=:id group by o.status",Object[].class).setParameter("id",id).getResultList();
  long all=0,pending=0,accepted=0,completed=0,cancelled=0,rejected=0;for(Object[] row:states){long n=((Number)row[1]).longValue();all+=n;switch((OrderStatus)row[0]){case PENDING->pending=n;case ACCEPTED->accepted=n;case COMPLETED->completed=n;case CANCELLED->cancelled=n;case REJECTED->rejected=n;}}
  Object[] totals=em.createQuery("select sum(o.quantityKg),sum(o.totalAmount) from OrderEntity o where o.listing.owner.id=:id and o.status=:completed",Object[].class).setParameter("id",id).setParameter("completed",OrderStatus.COMPLETED).getSingleResult();
  List<TopCrop> top=em.createQuery("select o.listing.produceName,count(o),sum(o.quantityKg),sum(o.totalAmount) from OrderEntity o where o.listing.owner.id=:id and o.status=:completed group by o.listing.produceName order by sum(o.quantityKg) desc,o.listing.produceName asc",Object[].class).setParameter("id",id).setParameter("completed",OrderStatus.COMPLETED).setMaxResults(5).getResultList().stream().map(row->new TopCrop((String)row[0],((Number)row[1]).longValue(),decimal(row[2]),decimal(row[3]))).toList();
  return new Insights(all,pending,accepted,completed,cancelled,rejected,decimal(totals[0]),decimal(totals[1]),false,"Completed-order value; payments are arranged outside KhetWallah and are not tracked here.",top);
 }
 private static BigDecimal decimal(Object n){return n==null?BigDecimal.ZERO:(BigDecimal)n;}
 public record TopCrop(String produceName,long completedOrders,BigDecimal quantityKg,BigDecimal completedOrderValue){}
 public record Insights(long totalOrders,long pendingOrders,long acceptedOrders,long completedOrders,long cancelledOrders,long rejectedOrders,BigDecimal completedQuantityKg,BigDecimal completedOrderValue,boolean paymentsTracked,String valueExplanation,List<TopCrop> topCrops){}
}
