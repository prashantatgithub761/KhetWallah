package com.prashant.KhetWallah.admin;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "admin_actions")
public class AdminAction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 254)
    private String actorEmail;
    @Column(nullable = false, length = 30)
    private String action;
    @Column(nullable = false, length = 30)
    private String targetType;
    @Column(nullable = false)
    private Long targetId;
    @Column(nullable = false, length = 500)
    private String reason;
    @Column(nullable = false)
    private Instant createdAt;

    protected AdminAction() {}
    public AdminAction(String actorEmail, String action, String targetType, Long targetId, String reason) {
        this.actorEmail = actorEmail;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
        this.createdAt = Instant.now();
    }
    public Long getId() { return id; }
    public String getActorEmail() { return actorEmail; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
