package com.codeguard.agent.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * 代码仓库实体。
 *
 * Repository 归属于 Project，用于记录一个项目下有哪些代码来源。
 */
@Entity
@Table(name = "code_repositories")
public class CodeRepositoryEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectEntity project;

    @Column(nullable = false, length = 180)
    private String repositoryName;

    @Column(nullable = false, length = 80)
    private String provider;

    @Column(length = 600)
    private String remoteUrl;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected CodeRepositoryEntity() {}

    public static CodeRepositoryEntity create(
            ProjectEntity project,
            String repositoryName,
            String provider,
            String remoteUrl
    ) {
        Instant now = Instant.now();

        CodeRepositoryEntity entity = new CodeRepositoryEntity();
        entity.id = UUID.randomUUID();
        entity.project = project;
        entity.repositoryName = repositoryName;
        entity.provider = provider == null || provider.isBlank() ? "manual" : provider.strip();
        entity.remoteUrl = remoteUrl;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public ProjectEntity getProject() {
        return project;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public String getProvider() {
        return provider;
    }

    public String getRemoteUrl() {
        return remoteUrl;
    }
}
