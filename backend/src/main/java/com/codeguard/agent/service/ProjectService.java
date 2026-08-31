package com.codeguard.agent.service;

import com.codeguard.agent.api.ProjectDto;
import com.codeguard.agent.api.ProjectRequest;
import com.codeguard.agent.api.RepositoryDto;
import com.codeguard.agent.persistence.CodeRepositoryEntity;
import com.codeguard.agent.persistence.CodeRepositoryRepository;
import com.codeguard.agent.persistence.ProjectEntity;
import com.codeguard.agent.persistence.ProjectRepository;
import com.codeguard.agent.security.CurrentUserService;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 项目和代码仓库服务。
 *
 * Project 用来承载业务项目，Repository 用来承载代码来源。
 * 企业场景下所有查询都会按当前组织过滤，避免不同客户或团队的数据混用。
 */
@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final CodeRepositoryRepository codeRepositoryRepository;
    private final OrganizationService organizationService;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    public ProjectService(
            ProjectRepository projectRepository,
            CodeRepositoryRepository codeRepositoryRepository,
            OrganizationService organizationService,
            CurrentUserService currentUserService,
            AuditLogService auditLogService
    ) {
        this.projectRepository = projectRepository;
        this.codeRepositoryRepository = codeRepositoryRepository;
        this.organizationService = organizationService;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public ProjectEntity ensureProject(String projectKey) {
        String organizationKey = currentUserService.organizationKey();
        organizationService.ensureOrganization(organizationKey);

        String normalizedKey = projectKey == null || projectKey.isBlank() ? "default" : projectKey.strip();
        return projectRepository.findByOrganizationKeyAndProjectKey(organizationKey, normalizedKey)
                .orElseGet(() -> {
                    ProjectEntity project = projectRepository.save(ProjectEntity.create(
                            organizationKey,
                            normalizedKey,
                            "default".equalsIgnoreCase(normalizedKey) ? "默认项目" : normalizedKey,
                            "自动创建的演示项目"
                    ));
                    auditLogService.record("PROJECT_AUTO_CREATED", "PROJECT", project.getProjectKey(),
                            "审查提交时自动创建项目 " + project.getProjectKey());
                    return project;
                });
    }

    @Transactional
    public CodeRepositoryEntity ensureRepository(
            String projectKey,
            String repositoryName,
            String provider,
            String remoteUrl
    ) {
        ProjectEntity project = ensureProject(projectKey);
        String normalizedRepository = repositoryName == null || repositoryName.isBlank()
                ? "manual-diff"
                : repositoryName.strip();

        return codeRepositoryRepository
                .findByOrganizationKeyAndProjectProjectKeyAndRepositoryName(
                        project.getOrganizationKey(),
                        project.getProjectKey(),
                        normalizedRepository
                )
                .orElseGet(() -> {
                    CodeRepositoryEntity repository = codeRepositoryRepository.save(CodeRepositoryEntity.create(
                            project,
                            normalizedRepository,
                            provider,
                            remoteUrl
                    ));
                    auditLogService.record("REPOSITORY_AUTO_CREATED", "REPOSITORY", repository.getRepositoryName(),
                            "审查提交时自动登记代码仓库 " + repository.getRepositoryName());
                    return repository;
                });
    }

    @Transactional
    public ProjectDto create(ProjectRequest request) {
        String organizationKey = currentUserService.organizationKey();
        organizationService.ensureOrganization(organizationKey);

        String projectKey = request.projectKey().strip();
        Optional<ProjectEntity> existing = projectRepository.findByOrganizationKeyAndProjectKey(organizationKey, projectKey);

        ProjectEntity project = existing.orElseGet(() -> projectRepository.save(ProjectEntity.create(
                organizationKey,
                projectKey,
                request.name(),
                request.description()
        )));

        if (existing.isEmpty()) {
            auditLogService.record("PROJECT_CREATED", "PROJECT", project.getProjectKey(),
                    "创建项目 " + project.getName());
        }

        return toDto(project);
    }

    @Transactional(readOnly = true)
    public List<ProjectDto> listProjects() {
        return projectRepository.findByOrganizationKeyOrderByCreatedAtAsc(currentUserService.organizationKey())
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RepositoryDto> listRepositories(String projectKey) {
        String organizationKey = currentUserService.organizationKey();
        if (projectRepository.findByOrganizationKeyAndProjectKey(organizationKey, projectKey).isEmpty()) {
            throw new EntityNotFoundException("Project not found: " + projectKey);
        }

        return codeRepositoryRepository
                .findByOrganizationKeyAndProjectProjectKeyOrderByCreatedAtAsc(organizationKey, projectKey)
                .stream()
                .map(this::toDto)
                .toList();
    }

    private ProjectDto toDto(ProjectEntity entity) {
        return new ProjectDto(
                entity.getId(),
                entity.getOrganizationKey(),
                entity.getProjectKey(),
                entity.getName(),
                entity.getDescription(),
                entity.getCreatedAt()
        );
    }

    private RepositoryDto toDto(CodeRepositoryEntity entity) {
        return new RepositoryDto(
                entity.getId(),
                entity.getOrganizationKey(),
                entity.getProject().getProjectKey(),
                entity.getRepositoryName(),
                entity.getProvider(),
                entity.getRemoteUrl()
        );
    }
}
