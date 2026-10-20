package com.prashant.KhetWallah.message;
import com.prashant.KhetWallah.user.AppUser;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name="message_reports",uniqueConstraints=@UniqueConstraint(name="uk_message_reporter",columnNames={"message_id","reporter_id"}),indexes=@Index(name="ix_message_reporter_time",columnList="reporter_id,created_at"))
public class MessageReport {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="message_id",nullable=false) private OrderMessage message;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="reporter_id",nullable=false) private AppUser reporter;
 @Column(nullable=false,length=500) private String reason;
 @Column(name="created_at",nullable=false) private Instant createdAt;
 protected MessageReport(){}
 MessageReport(OrderMessage message,AppUser reporter,String reason,Instant now){this.message=message;this.reporter=reporter;this.reason=reason;this.createdAt=now;}
 public Long getId(){return id;} public OrderMessage getMessage(){return message;} public AppUser getReporter(){return reporter;} public String getReason(){return reason;} public Instant getCreatedAt(){return createdAt;}
}
