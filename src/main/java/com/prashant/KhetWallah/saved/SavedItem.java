package com.prashant.KhetWallah.saved;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="saved_items",uniqueConstraints=@UniqueConstraint(name="uk_saved_account_target",columnNames={"account_id","target_type","target_id"}),indexes=@Index(name="idx_saved_account",columnList="account_id,target_type,id"))
public class SavedItem {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="account_id",nullable=false) private Long accountId;
 @Column(name="target_type",nullable=false,length=10) private String targetType;
 @Column(name="target_id",nullable=false) private Long targetId;
 @Column(name="created_at",nullable=false) private Instant createdAt;
 protected SavedItem(){}
 public SavedItem(Long accountId,String targetType,Long targetId){this.accountId=accountId;this.targetType=targetType;this.targetId=targetId;this.createdAt=Instant.now();}
 public Long getId(){return id;}public Long getAccountId(){return accountId;}public String getTargetType(){return targetType;}public Long getTargetId(){return targetId;}
}
