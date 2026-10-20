package com.prashant.KhetWallah.notification;

import com.prashant.KhetWallah.order.OrderEntity;
import com.prashant.KhetWallah.user.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

@Service
public class OrderNotificationService {
    private static final Set<String> EVENTS=Set.of("CREATED","ACCEPTED","REJECTED","CANCELLED","PICKUP_UPDATED","COMPLETED");
    private final OrderNotificationRepository notifications;
    private final AppUserRepository users;
    public OrderNotificationService(OrderNotificationRepository notifications,AppUserRepository users){this.notifications=notifications;this.users=users;}

    /** Called before email delivery is gated; entries commit or roll back with the order change. */
    @Transactional
    public void queue(OrderEntity order,String event){
        if(!EVENTS.contains(event)) throw new IllegalArgumentException("Unsupported order notification event");
        if(order.getId()==null) throw new IllegalArgumentException("Save the order before queuing updates");
        String revision=event.equals("PICKUP_UPDATED") ? ":"+order.getPickupRevision() : "";
        enqueue(order,order.getBuyer(),"BUYER",event,revision);
        AppUser farmer=order.getListing().getOwner();
        if(farmer!=null && !Objects.equals(farmer.getId(),order.getBuyer().getId())) enqueue(order,farmer,"FARMER",event,revision);
    }
    private void enqueue(OrderEntity order,AppUser recipient,String role,String event,String revision){
        String key=order.getId()+":"+event+revision+":"+recipient.getId();
        if(!notifications.existsByEventKey(key)) notifications.save(new OrderNotification(recipient,order.getId(),key,event,role,order.getListing().getProduceName()));
    }
    @Transactional(readOnly=true)
    public NotificationPage list(String email,int page,int size){
        AppUser user=user(email); PageRequest request=pageable(page,size);
        Page<NotificationView> result=notifications.findByRecipient_IdOrderByIdDesc(user.getId(),request).map(NotificationView::from);
        return new NotificationPage(result.getContent(),result.getNumber(),result.getTotalPages(),result.getTotalElements(),notifications.countByRecipient_IdAndReadAtIsNull(user.getId()));
    }
    @Transactional
    public NotificationView read(String email,Long id){
        AppUser user=user(email);
        OrderNotification item=notifications.findByIdAndRecipient_Id(id,user.getId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Notification not found"));
        item.markRead(); return NotificationView.from(item);
    }
    @Transactional
    public void readAll(String email){notifications.markAllRead(user(email).getId(),Instant.now());}
    private AppUser user(String email){return users.findByEmail(email).filter(u->!u.isSuspended()).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Sign in to your account"));}
    private static PageRequest pageable(int page,int size){
        if(page<0 || size<1 || size>50) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Use page >= 0 and size 1–50");
        return PageRequest.of(page,size);
    }
    public record NotificationPage(List<NotificationView> content,int page,int totalPages,long totalElements,long unreadCount){}
    public record NotificationView(Long id,Long orderId,String eventType,String labelKey,String cropName,String orderUrl,Instant createdAt,Instant readAt){
        static NotificationView from(OrderNotification n){
            String page=n.getRecipientRole().equals("BUYER")?"/my-orders.html":"/received-orders.html";
            return new NotificationView(n.getId(),n.getOrderId(),n.getEventType(),"order.notification."+n.getEventType().toLowerCase(Locale.ROOT),n.getCropName(),page+"?orderId="+n.getOrderId()+"#order-"+n.getOrderId(),n.getCreatedAt(),n.getReadAt());
        }
    }
}
