package com.codeguard.agent.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * 组织实体。
 *
 * 企业 SaaS 通常会按组织隔离数据。这里的组织可以理解成一个客户、
 * 一个公司，或者一条业务线。
 */
@Entity
@Table(name = "organizations")
public class OrganizationEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 80)
    private String organizationKey;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 600)
    private String description;

    @Column(nullable = false, length = 80)
    private String plan;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected OrganizationEntity() {}

    public static OrganizationEntity create(String organizationKey, String name, String description, String plan) {
        Instant now = Instant.now();

        OrganizationEntity entity = new OrganizationEntity();
        entity.id = UUID.randomUUID();
        entity.organizationKey = organizationKey;
        entity.name = name == null || name.isBlank() ? organizationKey : name.strip();
        entity.description = description;
        entity.plan = plan == null || plan.isBlank() ? "ENTERPRISE_DEMO" : plan.strip();
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public String getOrganizationKey() {
        return organizationKey;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getPlan() {
        return plan;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
