package com.codeguard.agent.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * 审计日志实体。
 *
 * 审计日志记录平台内关键动作，方便企业客户做追责、排查和合规审计。
 */
@Entity
@Table(name = "audit_logs")
public class AuditLogEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 80)
    private String organizationKey;

    @Column(nullable = false, length = 120)
    private String actorUsername;

    @Column(nullable = false, length = 80)
    private String actorRole;

    @Column(nullable = false, length = 120)
    private String action;

    @Column(nullable = false, length = 80)
    private String resourceType;

    @Column(length = 160)
    private String resourceId;

    @Lob
    @Column(nullable = false)
    private String summary;

    @Column(nullable = false)
    private Instant createdAt;

    protected AuditLogEntity() {}

    public static AuditLogEntity create(
            String organizationKey,
            String actorUsername,
            String actorRole,
            String action,
            String resourceType,
            String resourceId,
            String summary
    ) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.id = UUID.randomUUID();
        entity.organizationKey = organizationKey;
        entity.actorUsername = actorUsername;
        entity.actorRole = actorRole;
        entity.action = action;
        entity.resourceType = resourceType;
        entity.resourceId = resourceId;
        entity.summary = summary == null || summary.isBlank() ? action : summary.strip();
        entity.createdAt = Instant.now();
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public String getOrganizationKey() {
        return organizationKey;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public String getActorRole() {
        return actorRole;
    }

    public String getAction() {
        return action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getSummary() {
        return summary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
