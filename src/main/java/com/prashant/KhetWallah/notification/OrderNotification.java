package com.prashant.KhetWallah.notification;

import com.prashant.KhetWallah.user.AppUser;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "order_notifications", uniqueConstraints = @UniqueConstraint(name="uk_notification_event", columnNames="event_key"),
        indexes = @Index(name="idx_notification_recipient", columnList="recipient_id,id"))
public class OrderNotification {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="recipient_id", nullable=false) private AppUser recipient;
    @Column(name="order_id", nullable=false) private Long orderId;
    @Column(name="event_key", nullable=false, length=160) private String eventKey;
    @Column(name="event_type", nullable=false, length=24) private String eventType;
    @Column(name="recipient_role", nullable=false, length=8) private String recipientRole;
    @Column(name="crop_name", nullable=false, length=100) private String cropName;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="read_at") private Instant readAt;
    protected OrderNotification() {}
    public OrderNotification(AppUser recipient, Long orderId, String eventKey, String eventType,
                             String recipientRole, String cropName) {
        this.recipient=recipient; this.orderId=orderId; this.eventKey=eventKey; this.eventType=eventType;
        this.recipientRole=recipientRole; this.cropName=cropName; this.createdAt=Instant.now();
    }
    public Long getId(){return id;}
    public AppUser getRecipient(){return recipient;}
    public Long getOrderId(){return orderId;}
    public String getEventKey(){return eventKey;}
    public String getEventType(){return eventType;}
    public String getRecipientRole(){return recipientRole;}
    public String getCropName(){return cropName;}
    public Instant getCreatedAt(){return createdAt;}
    public Instant getReadAt(){return readAt;}
    public void markRead(){if(readAt==null) readAt=Instant.now();}
}
