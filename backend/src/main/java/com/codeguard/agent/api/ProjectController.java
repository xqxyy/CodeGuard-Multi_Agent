package com.codeguard.agent.api;

import com.codeguard.agent.service.ProjectService;
import com.codeguard.agent.service.ReviewWorkflowService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 项目管理接口。
 *
 * 企业级演示需要一个项目维度，让审查历史可以按项目归档和过滤。
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final ReviewWorkflowService reviewWorkflowService;

    public ProjectController(ProjectService projectService, ReviewWorkflowService reviewWorkflowService) {
        this.projectService = projectService;
        this.reviewWorkflowService = reviewWorkflowService;
    }

    @GetMapping
    public List<ProjectDto> projects() {
        return projectService.listProjects();
    }

    @PostMapping
    public ProjectDto create(@Valid @RequestBody ProjectRequest request) {
        return projectService.create(request);
    }

    @GetMapping("/{projectKey}/repositories")
    public List<RepositoryDto> repositories(@PathVariable String projectKey) {
        return projectService.listRepositories(projectKey);
    }

    @GetMapping("/{projectKey}/reviews")
    public List<ReviewListItem> reviews(@PathVariable String projectKey) {
        return reviewWorkflowService.listRecent(projectKey);
    }
}
