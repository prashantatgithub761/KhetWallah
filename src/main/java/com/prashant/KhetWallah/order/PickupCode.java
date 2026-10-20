package com.prashant.KhetWallah.order;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="order_pickup_codes")
public class PickupCode {
    @Id @Column(name="order_id") private Long orderId;
    @Column(name="code_hash",length=255) private String codeHash;
    @Column(name="expires_at") private Instant expiresAt;
    @Column(name="resend_at",nullable=false) private Instant resendAt;
    @Column(name="window_started_at",nullable=false) private Instant windowStartedAt;
    @Column(name="send_count",nullable=false) private int sendCount;
    @Column(name="failed_attempts",nullable=false) private int failedAttempts;
    @Column(name="pickup_revision",nullable=false) private long pickupRevision;
    @Column(name="consumed_at") private Instant consumedAt;
    protected PickupCode() {}
    PickupCode(Long id,Instant now){orderId=id;resendAt=now;windowStartedAt=now;}
    void refresh(Instant now){if(!now.isBefore(windowStartedAt.plusSeconds(3600))){windowStartedAt=now;sendCount=0;failedAttempts=0;}}
    void issue(String hash,long revision,Instant now){codeHash=hash;pickupRevision=revision;expiresAt=now.plusSeconds(900);resendAt=now.plusSeconds(60);sendCount++;consumedAt=null;}
    void fail(){failedAttempts++;}
    void consume(Instant now){consumedAt=now;codeHash=null;}
    public String getCodeHash(){return codeHash;}
    public Instant getExpiresAt(){return expiresAt;}
    public Instant getResendAt(){return resendAt;}
    public Instant getConsumedAt(){return consumedAt;}
    public int getSendCount(){return sendCount;}
    public int getFailedAttempts(){return failedAttempts;}
    public long getPickupRevision(){return pickupRevision;}
    @Override public String toString(){return "PickupCode[REDACTED]";}
}
