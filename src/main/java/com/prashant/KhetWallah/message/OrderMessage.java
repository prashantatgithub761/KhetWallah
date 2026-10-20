package com.prashant.KhetWallah.message;

import com.prashant.KhetWallah.order.OrderEntity;
import com.prashant.KhetWallah.user.AppUser;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="order_messages", indexes={@Index(name="ix_message_order_id",columnList="order_id,id"),@Index(name="ix_message_recipient_read",columnList="recipient_id,read_at"),@Index(name="ix_message_sender_time",columnList="sender_id,created_at")})
public class OrderMessage {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="order_id",nullable=false) private OrderEntity order;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="sender_id",nullable=false) private AppUser sender;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="recipient_id",nullable=false) private AppUser recipient;
 @Column(nullable=false,length=2000) private String body;
 @Column(nullable=false) private boolean hidden;
 @Column(name="created_at",nullable=false) private Instant createdAt;
 @Column(name="read_at") private Instant readAt;
 protected OrderMessage(){}
 OrderMessage(OrderEntity order,AppUser sender,AppUser recipient,String body,Instant now){this.order=order;this.sender=sender;this.recipient=recipient;this.body=body;this.createdAt=now;}
 public Long getId(){return id;} public OrderEntity getOrder(){return order;} public AppUser getSender(){return sender;} public AppUser getRecipient(){return recipient;} public String getBody(){return body;} public boolean isHidden(){return hidden;} public Instant getCreatedAt(){return createdAt;} public Instant getReadAt(){return readAt;} void setHidden(boolean value){hidden=value;}
 @Override public String toString(){return "OrderMessage[id="+id+", body=REDACTED]";}
}
