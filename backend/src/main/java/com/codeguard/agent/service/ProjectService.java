package com.codeguard.agent.service;

import com.codeguard.agent.api.ProjectDto;
import com.codeguard.agent.api.ProjectRequest;
import com.codeguard.agent.api.RepositoryDto;
import com.codeguard.agent.persistence.CodeRepositoryEntity;
import com.codeguard.agent.persistence.CodeRepositoryRepository;
import com.codeguard.agent.persistence.ProjectEntity;
import com.codeguard.agent.persistence.ProjectRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 项目和仓库服务。
 *
 * 它让 Review 不再只是“临时审查”，而是落到企业里的项目和仓库维度。
 */
@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final CodeRepositoryRepository codeRepositoryRepository;

    public ProjectService(
            ProjectRepository projectRepository,
            CodeRepositoryRepository codeRepositoryRepository
    ) {
        this.projectRepository = projectRepository;
        this.codeRepositoryRepository = codeRepositoryRepository;
    }

    @Transactional
    public ProjectEntity ensureProject(String projectKey) {
        String normalizedKey = projectKey == null || projectKey.isBlank() ? "default" : projectKey.strip();
        return projectRepository.findByProjectKey(normalizedKey)
                .orElseGet(() -> projectRepository.save(ProjectEntity.create(
                        normalizedKey,
                        "Default".equalsIgnoreCase(normalizedKey) ? "默认项目" : normalizedKey,
                        "自动创建的演示项目"
                )));
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
                .findByProjectProjectKeyAndRepositoryName(project.getProjectKey(), normalizedRepository)
                .orElseGet(() -> codeRepositoryRepository.save(CodeRepositoryEntity.create(
                        project,
                        normalizedRepository,
                        provider,
                        remoteUrl
                )));
    }

    @Transactional
    public ProjectDto create(ProjectRequest request) {
        String projectKey = request.projectKey().strip();

        ProjectEntity project = projectRepository.findByProjectKey(projectKey)
                .orElseGet(() -> projectRepository.save(ProjectEntity.create(
                        projectKey,
                        request.name(),
                        request.description()
                )));

        return toDto(project);
    }

    @Transactional(readOnly = true)
    public List<ProjectDto> listProjects() {
        return projectRepository.findAllByOrderByCreatedAtAsc()
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RepositoryDto> listRepositories(String projectKey) {
        if (projectRepository.findByProjectKey(projectKey).isEmpty()) {
            throw new EntityNotFoundException("Project not found: " + projectKey);
        }

        return codeRepositoryRepository.findByProjectProjectKeyOrderByCreatedAtAsc(projectKey)
                .stream()
                .map(this::toDto)
                .toList();
    }

    private ProjectDto toDto(ProjectEntity entity) {
        return new ProjectDto(
                entity.getId(),
                entity.getProjectKey(),
                entity.getName(),
                entity.getDescription(),
                entity.getCreatedAt()
        );
    }

    private RepositoryDto toDto(CodeRepositoryEntity entity) {
        return new RepositoryDto(
                entity.getId(),
                entity.getProject().getProjectKey(),
                entity.getRepositoryName(),
                entity.getProvider(),
                entity.getRemoteUrl()
        );
    }
}
