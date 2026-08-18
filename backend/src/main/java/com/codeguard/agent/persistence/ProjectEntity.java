package com.codeguard.agent.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * 企业项目实体。
 *
 * 多 Agent 平台通常服务多个业务项目，Project 是最基本的隔离维度。
 */
@Entity
@Table(name = "projects")
public class ProjectEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 80)
    private String projectKey;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 600)
    private String description;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ProjectEntity() {}

    public static ProjectEntity create(String projectKey, String name, String description) {
        Instant now = Instant.now();

        ProjectEntity entity = new ProjectEntity();
        entity.id = UUID.randomUUID();
        entity.projectKey = projectKey;
        entity.name = name == null || name.isBlank() ? projectKey : name.strip();
        entity.description = description;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
